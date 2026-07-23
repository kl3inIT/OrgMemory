# 0005 — Secure Java Graph Kernel, Not A Full LightRAG Port

## Status

Superseded on 2026-07-23 by
[0013](0013-full-lightrag-semantic-port.md).

The V1 recursive-CTE retrieval clause was superseded on 2026-07-23 by
[0010](0010-internal-retrieval-strategies-one-hop-graph.md). Decision 0013 now
supersedes the remaining scope limitation while retaining this decision's
evidence-level authorization model.

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

The Sources workspace reserves a top-level Knowledge graph navigation tab beside
Documents. The graph content starts only after the entity, relationship,
evidence-provenance, permission, and graph-query contracts exist. TanStack Query
remains the owner of server state. A small Zustand store is justified only for
high-frequency interactive graph state such as selection, focus, viewport,
layout, and panels.
The LightRAG WebUI at reference commit `c5bf73d` validates Sigma.js plus
Graphology as candidates for large interactive graphs; those dependencies are
not added until the first graph vertical slice needs them.
