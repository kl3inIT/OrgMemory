# 0013 — Full LightRAG Semantic Port With Replaceable Adapters

## Status

Accepted on 2026-07-23.

This decision supersedes the scope limits in:

- [0005](0005-secure-java-graph-kernel.md), including “not a full LightRAG
  port” and the exclusion of the upstream runtime/storage capability catalog;
- [0010](0010-internal-retrieval-strategies-one-hop-graph.md), only where
  one-hop PostgreSQL reads were treated as the engine's capability ceiling; and
- [0011](0011-postgresql-multimodel-graph-projection.md), only where Neo4j or
  OpenSearch adapter work required a new architecture decision.

Their evidence-level authorization, provenance, canonical-ledger, deterministic
ranking, and fail-closed requirements remain in force.

## Context

The first graph increments deliberately established the security model before
porting the full algorithm. That foundation now exists: stable Knowledge Assets,
immutable versions, source and ACL generations, evidence contributions,
OpenFGA authorization, durable graph publication, and a PostgreSQL/pgvector/AGE
projection.

Continuing to call the target “LightRAG-inspired” would now create the wrong
constraint. LightRAG `v1.5.4` contains a connected set of capabilities whose
quality and lifecycle semantics depend on each other:

- parser routing, four chunking strategies, multimodal sidecars, and VLM
  analysis;
- extraction continuation/gleaning, entity/relation profiling, deduplication,
  summarization, and incremental merge;
- local, global, hybrid, naive, mix, and bypass query semantics;
- entity, relation, and chunk retrieval, graph-neighborhood recovery, rerank,
  token budgets, references, raw context, and query caching;
- incremental update, delete/rebuild, entity/relation editing, export,
  recovery, evaluation, and tracing; and
- replaceable KV/content, vector, graph, and processing-state storage families.

Porting only the currently convenient subset would make later adapters and
quality features require another core redesign.

## Decision

OrgMemory will implement a full semantic port of the LightRAG `v1.5.4` core and
runtime capabilities in Java.

“Full semantic port” means equivalent contracts, lifecycle, algorithms,
extension points, and observable behavior. It does not mean translating Python
syntax or copying FastAPI and the upstream WebUI file by file. Spring Boot owns
deployment and delivery, Spring AI implements model-facing adapters, and the
OrgMemory web application owns the product experience.

### Core Boundary

`graph-rag-core` remains pure Java and is an executable functional core, not an
interface-only compatibility layer. It owns deterministic LightRAG algorithms
and orchestration for:

- content and processing state;
- lexical, vector, and graph indexes;
- document parsing and chunking strategies;
- multimodal analysis;
- extraction, continuation, profiling, and summarization;
- model-role selection, embeddings, tokenization, and reranking;
- query planning, graph expansion, context assembly, references, and cache;
- incremental indexing, commit/abort, delete/rebuild, editing, and export.

Core code cannot import Spring, SQL, an SDK, or a vendor-specific type.

Only effect boundaries become ports: model invocation, embeddings,
provider-specific tokenization or reranking, parser engines, durable
persistence, cache persistence, and telemetry export. Spring AI implements
model-facing adapters and may expose the completed retrieval engine through a
`DocumentRetriever` bridge; it does not own query planning, chunking parity,
authorization, graph traversal, fusion, token budgets, or citation assembly.

Core APIs remain synchronous and Reactor-free. Spring Boot owns configuration,
security, durable jobs, bounded virtual-thread execution, transactions, and
streaming delivery. Spring AI `TokenTextSplitter`, RAG advisors, vector stores,
and semantic response caches may be optional adapters only when their behavior
passes the same parity and authorization contracts.

Derived content, lexical, vector, and graph data is staged by generation.
Publication becomes visible through one compare-and-set generation head after
every required projection is prepared. Per-store commit methods cannot claim
cross-store atomicity. Durable worker lease/retry state remains in the
OrgMemory application layer instead of being duplicated in the reusable
engine.

The publication subject is a `ProjectionNamespace` (organization, workspace,
collection), not an individual Knowledge Asset version. Each asset-version
ingest contributes one delta batch and advances that namespace generation.
Queries pin one namespace snapshot so a shared graph, content, lexical and
vector view cannot tear across independently changing assets. Cross-namespace
retrieval is outside the current cache contract and must either be composed as
explicit subqueries or receive a multi-snapshot contract before it is exposed.

Each batch carries one producer-computed canonical `manifestFingerprint`,
derived from its sorted per-projection artifact manifests. The publication
store treats the fingerprint as opaque, persists it on the visible snapshot,
and rejects reuse of a batch ID or namespace-scoped idempotency key with a
different fingerprint. This makes crash retries truthful without duplicating
canonicalization inside storage adapters.

The graph adapter predates this shared publication contract. It remains
specialized until its reads and writes accept the namespace snapshot; it cannot
participate in mixed retrieval before that migration is complete. Omitting
`GRAPH` from the initial `ProjectionKind` enum records that implementation
truth rather than claiming false atomicity. The full graph capability remains
required by PR 8 and the parity manifest.

### Adapter Rule

Every external capability has a port and a shared conformance suite before its
first production adapter is accepted. One production adapter may ship before
the rest, but the first adapter cannot define the core contract around its own
query language or data model.

The first and planned adapters are:

| Capability | First adapter | Additional adapters |
| --- | --- | --- |
| Binary evidence | MinIO/S3-compatible | AWS S3, Azure Blob |
| Content and processing state | PostgreSQL | OpenSearch, Redis/MongoDB where appropriate |
| Lexical search | PostgreSQL FTS | OpenSearch |
| Vector search | pgvector | OpenSearch k-NN, Qdrant, Milvus |
| Graph | PostgreSQL/AGE | Neo4j, OpenSearch, Memgraph |
| Query cache | bounded local/PostgreSQL | Redis, OpenSearch |
| Parsing | Tika and native PDF | Docling, MinerU, third-party registry entries |
| Model operations | Spring AI routes | provider-specific adapters |

PostgreSQL is the first graph adapter, not the only supported graph model.
Neo4j and OpenSearch implement the same graph contract and conformance suite
without changing the query engine.

### Security-Preserving Parity

Upstream behavior is ported in allow-all conformance fixtures. Enterprise
fixtures apply stricter invariants:

- PostgreSQL remains canonical for source identity, immutable evidence,
  lifecycle, ACL generations, and audit.
- Search, vector, graph, and cache data are rebuildable projections.
- Entity and relation text remains on evidence contributions. A merged
  description is computed only from contributions visible to the actor.
- Authorization applies before ranking and limits, through every traversal and
  aggregation, and again before a citation or source is returned.
- Product APIs cannot use a LightRAG mode to bypass authorization. The engine
  still implements every upstream query strategy for planning, evaluation, and
  trusted routing.

These are security adaptations of the full semantics, not feature omissions.

### Compatibility Evidence

The pinned upstream repository and paper are the specification oracle. The
[parity manifest](../research/lightrag-v1.5.4-parity-manifest.md) maps every
required capability to:

- the upstream source symbol or documented behavior;
- the Java contract and implementation;
- allow-all golden fixtures;
- OrgMemory authorization fixtures; and
- the PR and verification evidence that close the row.

A green compilation alone cannot mark a manifest row complete.

## Consequences

The port is a twelve-PR program integrated through the `light-rag` branch. Each
PR branches from the latest `light-rag`, receives review and CI, and merges back
only when all required checks are green.

Adapter implementations may be staged, but their extension boundary is not
deferred. PostgreSQL remains usable throughout the program while OpenSearch and
Neo4j are added without replacing canonical evidence or authorization.

The larger scope increases implementation and conformance work. It removes the
greater risk of repeatedly redesigning the engine around partial ports.
