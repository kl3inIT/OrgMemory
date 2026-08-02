# Architecture Challenge Brief - Document View And Delete

Date: 2026-08-02  
Base commit: `39281c33f8dfd551fc96df1e7eac520dc5c58e1a`

## Requested outcome

Add `View` and `Delete` actions to the existing Documents table. Do not add a
Reindex action.

## Proposed boundary

Use Source Object ids in the browser and add source-centric server use cases.
`View` resolves the latest governed revision and streams original evidence only
after the current permission intersection and integrity checks pass. `Delete`
retires manual uploads across pending/failed/ready states, closing durable work
before retiring the source/asset. Connector-owned content remains managed by its
connector.

## Repository evidence

- `apps/api/.../source/SourceResponse.java` returns Source Object id only.
- `apps/api/.../knowledge/KnowledgeAssetLifecycleController.java` deletes by
  Knowledge Asset id and delegates to `can_delete` enforcement.
- `apps/api/.../knowledge/CitationContentController.java` streams by chunk id,
  with `no-store`, `nosniff`, a closed media policy, and permission audit.
- `core/.../retrieval/SecureSourceVisibilityAdapter.java` proves list visibility
  is the intersection of OpenFGA and canonical source ACL/classification state.
- `SourceIngestionJob` has pending/processing/succeeded/failed states but no
  cancellation state; expired leases can be reclaimed.
- ADR 0008 gives the worker ownership of durable ingestion and projections.
- ADR 0012 makes the Knowledge Asset stable, its versions immutable, and archive
  a tombstone rather than physical deletion.
- The roadmap forbids arbitrary rich-content preview without an explicit
  untrusted-content contract and maintained sanitization.

## Questions the reviewer must attack

1. Is a source-centric command the correct aggregate boundary, or should the UI
   expose Knowledge Asset ids and accept READY-only deletion?
2. How must retirement fence a currently leased worker so publication cannot
   resurrect deleted content?
3. Can the existing canonical evidence-scope resolver authorize whole-document
   delivery without inventing a weaker check or leaking pending sources?
4. Should pending/failed uploads be deletable in this increment, or is a
   READY-only first slice safer and honest enough?
5. Does archiving both Source Object and Knowledge Asset duplicate authority or
   preserve the current ledger contract?
6. Which negative tests are mandatory for non-disclosure, tenant isolation,
   stale permission evidence, integrity failure, and concurrent deletion?
7. Is extracting the Assistant preview renderer safe, and which file types must
   remain download-only?

## Required response

Return one explicit verdict: `accept`, `accept with changes`, or `reject`.
State the strongest contradiction, concrete must-fix items with repository
evidence, the committed recommendation, and the exact scope that remains
forbidden. Remain read-only and do not edit the worktree.
