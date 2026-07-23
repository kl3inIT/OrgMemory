# Full LightRAG Semantic Port Plan

## PR 1 — Architecture And Parity Ledger

- [x] Supersede scope-reduction decisions explicitly.
- [x] Pin the upstream reference and enumerate semantic capabilities.
- [x] Define adapter and conformance rules.
- [x] Record the twelve-PR integration and review flow.
- [x] Merge to `light-rag` after review and green CI.

## PR 2 — Projection, Evidence And Cache Contracts

- [x] Consolidate one canonical authorization scope and evidence reference.
- [x] Add capability-specific content, lexical, vector, publication, cache,
  and safe telemetry contracts without a lowest-common-denominator store
  interface.
- [x] Define atomic generation-head publication, durable preparation receipts,
  manifest-bound idempotency, published history, abort and cache-isolation
  semantics.
- [x] Record the graph migration prerequisite without falsely declaring
  `GRAPH` atomic before its adapter uses the shared namespace snapshot.
- [x] Keep durable job leasing and retry in the OrgMemory worker/application
  layer; do not duplicate it inside the reusable engine.
- [x] Expand `graph-rag-testkit` with deterministic in-memory reference
  adapters, publication/cache/security fixtures, and adapter conformance
  foundations.
- [x] Prove core runtime classpaths contain no Spring or vendor dependency.

## PR 3 — Executable Parser And Chunker Strategies

- [x] Add parser, tokenizer, embedding and chunker ports only where they cross
  an effect or provider boundary.
- [x] Add parser registry, routing, capability validation, passthrough, and
  third-party registration.
- [x] Implement fixed-token, recursive-character, semantic-vector, and
  paragraph-semantic chunking.
- [x] Persist per-document parser/chunker profile and positional provenance.
- [x] Run golden fixtures against the pinned upstream behavior; do not close a
  row with interfaces alone.

## PR 4 — Multimodal Sidecar And VLM

- [x] Define the sidecar interchange contract.
- [x] Support image, table, equation, surrounding-context, and analysis state.
- [x] Add VLM routing and multimodal chunk construction with inherited ACL and
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
- [ ] Migrate the existing graph projection ports to the shared namespace
  snapshot before PR 7 wires mixed retrieval, then add `GRAPH` to
  `ProjectionKind`.
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
