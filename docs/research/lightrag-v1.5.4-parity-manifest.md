# LightRAG v1.5.4 Semantic Parity Manifest

## Purpose

This manifest is the scope ledger for the Java port. A capability cannot be
removed because its adapter is not yet implemented or because an earlier
increment excluded it.

Upstream baseline:

- repository: <https://github.com/HKUDS/LightRAG>
- paper: <https://arxiv.org/abs/2410.05779>
- stable tag: `v1.5.4`
- commit: `9a45b64c2ee25b1d806e90db926a8af37480bb16`
- audited checkout: `D:\OrgMemory\tmp\upstream-lightrag-v1.5.4`

The checkout is reference-only and is not packaged with OrgMemory.

## Completion States

- `implemented`: current code and tests prove the required behavior.
- `partial`: a valid foundation exists, but upstream semantics are incomplete.
- `missing`: no implementation proves the capability.
- `planned`: the port program has assigned the capability to a PR.

Every `partial` or `missing` row remains required until its acceptance evidence
exists.

## Capability Matrix

| Area | Required semantic capability | Current evidence | State | Closing PR |
| --- | --- | --- | --- | --- |
| Core | Framework-neutral engine and storage/model ports | Pure-Java authorization, evidence, projection, cache and telemetry boundaries exist | partial | 2 |
| Core | Shared adapter conformance suite | Graph security plus in-memory publication/cache/content fixtures | partial | 2 |
| Storage | KV/content storage lifecycle, batch commit/abort, drop | Capability-specific staged content store and atomic generation-head contract | partial | 2, 8 |
| Storage | Processing/doc-status queries and recovery | OrgMemory durable worker jobs own lease/retry semantics; portable engine status is deliberately not duplicated | partial | 6, 8 |
| Storage | Lexical index port | Staged, permission-scoped lexical contract; production adapter remains pending | partial | 2, 8 |
| Storage | Vector index port for chunks/entities/relations | Primitive-vector, staged, permission-scoped contract plus existing graph vector ports | partial | 2, 8 |
| Storage | Graph node/edge/degree/adjacency/batch contract | Specialized graph ports exist; shared namespace snapshot migration remains | partial | 8 |
| Storage | OpenSearch unified KV/vector/graph/status backend | None | missing | 9 |
| Storage | Neo4j graph backend | None | missing | 10 |
| Parsing | Parser registry and runtime routing | Immutable registry snapshot, suffix/explicit routing and startup capability validation | implemented | 3 |
| Parsing | Native, legacy, passthrough and third-party parser SPI | Native/legacy routes, passthrough/reuse parsers and ServiceLoader plugin registration | implemented | 3 |
| Chunking | Fixed-token strategy | Pure-Java overlapping token windows with exact canonical-text offsets and pinned upstream fixture | implemented | 3 |
| Chunking | Recursive-character strategy | Pure-Java recursive separator strategy with overlap, hard cap and exact provenance | implemented | 3 |
| Chunking | Semantic-vector strategy | Batched embedding adapter, four threshold modes, deterministic breakpoints and hard-cap enforcement | implemented | 3 |
| Chunking | Paragraph-semantic strategy | Structured blocks, table-row/header handling, short-paragraph anchors, part labels and level-aware merge | implemented | 3 |
| Chunking | Per-document strategy options and version snapshot | Requested/actual components and options, tokenizer/embedding ids, canonical hash and profile hash persist per revision | implemented | 3 |
| Multimodal | Sidecar interchange format | Validated LightRAG split-bundle decoder with canonical hashes, anchors and opaque artifacts | implemented | 4 |
| Multimodal | Image, table and equation extraction | Executable multimodal core plus Spring AI image/text-analysis adapter and terminal outcome contract | implemented | 4 |
| Multimodal | Surrounding-context enrichment and VLM analysis | Token-bounded surrounding context, policy preflight, content-addressed invocation identity and derived chunks | implemented | 4 |
| Extraction | Structured entity/relation extraction | Pure-Java prompt/orchestration with Spring AI invocation and structured-response adapter | implemented | 5 |
| Extraction | Continuation/gleaning | Full initial-assistant-continuation conversation, token guard, correction merge and endpoint validation | implemented | 5 |
| Extraction | Configurable entity-type guidance and language profile | Immutable guidance, trusted examples, language, token-bounded section context and response limits included in the extraction-profile fingerprint | implemented | 5 |
| Extraction | Relation keywords, weight and binary decomposition | Binary relations, evidence-scoped type/keywords, positive support weight and visible-only aggregation | implemented | 5 |
| Extraction | Entity/relation profiling and description summarization | Per-round provider/estimated tokens and latency plus executable permission-scoped iterative map/reduce summarization | implemented | 5 |
| Indexing | Deduplication and canonical merge | Deterministic name/endpoint identities with evidence-scoped semantic contributions and type-drift tests | implemented | 5 |
| Indexing | Incremental update without full rebuild | Immutable revisions, version-head replacement and manifest-bound graph publication | implemented | 5, 6 |
| Indexing | Atomic publication and failed-batch abort | Durable PostgreSQL publication, idempotency conflict detection and previous-head preservation | implemented | 6 conformance |
| Lifecycle | Delete document and rebuild remaining contributions | Fail-closed asset retirement removes only the retired revision and reaggregates surviving evidence | implemented | 6 |
| Lifecycle | Retry, resume, stale-work cancellation and recovery | Durable retry/resume/cancel/supersede transitions, lease recovery, timeout and pre-publication revalidation | implemented | 6 |
| Lifecycle | Create/edit/delete/merge entity and relation | Append-only curated contributions, reversible aliases and reversible suppressions | implemented | 6 |
| Lifecycle | Export graph, entities, relations and evidence | Permission-scoped audited JSON, CSV, Markdown and text export with provenance | implemented | 6 |
| Lifecycle | Query/keyword cache and invalidation | Canonical exact model/query caches bound to namespace, publication and authorization state | implemented | 6 |
| Query | Local low-level entity retrieval | Ports exist; no runtime orchestration | partial | 7 |
| Query | Global high-level relation retrieval | Ports exist; no runtime orchestration | partial | 7 |
| Query | Hybrid entity plus relation retrieval | Strategy record exists; no runtime orchestration | partial | 7 |
| Query | Naive chunk retrieval | Secure FTS/pgvector retrieval exists | partial | 7 |
| Query | Mix graph plus chunk retrieval | Plan exists; no runtime orchestration | partial | 7 |
| Query | Bypass with caller-supplied keywords for trusted use | None | missing | 7 |
| Query | High-level and low-level keyword extraction | None | missing | 7 |
| Query | Shared query embedding across all channels | Query embedding exists for chunk path | partial | 7 |
| Query | One-hop neighbor and high-order relatedness recovery | Storage candidates exist; orchestration missing | partial | 7 |
| Query | Related original-chunk recovery | None | missing | 7 |
| Query | Deterministic fusion, deduplication and round-robin merge | Pure-Java primitives exist | partial | 7 |
| Query | Reranking and score provenance | None | missing | 7 |
| Query | Entity, relation and total token budgets | Pure-Java budget primitives exist | partial | 7 |
| Query | Context, prompt, reference and raw-data outputs | Chunk citations exist; graph context is missing | partial | 7 |
| Query | Streaming answer generation | Assistant stream exists; full engine not wired | partial | 7, 11 |
| Runtime | Role-specific extraction/query/keyword/VLM/embedding routes | Some AI routes exist | partial | 2, 4, 5, 7 |
| Runtime | Bounded concurrency and cancellation | Worker uses bounded virtual threads | partial | 5, 6 |
| Runtime | Worker/API/Assistant/MCP integration | Chunk Assistant exists; graph engine is not wired | partial | 11 |
| UI | Authorized citations and source preview | Planned source viewer | missing | 11 |
| UI | Permission-aware graph explorer | Disabled navigation target | missing | 11 |
| Evaluation | Upstream allow-all golden fixtures | No executable oracle yet | missing | 2, 12 |
| Evaluation | RAGAS quality evaluation | None | missing | 12 |
| Observability | Langfuse/OpenTelemetry-compatible tracing | General telemetry foundation only | partial | 12 |
| Security | Cross-tenant and denied contribution isolation | Strong graph/retrieval tests exist | implemented | every PR |
| Performance | PostgreSQL/OpenSearch/Neo4j comparison | None | missing | 12 |

## Oracle And Conformance Rules

1. Run the pinned Python implementation on sanitized deterministic fixtures.
2. Capture normalized entities, relations, keywords, retrieval channels,
   references, and token-allocation decisions as versioned JSON.
3. Do not compare provider prose byte for byte. Compare normalized semantic
   records, ordering invariants, budgets, and lifecycle effects.
4. Run the same fixture through Java in allow-all mode.
5. Run an OrgMemory variant where contributions have different ACLs and prove
   denied text, identities, counts, degrees, weights, neighbors, scores, timing,
   and citations cannot influence the visible result.
6. Record the upstream tag, prompt profile, model route, embedding profile,
   random seed where available, and fixture hash.

## Adapter Conformance Rules

Every content/state, lexical, vector, or graph adapter must prove:

- tenant isolation and workspace naming;
- bounded batch writes and explicit commit/abort behavior;
- idempotent retry and monotonic generation publication;
- complete delete/rebuild behavior;
- deterministic pagination and limits;
- consistent thresholds and score normalization;
- no permission expansion;
- health, startup validation, and fail-fast configuration; and
- export/import or rebuild from the canonical ledger.

An adapter-specific integration test cannot replace the shared conformance
suite.

## Program Mapping

The implementation sequence and merge gates are maintained in the
[active program plan](../increments/active/2026-07-23-full-lightrag-semantic-port/plan.md).
