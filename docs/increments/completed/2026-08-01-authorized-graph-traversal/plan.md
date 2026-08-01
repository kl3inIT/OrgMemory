# Authorized graph traversal coordinator plan

Status: completed (2026-08-01)

## 1. Characterize the unchanged adapters

- Add backend-local tests for zero limit, empty seeds with a fabricated
  snapshot, depth-zero seed ordering, and one global multi-source limit.
- Add a high-degree fixture that records the current fixed-prefix divergence.
- Add OpenSearch PPL success/fallback characterization without changing
  production traversal.
- Run graph-core/testkit and the affected adapter tests.
- Commit characterization separately before production code changes.

## 2. Establish the core-owned reference contract

- Add `AuthorizedGraphTraversalSource` with explicit snapshot validation and
  stable UUID-cursor incident-relation pages.
- Add page invariants and coordinator unit tests before the coordinator
  implementation.
- Implement `AuthorizedGraphTraversal` as level-synchronous multi-source BFS
  with complete-level normalization and one global limit.
- Make `StoreBackedAuthorizedQueryProjection` delegate to the coordinator.
- Remove final-result traversal from `GraphStore`.
- Run `:components:graph-rag-core:test` and
  `:components:graph-rag-testkit:test`.

## 3. Migrate every graph source

- PostgreSQL: explicit readable check and authorized relation-UUID keyset page.
- Neo4j: explicit readable check and equivalent Cypher keyset page.
- OpenSearch: explicit scope/snapshot check and relation-UUID sorted
  `search_after` page over the immutable snapshot.
- In-memory testkit: execute the same coordinator over a deterministic paged
  source rather than its own queue policy.
- Remove PostgreSQL CTE and OpenSearch PPL final-result authority; retain no
  unused compatibility branch that can produce public IDs.
- Run each adapter integration suite after its migration.

## 4. Replace characterization with shared conformance

- Pin all arguments and early-return snapshot validation.
- Pin authorized depth-zero seeds, minimum depth, canonical UUID ties, one
  global limit, cycles, self-loops, disconnected nodes, and seed permutations.
- Pin multi-page high-degree traversal and invalid page/cursor failures.
- Prove hidden contributions and endpoints do not affect results or errors.
- Repeat with shuffled source page order where the fake can simulate it.

## 5. Consolidate and deliver

- Record the core-owned traversal decision in a new ADR.
- Reconcile the secure GraphRAG spec/test pair and their provenance.
- Update architecture only with implemented facts.
- Run IDE/static checks for edited Java, focused modules, then
  `./gradlew --no-daemon clean test` with a terminating timeout.
- Move the increment to completed, update the roadmap, and record verification.
- Commit logical steps, open one PR, address review/CI, merge, and verify
  `origin/main`.
