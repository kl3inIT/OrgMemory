# 0026 — Live connectors share one polling lifecycle

Status: accepted (2026-08-01)

## Decision

Slack, Google Drive, and GitHub delegate connection enumeration, content
cadence, credential-derived client lifecycle, failure isolation, mostly-failed
admission, retirement, and outer-cursor scaffolding to one final
`PollingConnectorBatchSource`. Adapters retain provider calls, eligible-unit
counting, mappings, completeness evidence, component cursor material, and an
explicit historical outer-cursor prefix.

Credentials are resolved on every poll. The driver caches only the derived
client under a typed organization/source/connection identity plus credential
and client-setting revisions; cached clients necessarily retain
credential-derived material for their lifetime. Rotation replaces them on the
next poll, and missing or disabled connections retire both client and cadence
state.

A pass in which at least half of eligible units failed provider requests
returns no batch and reports `mostly_failed`. GitHub counts a repository at
most once across collaborator and content request failures and excludes
configured truncation, content skipped after the same audience failure, and
incomplete source fields. This deliberately stalls healthy-unit membership
revocations within the same connection until a threshold-level failure clears;
recurring `UNAVAILABLE` activity exposes that fail-closed operational cost.

Persisted cursor bytes are immutable compatibility identifiers. The shared
scaffolding therefore accepts an adapter-owned literal prefix instead of
deriving one from the source id: Google Drive must continue to emit
`google-drive-` even though its source id is `google_drive`.

## Why

Three copied loops had already drifted: GitHub omitted Slack and Drive's
mostly-failed admission, while Slack and Drive discarded safe rate-limit or
token-refresh client state on each poll. Optional helpers would leave every
policy bypassable. Centralizing only the lifecycle makes admission and
retirement consistent without moving provider evidence into a generic model.

## Reference comparison

Pinned Onyx commit `618b5031bf21463f44e3bed9eb9d5073b806fec0`
uses one `ConnectorRunner` to normalize batching, connector failures, and the
final checkpoint while provider implementations retain their load/poll calls.
Its factory caches connector classes rather than credential-bearing instances,
and dynamic connectors receive an `OnyxDBCredentialsProvider` that reloads and
can rotate database-backed credentials under a renewal lock. This supports the
chosen runner/adapter boundary and the rule that credential freshness must not
depend on a cached connector object.

Pinned Airbyte Python CDK commit
`fe0a0828b56ae3cc709629f4ff7931213fa3d38f` constructs stream instances once
for each `read` command from that command's configuration. Its
`ConnectorStateManager` keeps independent stream state; the sequential source
can continue after a stream failure but ultimately exits non-zero, while the
concurrent source closes and emits state only for successfully read partitions,
collects stream exceptions, marks those streams incomplete, and ultimately
fails the attempt. HTTP retry/backoff, failure classification, and secret
filtering are shared CDK mechanics rather than reimplemented per connector.

OrgMemory adopts Airbyte's policy-owner/source-semantics split and explicit
successful-unit checkpoint discipline, but not its process lifetime: OrgMemory
runs a long-lived JVM poller, so safe provider rate-limit and token-refresh state
must be cached, rotated, and retired explicitly. Nor can OrgMemory copy
per-stream/partition checkpoints directly: authorization completeness,
tombstones, and current cursors are connection/component scoped. If the
documented revocation stall becomes operationally unacceptable, the follow-up
is a durable per-unit authorization checkpoint model, not allowing a broadly
degraded connection batch to advance.

## Strongest counterargument

Keep each adapter's loop because Slack counts channels, Drive counts files,
and GitHub has distinct collaborator and content failure modes. A common pair
of counts cannot prove that any adapter chose an honest denominator, and
whole-connection rejection can delay a healthy repository's revocation.

The adapter still owns and tests that denominator; the driver owns the one
admission decision that copied implementations had failed to apply
consistently. The revocation stall is accepted and made observable instead of
allowing a broadly degraded crawl to advance its checkpoint.

## Rejected alternatives

- Optional utility methods while adapters retain their polling loops, because
  they cannot prevent another policy omission.
- A broad strategy model containing provider intermediate state, because it
  would expand the integration SPI and obscure completeness evidence.
- Caching without resolving credentials every poll, because rotation and
  revocation would become restart-dependent.

Independent challenge record:
`docs/increments/completed/2026-08-01-connector-polling-driver/`.
