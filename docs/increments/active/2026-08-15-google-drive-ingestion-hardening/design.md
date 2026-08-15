# Google Drive Ingestion Hardening Design

## Problem

The Google Drive adapter already authenticates a service account, optionally
impersonates one Workspace user through domain-wide delegation, crawls Drive v3,
separates content and permission cadence, and feeds the governed connector
ledger. Its provider tests are strong, but the production path still has two
unproved properties:

1. one Drive batch can retain up to 500 individually capped bodies before
   reconciliation: nominally 5,000 MiB for metadata-sized files and as much as
   12,500 MiB when Google-native exports omit size metadata and each reaches the
   25 MiB response cap, because `ConnectorCrawlBatch` owns every body as a
   `String`;
2. no test drives recorded Drive responses through the real adapter, connector
   reconciliation, PostgreSQL ledger, sealed ACL, and permission-aware retrieval
   in one flow.

Per-file caps do not bound a batch. The shared polling driver also constructs
every enabled connection's batch before the worker ingests any of them. The
pre-ledger worker bound is therefore the sum across enabled connections of each
batch budget, one in-flight capped response, metadata, and transient copies. This
increment is limited to the enterprise demo's one enabled Google Drive
connection.

## Product promise

OrgMemory is governed organizational memory. Connected evidence must become
searchable through the same immutable revision and authorization path as every
other source, without allowing an unreadable or unbounded Drive to widen access,
retire unseen evidence, or destabilize the worker.

## Proposed boundary

Keep the established source boundary:

- `integrations:connectors` owns Google APIs, service-account credentials,
  optional delegated subject, provider retries, mappings, and completeness;
- `apps:worker` owns polling and retry;
- `core` owns durable reconciliation, immutable source revisions, sealed ACL
  generations, mapping, lifecycle, and retrieval enforcement.

Do not add a Google-specific staging schema or a second ingestion service. Add
an adapter-owned aggregate extracted-content budget, `maxBatchBytes`, exposed by
the existing descriptor-driven browser form. It defaults to 64 MiB and is
clamped to at least the API client's 25 MiB single-response cap. The adapter
reads one body under that cap, counts its UTF-8 bytes, and retains it only when
the aggregate stays within budget. A crossing body is discarded. The adapter
then stops body reads but continues permission observation for every remaining
listed file. It marks CONTENT and whole-crawl completeness false with
`GOOGLE_DRIVE_CONTENT_BUDGET_EXHAUSTED`; permission completeness changes only
when sharing itself could not be established. Peak body retention is the budget
plus one capped response and transient copies.

The vertical proof uses recorded Google responses and a generated non-production
service-account key, then runs the resulting batch through the real PostgreSQL
ledger and retrieval boundary. It proves direct-user allow/deny and a later
permission-only revocation without content rematerialization. Google Group and
domain grants remain fail-closed because Directory membership is not captured.

## Identity and authentication scope

This increment keeps the existing enterprise setup model:

- one connector credential is one Google service-account JSON key;
- optional `impersonatedUser` is the delegated Workspace user whose Drive view is
  crawled;
- the adapter requests only `drive.readonly`;
- the demo uses direct user grants, whose verified email observations can map to
  OrgMemory users;
- Admin SDK user/group enumeration, Google Group membership sync, multi-user
  impersonation, OAuth refresh-token storage, and Change API/webhooks are out of
  scope.

Those exclusions are functional limits, not implied support.

## Failure and replay semantics

A budget hit is partial progress, not an exception, rejection, or provider
failure. It must:

- emit no tombstone or complete-crawl claim for unseen content;
- leave CONTENT incomplete with the stable, non-secret
  `GOOGLE_DRIVE_CONTENT_BUDGET_EXHAUSTED` reason while advancing only its
  observed checkpoint cursor;
- continue permission observation for every listed file without counting
  budget-skipped files in the mostly-failed numerator;
- preserve a complete PERMISSION component when all sharing was established;
- consume content cadence to avoid a hot loop; operators raise the budget or
  narrow scope and use the existing crawl-now request.

This accepts source replay before the ledger boundary for the demo rather than
introducing a durable pre-ledger queue. The decision is valid only while the
bounded full crawl fits the pilot envelope. Measured repeated-provider cost,
large-tenant scale, or change-token requirements reopen durable staging.

Permission-only reconciliation of an object with no materialized source head is
benign and unchanged: there is no revision, chunk, or retrieval surface to
govern, and the ACL seals when content later materializes. Throwing here would
wedge the security-relevant PERMISSION checkpoint for blank, oversized, and
budget-stopped new files.

## Strongest counterargument

A durable per-object staging ledger is the principled production design. It would
write content and permission envelopes before reconciliation, resume after each
object, avoid repeating provider reads after worker death, and support Drive
change tokens. Aggregate limiting still re-reads the Drive after failure and can
starve files ordered after the cap.

That alternative is not selected for this increment because it creates a new
persistence owner, migrations, cleanup/retention rules, encrypted payload
handling, claim leases, and cursor semantics before the pilot has measured this
cost. The current connector contract and reconciliation path are already durable
and idempotent after the batch boundary. A strict aggregate cap converts the
unbounded pre-ledger window into an explicit pilot limit without adding a second
source of truth.

## Rejected alternatives

- Rely on `maxFiles` and `maxFileBytes`; their product is the unsafe aggregate.
- Lower only `maxFiles`; document sizes vary and Google-native exports omit size
  metadata.
- Stream directly into `ConnectorIngestionService`; this would couple provider
  calls to per-object transactions and make component completeness and
  whole-crawl retirement depend on half-observed provider state.
- Add Directory API membership sync here; authorization expansion requires its
  own source-evidence, cadence, admin-scope, and revocation design.
- Replace service accounts with OAuth; that changes consent, refresh-token
  storage, and connection ownership without solving the memory or vertical-proof
  gaps.

## Reopening conditions

Durable checkpoint continuation becomes a new decision when any one occurs:

1. `GOOGLE_DRIVE_CONTENT_BUDGET_EXHAUSTED` appears on two consecutive scheduled
   content passes for one connection;
2. a standing tenant corpus exceeds `maxBatchBytes` or `maxFiles`;
3. measured crash replay or repeated-provider cost exceeds the pilot envelope;
4. the one-enabled-Drive demo envelope expands materially; or
5. source change tokens, webhooks, or incremental sync are adopted.

The preferred follow-up passes a last-observed CONTENT cursor or resume token
into the adapter, analogous to the pinned Onyx runner. It is not a
Google-specific staging schema by default.

## Exit gates

- Independent architecture challenge accepts the bounded pilot boundary or its
  binding corrections are incorporated.
- Aggregate budget behavior is covered for exact-boundary and over-boundary
  cases, including component completeness and no false retirement authority.
- A recorded-response Drive adapter-to-retrieval integration test proves allow,
  deny, revocation, unchanged content identity, and no secret/network use.
- Backend static inspection and narrow tests pass, followed by terminating
  `clean test`.
- Architecture, ingestion spec, mirrored test matrix, roadmap, and verification
  evidence are reconciled before completion.
