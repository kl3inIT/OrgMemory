# 0010 — Internal Retrieval Strategies Over A One-Hop Relational Graph

## Status

Accepted on 2026-07-23. This decision supersedes only the V1 recursive-CTE
retrieval clause in [0005](0005-secure-java-graph-kernel.md).

## Context

LightRAG `v1.5.4` is the semantic reference for entity, relation, and chunk
retrieval, deterministic merging, and context budgets. Its public query modes
and globally merged descriptions are not suitable as OrgMemory's product API or
permission boundary. Current LightRAG retrieval expands batched one-hop
adjacency; a recursive traversal is not required on the query hot path.

OrgMemory must also preserve evidence-level ACL and provenance. An entity or
relationship may have contributions from assets with different visibility.
Computing a global description, degree, weight, neighbor count, or ranking
signal before authorization can leak restricted knowledge even when the final
text is filtered.

## Decision

OrgMemory's Assistant and MCP use one stable secure-retrieval API. The graph
core supports internal strategies composed from three independently testable
channels:

- `CHUNK`: the existing permission-aware FTS and pgvector retrieval;
- `ENTITY`: entity seeds and authorized entity contributions;
- `RELATION`: relation seeds and authorized relation contributions.

The predefined strategies are:

- `CHUNK_ONLY`: chunk retrieval;
- `ENTITY_ONLY`: entity retrieval for focused evaluation or routing;
- `RELATION_ONLY`: relation retrieval for focused evaluation or routing;
- `SECURE_HYBRID`: entity plus relation retrieval;
- `SECURE_MIX`: entity, relation, and chunk retrieval.

`SECURE_MIX` is the default product plan. These strategies are not request
parameters in the public API. A trusted internal planner may select a narrower
strategy for evaluation, routing, cost control, or fail-closed degradation
without changing the Assistant/MCP contract. LightRAG names such as local,
global, naive, and bypass do not become product-facing modes.

The reusable graph core is pure Java. Canonical entities and relations retain
identity only. Descriptions, keywords, confidence, source revision, chunk,
Knowledge Asset, ACL snapshot/generation, projection generation, extractor,
model, prompt version, and extraction time live on immutable contributions.

Every graph read requires an authorization scope. Seed filtering happens before
ranking and limits. Degree, weight, aggregation, adjacency, context assembly,
and citations use only visible contributions. Storage adapters implement
batched one-hop relational reads for V1. A bounded recursive query is reserved
for an explicit graph-explorer or measured multi-hop requirement.

Context construction preserves LightRAG's useful invariants: separate entity,
relation, and total budgets; deterministic ordering; whole-item allocation; and
a dynamic chunk remainder after prompt, query, graph context, and safety buffer.
Provider tokenization remains an adapter concern.

## Consequences

PostgreSQL remains the first graph projection and pgvector store. Neo4j may be a
read-only rebuildable projection only when measured requirements need at least
three-hop traversal, graph algorithms, or PostgreSQL misses an agreed latency
target. It never becomes the canonical evidence or permission store.

Conformance means strategy/channel semantics on sanitized allow-all fixtures,
not byte-for-byte parity with LightRAG. OrgMemory-specific tests take precedence:
restricted descriptions, neighbor identities, degree, counts, scores, and
citations must not leak. PostgreSQL and Spring AI adapters follow only after the
framework-neutral contracts and reference testkit pass.
