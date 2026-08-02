# 0030 — PostgreSQL graph topology uses an exact selected backend

Status: accepted (2026-08-02)

## Decision

`graph-rag-postgres` selects exactly one topology implementation through
`orgmemory.graph-rag.postgres.topology-backend=APACHE_AGE|RELATIONAL`.
`APACHE_AGE` is the production default. Selecting it constructs an AGE-backed
`GraphStore`; the extension, catalog, session preload, and runtime privileges
are hard dependencies and application startup fails when they are unavailable.
There is no optional or automatic fallback. `RELATIONAL` is an explicit
operator/test selection.

Both choices retain PostgreSQL relational canonical identity, contributions,
publication heads, and authorization evidence. With AGE selected, relational
staging, exact-batch AGE cleanup/rebuild, and the AGE ready marker join one
transaction. Every AGE node and edge is publication-batch-pinned, and cleanup
requires the existing exact store-issued discard permit. The ready marker is a
completeness check, never a second publication authority.

AGE supplies only authorized, cursor-bounded incident-relation pages. It filters
the exact batch, requested entities, and pre-authorized Knowledge Assets before
deduplication, canonical relation ordering, and page limit. The pure-Java
coordinator still owns snapshot validation, seed and endpoint authorization,
page completeness, breadth-first normalization, and the final global limit.

## Why

The previous `DISABLED|OPTIONAL|REQUIRED` property was inert: auto-configuration
always returned `PostgresGraphStore`, and the AGE projection was constructed
only by its test. An operator-visible `REQUIRED` value therefore did not require
or use AGE. The owner selected LightRAG's explicit implementation/dependency
model while retaining OrgMemory's stronger publication, ACL, and provenance
boundaries.

## Strongest counterargument

No production latency corpus proves AGE is faster for OrgMemory's permission-
filtered traversal, while the relational reference is already correct. Removing
the inert setting would have been smaller and safer. The selected path therefore
makes no performance claim and keeps relational topology as a fully conforming
explicit backend.

## Rejected alternatives

- Keep `REQUIRED` and merely instantiate the old revision-mutable projection;
  this would not be batch-safe and could remain write-only after core-owned
  traversal consolidation.
- Retain `OPTIONAL` and fall back on AGE errors; this would let deployments
  silently change runtime semantics.
- Make AGE authoritative for evidence or authorization; descriptions, ACLs,
  provenance, publication, ranking, and citations remain relational/core-owned.
- Add another AGE publication head; the namespace head plus exact transactional
  ready marker is sufficient.

Challenge record:
`docs/increments/completed/2026-08-02-apache-age-graph-backend/`.
