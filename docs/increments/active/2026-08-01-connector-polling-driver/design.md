# Shared Connector Polling Driver

Source: full-codebase simplify review (2026-08-01), deferred items B11 and
B21 plus policy drift V3 in
`docs/increments/completed/2026-08-01-authz-consolidation/references/phase2-codex-report.md`.

## Problem

Slack, Google Drive, and GitHub each implement the same connection-level
polling mechanics inside their source adapter:

- enumerate enabled connections and isolate one connection's failure from the
  rest of the poll;
- decide whether content is due or only permissions/membership should be
  refreshed;
- advance that cadence only after an admitted batch exists;
- resolve a credential and construct an API client;
- turn an adapter exception into a `ConnectorConnectionFailure`; and
- build the outer crawl cursor from component cursors.

Those copies have already drifted. Slack and Drive abort a run when at least
half of its eligible units cannot be read, while GitHub checkpoints an
equally degraded result. GitHub retains its installation-token client by
credential fingerprint, while Slack recreates its rate-limit gate and Drive
recreates its access-token source on every poll. `ContentCadence` is shared,
but each adapter still wires and advances it independently.

The drift is reliability policy, not harmless formatting. A mostly failed
batch looks healthy enough to checkpoint; recreating token/rate-limit clients
throws away safe in-memory state; and an accidentally changed cursor makes a
previously completed batch look new.

## Constraints

- The persisted crawl cursor and every component cursor are compatibility
  identifiers. Existing bytes for the same captured source state must remain
  identical.
- `ConnectorBatchSource`, `ConnectorPoll`, `ConnectorCrawlBatch`, and the
  versioned fixtures under `contracts/connector` remain unchanged.
- Source-specific API calls, admissibility rules, completeness claims,
  mappings, and component cursor material remain adapter-owned.
- Credentials remain fetched on every poll. A cache may retain a derived
  client, never the exposed credential, and must rebuild on credential or
  client-affecting configuration changes.
- A failed content attempt must not consume its interval. A disabled or
  deleted connection must not leave an unbounded client/cadence entry.
- Public contracts, generated models, extraction parallelism, loaders, and
  persisted digests/fingerprints are out of scope.

## Reference Evidence

The comparable Onyx checkout is pinned at
`D:/OrgMemory/tmp/onyx` commit
`618b5031bf21463f44e3bed9eb9d5073b806fec0`.

| System | Behavior | Mechanism and evidence | Judgment for OrgMemory |
|---|---|---|---|
| Current OrgMemory | Each adapter is a `ConnectorBatchSource`; Slack/Drive duplicate the GitHub outer loop but have no derived-client cache. | `SlackConnectorBatchSource`, `GoogleDriveConnectorBatchSource`, `GitHubConnectorBatchSource`, and `ContentCadence` under `integrations/connectors/src/main/java`. | The outer mechanics are one responsibility and should be enforced once. Component semantics must remain local because their completeness evidence differs. |
| Onyx | A shared runner normalizes connector shapes, batching, failure outputs, and checkpoint completion, while connectors retain source calls. Credentials are loaded through a provider capable of observing rotation. | `backend/onyx/connectors/connector_runner.py`, `backend/onyx/connectors/factory.py`, and `backend/onyx/connectors/credentials_provider.py`. | Reuse the runner/adapter separation, not Onyx's document/checkpoint model. OrgMemory's independent ACL components and fail-closed retirement claims require its existing crawl-batch contract. |

Onyx also demonstrates why the shared layer should not cache a connector
class as though that cached credentials: its factory caches imported classes,
but credential state is supplied to an instance through a provider. OrgMemory
will continue to resolve its encrypted credential each poll and cache only a
derived API client behind a fingerprint.

## Decision

Introduce an abstract `PollingConnectorBatchSource<C>` in
`com.orgmemory.connectors`. Slack, Drive, and GitHub extend it while remaining
the beans contributed as `ConnectorBatchSource`.

### Driver-owned flow

The final `pendingBatches()` method will:

1. read the enabled configurations for one source system;
2. derive a canonical connection identity record from organization id,
   source system, and source connection key;
3. resolve the credential on every poll and obtain a cached client;
4. ask the shared cadence whether content is due;
5. invoke the adapter once with `(client, configuration, contentDue)`;
6. reject a result when `failedUnits > 0` and
   `failedUnits >= totalUnits * 0.5`;
7. advance cadence only for an admitted content batch;
8. convert known credential/API/mostly-failed exceptions into one failure
   envelope without ending other connections; and
9. retire cache and cadence entries for connections no longer enabled.

The driver does not catch unknown runtime exceptions. Each adapter identifies
its expected credential/API exception types and maps their source error code;
the shared mostly-failed rejection uses `mostly_failed`.

### Derived-client cache

The cache key is a typed connection identity, never a concatenated or hashed
surrogate. Its revision is the SHA-256 of the credential bytes plus an
adapter-provided fingerprint of settings that affect client construction.
Slack and GitHub have no such settings today. Drive includes the impersonated
user because that value is bound into its access-token source.

The exposed credential exists only while hashing and constructing a client;
it is not retained by the driver. Rotation or a client-affecting
configuration edit replaces the cached context atomically on the next poll.
Missing credentials evict the entry immediately. Entries for disabled or
deleted connections are pruned after every enumeration.

### Mostly-failed admission

The threshold is one shared policy: at least half failed rejects the entire
connection attempt and therefore writes no checkpoint.

- Slack counts channels it attempted to read, retaining the existing
  `not_in_channel` scope exception.
- Drive counts indexable files it attempted to observe.
- GitHub counts repositories and records a repository once if either its
  authoritative collaborators or its content could not be read. A
  permissions-only pass counts collaborator failures only.

Applying this to GitHub is an intentional behavior correction. A one-repository
installation whose collaborators endpoint is forbidden now produces no batch
and a `mostly_failed` failure, instead of checkpointing an incomplete empty
ACL-shaped result. Below the threshold the existing incomplete component
states remain fail-closed and useful.

### Cursor compatibility

The driver owns only the common outer-cursor scaffolding: sort
`component=cursor`, join with `;`, SHA-256 the material, and prefix it with the
source-system token. Each adapter continues to build its content, permission,
and membership cursor material byte-for-byte as before.

Before production code changes, golden tests will pin all four cursor strings
for one deterministic full crawl of each connector. Those vectors remain
green after delegation to the driver. No migration, compatibility fallback,
or cursor rewrite is permitted.

## Rejected Alternatives

### Utility methods while adapters keep the loop

This reduces lines but does not establish one policy owner. Adapters could
still omit failure admission, advance cadence early, or bypass cache
invalidation.

### Move source crawl semantics into one strategy object

An expansive strategy SPI would expose source-specific intermediate models
and completeness rules solely to make the abstraction look uniform. It would
increase compatibility surface and make fail-closed evidence harder to audit.
The chosen callback returns the existing final batch plus unit-failure counts.

### Cache clients without resolving credentials every poll

That would make revocation and rotation restart-dependent. The chosen design
uses a fresh credential fingerprint as the cache revision on every poll.

### Preserve GitHub's current degraded-batch behavior

That makes the shared threshold optional and retains the confirmed policy
drift. The cost of the correction is more retries during a broad GitHub
outage; the benefit is that a badly degraded connection cannot advance its
checkpoint while looking successful.

## Risks And Required Proof

- **Cursor drift:** golden byte vectors before refactoring and again after it.
- **Cache leakage or stale identity:** tests for reuse, credential rotation,
  Drive impersonation change, missing credential eviction, and disabled
  connection retirement.
- **Policy miscount:** boundary tests below, at, and above 50%, including
  GitHub collaborator and content failures.
- **Cadence regression:** existing per-adapter cadence/request/failure tests
  plus direct driver tests for advance-after-admission only.
- **SPI/fixture breakage:** no core or contract edits; existing
  auto-configuration and contract fixture suites stay green.

## Architecture Challenge

Required by `AGENTS.md` because this changes connector failure policy,
credential-derived client lifecycle, and the adapter/framework boundary.
The independent record will be written to `challenge-verdict.md` before
`plan.md` is created.
