# Google Drive Ingestion Hardening Design

## Problem

The Google Drive adapter already authenticates a service account, optionally
impersonates one Workspace user through domain-wide delegation, crawls Drive v3,
separates content and permission cadence, and feeds the governed connector
ledger. Its provider tests are strong, but the production path still has two
unproved properties:

1. one Drive batch can retain up to `maxFiles * maxFileBytes` of extracted text
   before reconciliation (500 * 10 MiB by default, plus one bounded Google-native
   export), because `ConnectorCrawlBatch` owns every body as a `String`;
2. no test drives recorded Drive responses through the real adapter, connector
   reconciliation, PostgreSQL ledger, sealed ACL, and permission-aware retrieval
   in one flow.

Per-file caps do not bound a batch. A connector that can exhaust the worker before
its first durable write is not ready for the enterprise demo.

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

Do not add a Google-specific staging schema or a second ingestion service. Add an
adapter-owned aggregate extracted-content budget, `maxBatchBytes`, with a
conservative default and browser configuration. The adapter counts UTF-8 bytes
of each extracted body before retaining it. Once the next body would exceed the
budget, it stops reading further bodies, marks content and whole-crawl
completeness false, but preserves permission evidence already established. A
single response remains bounded by the existing API-client hard cap.

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

A budget hit is partial progress, not success and not a provider failure. It must:

- emit no tombstone or complete-crawl claim for unseen content;
- leave the CONTENT component incomplete so its checkpoint does not advance;
- leave already established permission evidence eligible for reconciliation;
- expose a stable, non-secret incomplete reason;
- retry from the source on a later poll after configuration changes or a larger
  budget.

This accepts source replay before the ledger boundary for the demo rather than
introducing a durable pre-ledger queue. The decision is valid only while the
bounded full crawl fits the pilot envelope. Measured repeated-provider cost,
large-tenant scale, or change-token requirements reopen durable staging.

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
