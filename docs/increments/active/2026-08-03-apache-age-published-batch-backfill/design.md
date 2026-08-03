# Apache AGE Published-Batch Backfill

## Problem

Production enabled the explicit Apache AGE topology backend after projection
generation 7 had already been published by the relational backend. The
relational publication ledger and immutable graph rows therefore contain the
published batch, while the AGE catalog contains no organization graph and no
ready marker. AGE readers correctly fail closed, which currently makes both
Knowledge Graph exploration and Assistant retrieval unavailable.

The AGE backend increment deliberately did not include a corpus-wide rebuild,
but a backend cutover still needs a bounded way to materialize already-published
relational snapshots. Re-extracting or re-embedding the source corpus would be
slow, expensive, and unnecessary because the canonical relational graph rows
already identify the exact published content.

## Proposal Under Challenge

Add an explicitly invoked one-shot reconciliation mode to the existing API
artifact and image when all of these are true:

- the PostgreSQL GraphRAG integration is enabled;
- the selected topology backend is `APACHE_AGE`;
- published-batch reconciliation is explicitly enabled by configuration.

Deployment first quiesces the old worker. The reconciler will:

1. enumerate published snapshots containing the `GRAPH` projection with stable
   keyset pagination;
2. preflight configured ceilings for batches, relational entities, and relation
   contributions before its first AGE mutation;
3. validate every snapshot and complete persisted batch against the relational
   publication authority and durable `GRAPH` receipt;
4. reject incomplete relational topology before mutation;
5. skip an exact, unique AGE ready marker;
6. otherwise prepare the tenant graph and rebuild only that exact batch from
   immutable relational graph rows under the existing organization advisory
   lock;
7. verify exact copied entity and edge counts, create the ready marker last in
   the same transaction, and revalidate it before proceeding.

The one-shot exits non-zero on any preflight or repair failure. It is
idempotent and changes neither publication heads, generations,
job state, receipts, source objects, chunks, embeddings, nor model routes. A
failed rebuild remains fail-closed and aborts deployment. API and worker
services start only after the one-shot succeeds.

Production Compose exposes the mode under the operations profile with
conservative ceilings. Normal service startup remains non-mutating. Newly
published AGE batches already carry markers through the normal staging
transaction; rerunning the one-shot safely scans and skips exact markers.

## Strongest Counterargument

A one-shot still makes deployment depend on the size and health of historical
data, and rollback cannot undo already committed AGE repairs. Alternatively,
publishing a fresh generation for
each current namespace would reuse the normal publication protocol and avoid a
second path that can manufacture ready markers.

The one-shot reuses the existing API entrypoint with an explicit property and
closes after its runner completes, so it adds no second executable or image.
Republishing current heads does not restore the documented ability to address
retained historical snapshots and would repeat LLM/embedding work even though
exact relational topology already exists.

## Rejected Alternatives

- Do not fall back to the relational topology when AGE is incomplete. That
  violates the explicit backend decision and hides cutover drift.
- Do not repair during a read. Mutating reads add unbounded request latency and
  let concurrent requests stampede the same tenant graph.
- Do not put AGE data movement in Flyway. The copy is operational data repair,
  can be large, and must retain an explicit batch ceiling.
- Do not reindex or re-extract the corpus. The canonical relational topology is
  sufficient to reproduce the exact published graph batch.

## Scope

- Bounded published-snapshot enumeration.
- Exact-marker inspection and idempotent AGE rebuild.
- API startup orchestration and production configuration.
- Focused integration tests and operational documentation.

## Non-goals

- Changing the AGE/relational backend decision.
- Repairing projection content whose relational canonical rows are invalid.
- Reindex controls in the user interface.
- Changing model routing, source ACLs, or document lifecycle.

## Architecture Challenge

`challenge-brief.md` is the reviewer input. `challenge-verdict.md` will record
the binding result before implementation.
