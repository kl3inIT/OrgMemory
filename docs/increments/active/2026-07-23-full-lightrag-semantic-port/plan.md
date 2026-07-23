# Full LightRAG Semantic Port Plan

## PR 1 — Architecture And Parity Ledger

- [x] Supersede scope-reduction decisions explicitly.
- [x] Pin the upstream reference and enumerate semantic capabilities.
- [x] Define adapter and conformance rules.
- [x] Record the twelve-PR integration and review flow.
- [ ] Merge to `light-rag` after review and green CI.

## PR 2 — Full Pure-Java Core Contracts

- [ ] Add provider-neutral content, state, lexical, vector, graph, cache,
  parser, chunker, multimodal, model-role, tokenizer, reranker, lifecycle,
  query, reference, and export contracts.
- [ ] Expand `graph-rag-testkit` with allow-all oracle fixtures and adapter
  conformance suites.
- [ ] Prove core runtime classpaths contain no Spring or vendor dependency.

## PR 3 — Parser And Chunker Strategies

- [ ] Add parser registry, routing, capability validation, passthrough, and
  third-party registration.
- [ ] Implement fixed-token, recursive-character, semantic-vector, and
  paragraph-semantic chunking.
- [ ] Persist per-document parser/chunker profile and positional provenance.

## PR 4 — Multimodal Sidecar And VLM

- [ ] Define the sidecar interchange contract.
- [ ] Support image, table, equation, surrounding-context, and analysis state.
- [ ] Add VLM routing and multimodal chunk construction with inherited ACL and
  provenance.

## PR 5 — Extraction And Indexing Parity

- [ ] Implement continuation/gleaning, configurable entity guidance, relation
  weights, profiling, summarization, and permission-scoped merge.
- [ ] Complete incremental canonicalization and entity/relation/chunk
  embeddings.
- [ ] Preserve atomic generation publication and bounded concurrency.

## PR 6 — Lifecycle, Recovery And Cache

- [ ] Implement update, delete/rebuild, retry/resume, cancellation, and failed
  batch abort.
- [ ] Implement entity/relation create, edit, merge, delete, and export.
- [ ] Add query/keyword cache contracts, first adapter, and invalidation.

## PR 7 — Full Query Runtime

- [ ] Implement local, global, hybrid, naive, mix, and trusted bypass semantics.
- [ ] Add high/low keyword planning, entity/relation/chunk seeds, graph
  expansion, related-chunk recovery, fusion, rerank, and token budgeting.
- [ ] Return context, prompt, references, raw retrieval trace, and streaming
  results through permission-aware contracts.

## PR 8 — PostgreSQL Adapter Parity

- [ ] Implement all first-production storage/index contracts with PostgreSQL,
  pgvector, FTS, and AGE where appropriate.
- [ ] Remove concrete PostgreSQL retrieval assumptions from core.
- [ ] Pass the complete adapter conformance suite.

## PR 9 — Unified OpenSearch Adapter

- [ ] Implement content/KV, processing state, lexical, vector k-NN, and graph
  storage.
- [ ] Use PPL `graphlookup` when available and bounded batched BFS otherwise.
- [ ] Prove tenant isolation, rebuild, scoring, and lifecycle parity.

## PR 10 — Neo4j Graph Adapter

- [ ] Implement the complete graph contract without owning ACL or provenance.
- [ ] Support bounded configurable traversal and batch adjacency.
- [ ] Pass the same graph conformance and security suite as PostgreSQL.

## PR 11 — Runtime And Product Integration

- [ ] Wire worker indexing, API, Assistant, MCP, source/citation streaming, and
  the authorized graph explorer.
- [ ] Keep Assistant and MCP on the same permission-aware application use cases.
- [ ] Verify real-browser allow, deny, revoke, source preview, and graph flows.

## PR 12 — Evaluation And Production Hardening

- [ ] Run upstream oracle parity, RAGAS, quality/cost/latency baselines, and
  PostgreSQL/OpenSearch/Neo4j load comparisons.
- [ ] Add Langfuse/OpenTelemetry-compatible traces without unsafe prompt
  retention.
- [ ] Complete tenant isolation, ACL leakage, outage, delete/rebuild,
  backup/restore, and operational runbooks.
- [ ] Audit every parity-manifest row before opening `light-rag -> main`.

## Merge Gate For Every PR

- [ ] Relevant local static, unit, integration, contract, and conformance tests
  pass.
- [ ] Backend, Web, OpenFGA, and aggregate CI checks required by the diff are
  green.
- [ ] CodeRabbit has no unresolved actionable finding.
- [ ] The branch merges into `light-rag`, never directly into `main`.
- [ ] The next branch starts from the new remote `light-rag` head.
