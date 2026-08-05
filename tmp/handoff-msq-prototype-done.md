# ADR 0020 Phase 2 handoff completion

Date: 2026-08-05
Branch: `feat/msq-prototype-bench`

## Outcome

Plan steps 1-3 are implemented without live retrieval wiring, deployment,
cache changes, authorization-sequence changes, or Phase 1 edits.

- Shadow equivalence passes for 1/7/20 spaces and narrow/broad grants.
- The local compound-query latency gate fails: 20 of 72 scenarios exceed the
  predeclared p95 <= 500 ms threshold. Every 1x and 10x scenario passes; at
  100x only the four 1-space/narrow cold/warm, concurrency-1/4 scenarios pass.
- The recall@40 harness and 15-question golden-data skeleton are complete.
  Actual keyword-seeded and bypass observations are not available, so the
  recall gate is explicitly not scored.
- The production-shaped restored-copy ZM run remains plan step 4 and was not
  in this handoff's scope.

ADR 0020 therefore does not permit cutover on this evidence.

## Files

- `integrations/graph-rag-postgres/src/test/java/com/orgmemory/graphrag/postgres/AuthorizedMultiSnapshotQuery.java`
- `integrations/graph-rag-postgres/src/test/java/com/orgmemory/graphrag/postgres/AuthorizedMultiSnapshotQueryIntegrationTests.java`
- `integrations/graph-rag-postgres/src/test/java/com/orgmemory/graphrag/postgres/MultiSnapshotSyntheticDataset.java`
- `docs/increments/active/2026-08-05-multi-snapshot-query-prototype/results.md`
- `evaluation/src/orgmemory_eval/retrieval_recall.py`
- `evaluation/tests/test_retrieval_recall.py`
- `evaluation/fixtures/retrieval-recall-golden-v1.json`
- `evaluation/README.md`
- `evaluation/pyproject.toml`

## Verification

- Opt-in benchmark:
  `ORGMEMORY_RUN_MSQ_BENCHMARK=true` plus the focused Gradle benchmark test —
  PASS as a harness run in 8m06s; 72 scenario rows and 18 EXPLAIN attempts
  recorded, with threshold failures retained as evidence.
- Focused normal PostgreSQL shadow test — PASS; the opt-in benchmark is skipped.
- `uv run --frozen pytest` — 37 passed.
- `uv run --frozen ruff check src tests` — PASS.
- Mechanical package/zero-byte checks — PASS.
- `git diff --check` — PASS before commits.
- `./gradlew.bat --no-daemon clean test` — PASS in 9m05s.
- JetBrains MCP inspection was unavailable; Gradle compile/test gates were used.

## EXPLAIN highlights

- 1x / 20 spaces / broad: Sort root, 4.56 ms execution, 965 shared hits.
- 10x / 20 spaces / broad: Sort root, 230.62 ms execution, 108,153 shared hits.
- 100x / 1 space / broad: Sort root, 1,118.69 ms execution, 988,354 shared hits.
- 100x / 7 and 20 spaces: EXPLAIN ANALYZE exceeded the 5,000 ms statement cap.

The growing shared-buffer work falsifies the current physical plan at 100x;
the threshold was not changed or tuned after measurement.

## Commits

- `f0cca0f3` — `test(evaluation): add retrieval recall gate harness`
- `17e22b2e` — `test(graph-rag): prototype multi-snapshot query plane`
