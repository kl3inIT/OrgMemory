# Secure GraphRAG

## Current Contract

- `graph-rag-core` is pure Java and owns canonical graph identity, immutable
  evidence contributions, authorization-scoped read ports, atomic revision
  replacement, internal retrieval strategies, and context-budget rules.
- Canonical entities and relations do not contain merged descriptions.
  Descriptions, keywords, confidence, source revision, chunk, Knowledge Asset,
  ACL, projection, model, prompt, and extraction time remain on contributions.
- Every graph read requires an `AuthorizedEvidenceScope`; ranking, adjacency,
  degree, weight, aggregation, and citations can use only visible
  contributions.
- `SECURE_MIX` is the product default. Strategy selection remains internal.
- Query results preserve structured entity, relation, and chunk selections.
  Entity and relation descriptions retain their individual chunk evidence;
  they are never reduced to an authorization-free merged string.
- One deterministic renderer applies the final input-token budget across all
  selected Knowledge Spaces and assigns references to every contributing
  evidence item.

## Structured Extraction

- `graph-rag-spring-ai` implements `EntityRelationExtractor` through Spring AI
  `ChatClient` structured output.
- The adapter is explicitly constructed with a provider id and `ChatModel`; it
  is not discovered as a generic Spring bean.
- The request profile supplies the model, prompt version, and item limits. The
  provider and prompt version must match the configured adapter.
- Source content is placed in the user message as untrusted evidence. The
  system instruction prohibits following source-embedded instructions or using
  facts outside the chunk.
- The response uses response-local entity references. Every relationship must
  resolve both endpoints within the same response.
- Malformed structured output, invalid orientation/confidence, limit overflow,
  duplicate references, unresolved endpoints, and provenance mismatch fail
  closed before a projection writer is called.

## PostgreSQL Projection

- `graph-rag-postgres` implements content, FTS lexical, pgvector, graph,
  publication, seed, embedding, and topology-candidate ports without becoming
  an authorization authority.
- Content, lexical, vector, and graph records stage under one immutable batch
  id. A namespace publication CAS exposes all required projection kinds
  together; losing and aborted batches never enter read history.
- New batches copy the exact published predecessor before applying mutations.
  Old winning batches remain readable, and discard removes unreachable staged
  data. Reads validate the complete snapshot identity before touching records.
- Canonical identity, contributions, publication heads, and entity/relation
  embeddings are stored relationally. Every query applies organization and the
  pre-authorized Knowledge Asset set before aggregation, distance threshold, and
  limit.
- Revision replacement is atomic and generation-monotonic under a transaction
  advisory lock. Contribution and embedding writes are bounded by both record
  count and estimated payload bytes.
- pgvector supports exact, HNSW, half-vector HNSW, IVFFlat, and optional
  VChordRQ index strategies for immutable shared-projection vectors. Indexes
  are rebuildable and embedding profiles remain immutable.
- Apache AGE stores topology identity and evidence identifiers only. Bounded
  traversal filters every edge by authorized Knowledge Asset; all returned IDs
  remain candidates requiring relational evidence recheck.
- A globally bounded breadth-first relational traversal implements the same
  topology port when AGE is disabled. A future Neo4j projection can implement
  that port without changing core retrieval contracts.

## Worker Publication

- A durable graph-index job is inserted only after the canonical source
  revision reaches `READY`. The job is unique per immutable Knowledge Asset
  version and immutable `GraphProcessingProfile`, and stores lease, attempts,
  retry time, and bounded failure evidence.
- `GraphProcessingProfile` and `EmbeddingProfile` are independent coordinates.
  The former snapshots algorithm, complete extraction settings, exact prompt
  templates, merge semantics and embedding-payload format; the latter owns
  provider/model/vector geometry. Changing either produces a new rebuildable
  generation without mutating a completed historical job.
- Claims pin the current asset/version/revision, active chunk generation, ACL
  snapshot/generation, language, immutable embedding profile and exact
  hash-addressed graph-processing profile. A retry reuses those coordinates;
  an explicit rebuild resolves the current profile and enqueues a distinct job
  only when its profile hash differs.
- Chunk extraction uses bounded virtual-thread concurrency and renews the lease
  between batches. Model output remains untrusted and must satisfy the
  structured extraction contract before assembly.
- Unicode-normalized entity and relation keys create deterministic,
  organization-scoped identities. Descriptions and confidence remain separate
  per-chunk evidence contributions.
- Contributions and their entity/relation embeddings publish through one
  PostgreSQL transaction after a current-version recheck. Retries cannot expose
  a partial generation or move the projection head backwards.
- The graph extraction route is independently configurable from Assistant chat;
  the graph embedding route must still equal the Knowledge Asset version's
  immutable embedding profile.

## Runtime Delivery

- Assistant retrieval resolves the full canonical ACL/classification/lifecycle
  scope before the graph engine or model sees evidence.
- The complete selected entity/relation/chunk evidence closure is BatchChecked
  and re-read from the canonical ledger after ranking. Scope, OpenFGA model,
  ACL snapshot, source revision, and projection generation must still match.
  That verified closure is the request authorization snapshot; the same
  pure-Java renderer creates the model prompt and citation numbering, and
  answer tokens stream without replaying the full authorization pipeline after
  generation.
- Reranking is server-owned typed policy. It defaults off, cannot be enabled
  without a named adapter, and a transient provider failure emits a sanitized
  fallback event before using the already-authorized retrieval order.
- Citation URLs point to an authenticated backend endpoint. The endpoint applies
  the current canonical authorization boundary on each open, streams the
  original evidence from object storage, and never exposes a MinIO key or
  presigned storage URL.
- Read-only MCP search uses the same application retrieval boundary. Graph
  explorer access stays curator/admin-only.
