# 0005 — Secure Java Graph Kernel, Not A Full LightRAG Port

## Status

Accepted on 2026-07-20.

## Context

LightRAG provides useful indexing/query semantics but its Python server, WebUI,
provider/storage adapters, and merged descriptions are not an enterprise
permission boundary. Post-filtering merged graph facts can leak restricted data.

## Decision

Reimplement the semantic kernel in Java: extraction, canonicalization,
evidence contributions, embeddings, local/global/mix retrieval, incremental
delete, and context assembly. `graph-rag-core` is framework-neutral;
Spring AI, PostgreSQL, and conformance fixtures live in separate modules.

PostgreSQL graph tables, recursive CTEs, and pgvector are V1. Canonical nodes and
relations retain identity only; evidence contributions retain source/chunk/ACL
and model provenance. Ranking, traversal, aggregation, and citations are scoped
to authorized contributions.

## Consequences

Pinned upstream LightRAG is a reference/conformance oracle, not production code.
Neo4j may later become a rebuildable projection after benchmarks; it is not a V1
dependency or source of truth.
