# Architecture Challenge Verdict: Bounded Google Drive Ingestion

Date: 2026-08-15
Source checkout: `D:\OrgMemory-worktrees\google-drive-ingestion-hardening`
Commit reviewed: `39bd777136324234d4472c1010258441e28b5f13`
Independent reviewer: Claude Fable 5, read-only Orca terminal
`term_a1b00082-a03c-413d-9217-87fe46054f9c`
Pinned Onyx commit: `618b5031bf21463f44e3bed9eb9d5073b806fec0`

## Verdict

**ACCEPT WITH BINDING CORRECTIONS.**

Keep the bounded pilot boundary: adapter-owned aggregate `maxBatchBytes`, no
Google-specific staging schema, and no new persistence owner. Durable per-object
staging is not mandatory for the enterprise demo. The original proposal was not
implementable as written for unsized Google-native exports, understated the
multi-connection worker bound, and exposed a pre-existing permission-only
reconciliation defect. Every correction below is binding.

## Committed ownership

- `integrations:connectors` owns Google APIs, credentials/delegation, provider
  retries, the 25 MiB single-response cap, listing, permission observation,
  extraction, `maxFiles`, `maxFileBytes`, `maxBatchBytes`, completeness,
  component cursors, and mostly-failed evidence.
- `apps:worker` owns polling, three-attempt batch retry, checkpoint advancement,
  and attempt recording.
- `core` owns validation, identity resolution, per-object durable
  reconciliation, sealed ACL generations, immutable content publication,
  retirement, and checkpoint persistence.
- This increment adds no Google-specific core contract, staging table, Directory
  API, OAuth, multi-user enumeration, change-token, or webhook surface.

The shared polling driver constructs every enabled connection's batch before the
worker ingests any batch. The real pre-ledger body bound is therefore the sum over
enabled connections of each batch budget plus one in-flight 25 MiB response and
metadata/transient copies. This verdict is limited to the demo envelope of one
enabled Google Drive connection.

## Binding aggregate-budget semantics

1. `maxBatchBytes` lives in `GoogleDriveCrawlSettings`, defaults to 64 MiB, uses
   the default when non-positive, and clamps smaller positive values to the
   25 MiB API response cap. One admissible body can therefore make progress in
   an empty batch.
2. The budget counts UTF-8 bytes of retained nonblank bodies. Metadata,
   permissions, identities, and cursor material are outside this budget.
3. Google-native exports have no size metadata. Read one body under the existing
   25 MiB response cap, compute UTF-8 bytes, and discard it if retaining it would
   exceed the aggregate budget. Peak body retention is budget plus one capped
   response and transient copies.
4. After exhaustion, continue over every remaining listed file in the existing
   permissions-only path. Do not count budget-skipped files as provider failures
   or distort mostly-failed admission.
5. Bodies retained before exhaustion reconcile normally. Emit CONTENT as
   `INCOMPLETE` with stable reason
   `GOOGLE_DRIVE_CONTENT_BUDGET_EXHAUSTED`; emit whole-crawl completeness false.
   PERMISSION completeness changes only when sharing cannot be established.
6. A budget hit is partial progress, not an exception or rejected attempt. It
   consumes the content cadence to avoid a hot loop. Operators can raise the
   budget or narrow scope and use the existing crawl-now request.
7. An incomplete CONTENT checkpoint advances observed cursor/reason only; the
   last-successful cursor remains. An identical later partial batch can be
   checkpoint-skipped after the provider work that produced it.
8. A budget hit cannot retire unseen evidence because pruning requires both
   whole-crawl completeness and a COMPLETE CONTENT component.

## Required core correction

`ConnectorReconciler.reconcilePermissions` currently throws when a permission
item names an object with no materialized source head. Drive already adds the
permission item before size and blank-body decisions, so a new oversized or
blank file can wedge the PERMISSION checkpoint indefinitely. Budget-stopped tail
files would multiply that defect.

An empty source head must return the existing benign `UNCHANGED` outcome. An
unmaterialized object has no revision, chunks, or retrievable surface to govern;
its ACL is sealed when content later materializes. This requires no new outcome,
contract, or schema.

## Required tests

- Exact aggregate boundary retains the body and keeps CONTENT complete.
- Crossing body, including a native unsized export, is discarded; earlier bodies
  remain; CONTENT is incomplete with the exact reason; permissions for every
  listed file remain present.
- A budget-hit batch through PostgreSQL retires nothing and a permission change
  after the cutoff still converges.
- A budget hit is admitted, is not `mostly_failed`, advances cadence, and an
  identical observed cursor is checkpoint-skippable.
- The incomplete reason is visible through existing admin checkpoint/activity
  state.
- Existing golden Drive cursor bytes are unchanged when the budget is not hit.
- The vertical Drive proof includes an unmaterialized permission item (blank or
  budget-stopped tail) so the core correction is exercised through PostgreSQL.
- Direct-user allow, another-user deny, permission-only revocation, and unchanged
  content identity are proved through permission-aware retrieval.

## Strongest counterargument

A durable per-object staging ledger would fence an attempt before provider reads,
resume after each object, avoid repeated API calls after worker death, and allow
tail files to converge without operator action. Pinned Onyx implements that shape
with a database `IndexAttempt`, streamed provider batches, and checkpoint
continuation.

It does not defeat this pilot choice. Everything after OrgMemory's batch boundary
is already durable and idempotent; retirement and authorization fail closed; the
aggregate cap makes the pre-ledger body cost finite. Adding staging now would add
a second persistence owner, migrations, encrypted-payload retention, leases, and
cleanup before measured need. Tail starvation remains a disclosed coverage gap,
not a governance widening.

## Reopening conditions

Reopen durable checkpoint continuation when any one occurs:

1. `GOOGLE_DRIVE_CONTENT_BUDGET_EXHAUSTED` appears on two consecutive scheduled
   content passes for one connection;
2. a real tenant's standing in-scope corpus exceeds `maxBatchBytes` or
   `maxFiles`;
3. measured crash replay or repeated-provider cost exceeds the pilot envelope;
4. the one-Drive-connection demo envelope expands materially; or
5. source change tokens, webhooks, or incremental sync are adopted.

The preferred follow-up is Onyx-shaped connector checkpoint continuation: pass a
last-observed CONTENT cursor/resume token into the adapter. Do not default to a
Google-specific staging schema.

## Contradiction round

The reviewer challenged its verdict with the mandated cases:

- **Unsized native document:** forced read-then-discard and the 25 MiB budget
  floor; verdict retained.
- **Stable-order tail starvation:** confirmed as real. It is accepted only as an
  observable pilot limit with the consecutive-budget-hit reopening trigger.
- **Crash after provider reads, before reconciliation:** confirmed full provider
  replay. It remains acceptable because the pass is bounded and downstream
  reconciliation is idempotent; it reopens when measured cost exceeds the pilot.

No contradiction overturned the corrected verdict.

## Scope limit

This verdict certifies only the bounded enterprise-demo envelope at the reviewed
commit: service-account credential, direct-user grants, one enabled Drive
connection, and a corpus intended to fit within `maxBatchBytes`. It does not
certify production-scale ingestion, Directory membership, OAuth, incremental
sync, or large-tenant throughput.
