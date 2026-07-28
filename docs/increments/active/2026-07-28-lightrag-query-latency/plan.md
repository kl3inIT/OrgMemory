# LightRAG multi-space query latency plan

## 1. Lock the regression — complete

- Add a multi-space application test that fails when keyword planning or
  embedding is repeated per snapshot.
- Add exact keyword-cache key/hit/miss and trusted-bypass tests.
- Add PostgreSQL vector-order and ACL-generation bulk-query tests.
- Preserve existing authorization-scope change, final BatchCheck and canonical
  evidence recheck tests.

## 2. Restore per-query semantic effects — complete

- Introduce an immutable prepare-once query contract in `graph-rag-core`.
- Keep one-shot engine execution as a delegating compatibility path.
- Reuse one prepared query across all permission-equivalent Knowledge Space
  snapshots.
- Add and configure the independent `KEYWORD_PLANNING` workload route.
- Wire the existing exact model-invocation cache into keyword planning.

## 3. Remove application and storage amplification — complete

- Remove the outer provider-spanning read transaction.
- Bulk-resolve ACL generations while retaining the second scope resolution.
- Eliminate confirmed duplicate graph contribution reads where the same
  immutable result can be reused.
- Make pgvector cosine ordering index-eligible and retain deterministic ties.
- Add bounded, deterministic snapshot execution only after transaction and
  shared-preparation safety tests pass.
- Consolidate and rerank globally, or leave reranking fail-fast disabled until
  that candidate-level contract is complete.

Multi-space reranking is fail-closed when enabled; the production default
remains disabled. A later candidate-level contract may enable one global
reranker call without restoring per-space provider amplification.

## 4. Make latency attributable — complete in code

- Record payload-free authorization, keyword, embedding, per-snapshot,
  consolidation and final-recheck durations.
- Correct the rerank event so it measures only an attempted reranker call.
- The OpenTelemetry adapter test verifies the closed payload-free attribute set,
  original timing, model-route fingerprint, hashed scope fingerprint and cache
  status. Live collector ingestion remains part of release proof.

## 5. Verify and release — repository release complete; live proof pending

- JetBrains MCP was unavailable; the repository fallback compile/core gates and
  mechanical static floor passed.
- Focused graph core/testkit, core permission/retrieval, API route/property,
  OpenTelemetry and PostgreSQL integration tests passed.
- Production Compose interpolation/config validation passed.
- The terminating `clean test` gate passed in 10m48s.
- PR #102 passed review/CI and merged to `main` as `7cf1c8a`.
- Deploy the immutable image and repeat the production MCP search with
  before/after timings.
