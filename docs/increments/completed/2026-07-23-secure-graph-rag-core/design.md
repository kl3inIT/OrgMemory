# Secure GraphRAG Core

## Outcome

Create the framework-neutral contracts and deterministic reference behavior for
OrgMemory's LightRAG-inspired internal retrieval strategies before adding model
or database adapters.

## Scope

- Add pure-Java `graph-rag-core` and reusable `graph-rag-testkit` projects.
- Model canonical identity separately from evidence contributions.
- Require organization, actor, authorized assets, authorization-model version,
  and evaluation time on every graph read.
- Define extraction contracts compatible with the useful entity/relation fields
  from pinned LightRAG `v1.5.4`.
- Define batched one-hop projection read/write contracts.
- Add internal chunk-only, entity-only, relation-only, secure-hybrid, and
  secure-mix plans without exposing mode selection through the public API.
- Add deterministic rank, round-robin merge, deduplication, and whole-item
  context-budget behavior.
- Prove that unauthorized contributions cannot affect text, adjacency, degree,
  weight, count, or ordering in the in-memory reference projection.

## Boundaries

This increment does not add Flyway migrations, Spring AI, PostgreSQL, pgvector,
worker wiring, runtime endpoints, or model calls. It does not expose LightRAG
query modes. `CHUNK`, `ENTITY`, and `RELATION` are internal retrieval channels
composed by a trusted retrieval strategy.

## Reference

The implementation oracle is the read-only checkout
`D:\OrgMemory\tmp\upstream-lightrag-v1.5.4`, pinned to commit
`9a45b64c2ee25b1d806e90db926a8af37480bb16`. OrgMemory intentionally differs
where upstream merged descriptions or unscoped graph statistics would violate
evidence permissions.

## Acceptance

- Both new projects compile without Spring on their runtime classpath.
- Deterministic algorithm and permission-isolation tests pass.
- Replacement/removal of one revision leaves contributions from other revisions.
- Existing repository tests remain green.
