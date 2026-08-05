# Multi-snapshot query prototype and evaluation harness plan

Design: [design.md](design.md). Gates: ADR 0020 conditions 2–3.

## 1. Compound statement and shadow-compare

- Author the `AuthorizedMultiSnapshotQuery` SQL: tuple-set join (space,
  batch, generation, ACL generation, asset ids) → vector seed ranking →
  depth-1 expansion → contribution aggregation → global LIMIT, every row
  carrying space/batch/asset identity.
- Build the Testcontainers benchmark fixture that loads the current-shape
  projection and runs both paths.
- Assert result-set equivalence (candidates + attribution) between the
  compound statement and N per-space queries across 1/7/20 spaces and
  narrow/broad grants. Equivalence failures stop the increment before any
  timing is reported.

## 2. Scale and measure

- Deterministic synthetic generators for 10× and 100× projections.
- Measurement per the hardening runbook: cold/warm, ≥5 repetitions,
  concurrency 1 and 4, `EXPLAIN (ANALYZE, BUFFERS)`, p50/p95/p99, pool
  waits. Predeclared threshold: compound p95 ≤ 500 ms at every scale point.
- Record pass/fail per scenario in a results document inside this
  increment; a failed scenario is evidence, not a reason to tune the
  threshold.

## 3. Retrieval evaluation set

Status: **complete (2026-08-05)**. The committed v2 observation and report
artifacts record a passing 43-case recall gate; see [results.md](results.md).

Use the 50 official cases in `demo/fixtures/public-evaluation.json` as the
single source, deriving document-level recall goldens from its 43 Allow cases
and scoring the later production transcript offline for permission, exact
citation-set, multi-document, and latency correctness. Compare keyword-seeded
and raw-query-bypass document recall@40 (diagnostic keyword topK 60); bypass
must remain within 2 points. The optional LightRAG-style judge is pluggable and
disabled by default.

## 4. Production-shaped shadow run (zm)

- Run the benchmark's 1×-scale scenarios against a restored copy of the
  production projection (ops profile, read-only, never the live database),
  capturing the same metrics. Shared-Postgres noise is part of the
  intended signal.

## 5. Verdict and consolidation

- Write the gate verdict (pass/fail per ADR 0020 conditions 2–3) into this
  increment and reference it from a new decision entry only if a gate
  fails (falsification supersedes the cutover intent).
- If both gates pass: open the Phase 3 cutover increment proposal,
  including the deadline-aware admission and cooperative cancellation
  design required by the deferred PR #292 finding.
- Consolidate reusable benchmark mechanics into the testing guideline;
  refresh affected spec/test `Source:`/`Reconciled:` lines; roadmap update.
