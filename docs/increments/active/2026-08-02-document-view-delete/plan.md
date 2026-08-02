# Plan - Document View And Delete

Design: [design.md](design.md).

## Step 1 - Independent challenge

- [x] Dispatch the written brief to an independent read-only reviewer.
- [x] Record the verdict, strongest contradiction, must-fix items, and committed
  scope in `challenge-verdict.md`.

## Step 2 - Characterization tests first

- [x] Pin current source-list identity, citation delivery headers, Knowledge
  Asset retirement, and record the existing worker lease/publication race that
  keeps pre-publication cancellation out of scope.
- [x] Add failing contract tests for source action metadata, protected source
  content, authorized READY-upload retirement, denial/non-disclosure, and
  idempotency.

## Step 3 - Source action boundary

- [x] Resolve visible Source Object, latest revision, current Knowledge Asset,
  and action availability without exposing storage keys or denied identities.
- [x] Add a source-content use case and endpoint that rechecks authorization,
  validates blob integrity, audits allow/deny, and applies the existing safe
  representation policy.
- [x] Add one idempotent source-retirement coordinator for READY manual uploads.
  Recheck `knowledge_asset can_delete`, archive the asset/version and Source
  Object; keep every other lifecycle state and connector-owned sources out of
  scope. Retained physical data remains governed by retention policy.
- [x] Regenerate OpenAPI and Hey API clients instead of hand-writing ordinary
  REST DTOs or transports.

## Step 4 - Documents UI

- [x] Add a closed PDF/image/text protected document preview without sharing
  citation-specific authorization.
- [x] Add title/`View` affordances, metadata/detail sheet, download fallback,
  and content-unavailable states.
- [x] Add an overflow `Delete` action for eligible READY uploads, named
  confirmation, pending/success/error states, list invalidation after
  retirement, and explicit disabled explanations elsewhere.
- [x] Keep `Reindex` absent. Focused browser verification covers the keyboard
  interaction path; responsive and theme behavior use existing primitives.

## Step 5 - Verification and consolidation

- [x] Run focused core/API permission/lifecycle tests, web unit/typecheck, and a
  real-browser READY View/Delete flow with processing Delete disabled.
- [x] Run backend compile, terminating clean test, web production build, OpenAPI
  drift, docs check/build, `git diff --check`, and repository docs checker.
- [x] Reconcile `knowledge-ingestion` and its mirrored test matrix; add only
  implemented facts to architecture/specs.

## Step 6 - Delivery

- [ ] Commit coherent changes, open a PR, wait for required CI/review, address
  actionable findings, and merge only when green.
- [ ] Deploy through the normal main-based production workflow and verify one
  authorized view, one denied view, one retirement, and list/retrieval removal.
- [ ] Record the verified checkpoint in Northstar without secrets or content.

## Deferred follow-up - Pre-publication cancellation

- Introduce a monotonic claim-generation token and terminal cancellation marker.
- Fence publication prepare/complete and ingestion terminal transitions against
  the same locked source/job facts.
- Define aborted publication/outbox cleanup for no-asset, prepared, tuple-written,
  applied-before-ingestion-complete, retry, failed, and already-retired states.
- Prove stale workers cannot activate an asset, advance the source head, enqueue
  graph work, or change a cancelled job at every transaction cut point.
