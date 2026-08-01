# 0027 — Authorized graph traversal has one core result authority

Status: accepted (2026-08-01)

## Decision

The framework-neutral `AuthorizedGraphTraversal` in `graph-rag-core` is the
only implementation allowed to return expanded entity identifiers. It owns
exact publication-snapshot validation, seed and endpoint authorization,
level-synchronous multi-source breadth-first traversal, complete-page draining,
minimum-depth and canonical-UUID normalization, one global node limit, and
fail-closed response to malformed source behavior.

`GraphStore` implementations expose only snapshot-bound authorized entity reads
and bounded incident-relation pages. Pages use an exclusive canonical relation
UUID cursor, fetch one extra record to prove whether another page exists, and
must be strictly ordered. PostgreSQL, Neo4j, OpenSearch, and the in-memory
testkit execute the same coordinator contract.

Native execution may be added later only as evidence consumed and rechecked by
the core coordinator, with explicit snapshot identity, minimum-depth
candidates, relation or path witnesses, and a completeness proof. It must not
return the public final-ID result directly.

## Why

The former abstract `GraphStore.expandEntityIds` delegated security-sensitive
semantics to adapters. They had already diverged on zero-limit acceptance,
empty-input snapshot validation, seed ordering, global limits, high-degree
completion, and native/fallback behavior. Tests around final adapter results
could sample that drift but could not prevent a new override from bypassing a
required core rule.

Stable authorized pages keep storage-specific query mechanics below the seam
while removing the result-producing escape hatch. Paging bounds transport and
per-page memory; it does not authorize a partial semantic result. The current
`List<UUID>` contract has no representation for partial completion, so an
unprovable or malformed traversal fails.

Pinned LightRAG `v1.5.4` separates ordinary query orchestration from
backend-specific graph exploration. OrgMemory keeps the inexpensive query
shape but does not adopt explorer-specific result semantics because
authorization and immutable publication identity are product contracts.

## Strongest counterargument

A default method on `GraphStore` would be a smaller migration and no
coordinator can prove that a backend silently omitted an authorized relation.
Authoritative storage protocols and real-backend conformance remain necessary
under either design, and a common native-evidence model could itself drift.

The default-method alternative nevertheless preserves adapter final-result
authority, which is the existing failure mode. The selected seam instead makes
the shared path mandatory and tests the source protocol directly on every
production backend.

## Rejected alternatives

- Backend final-ID overrides behind a default reference method, because the
  security and determinism policy remains bypassable.
- Retaining PostgreSQL recursive CTE or OpenSearch PPL as unverified public
  accelerators, because neither carried a completeness proof consumed by core.
- Returning a fixed prefix when paging cannot complete, because the public
  result type cannot disclose that it is partial.

Independent challenge and reference evidence:
`docs/increments/completed/2026-08-01-authorized-graph-traversal/`.
