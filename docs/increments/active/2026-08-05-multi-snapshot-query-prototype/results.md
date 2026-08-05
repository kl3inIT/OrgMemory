# ADR 0020 Phase 2 local gate evidence

Date: 2026-08-05. Supersedes the earlier five-sample report whose p95 and p99
labels both represented the maximum observation. Environment: disposable
Testcontainers PostgreSQL 18 with pgvector, a fixed four-connection Hikari pool,
fixed synthetic seed, and 1536-dimensional vectors. The benchmark never
registers a Spring runtime query bean and cannot address a non-Testcontainers
database.

## Verdict

- Shadow equivalence: **PASS** for 1, 7, and 20 spaces under narrow and broad
  grants. Every scenario returned its fixture-derived non-empty candidate count,
  and the compound and independently parameterized per-space paths returned the
  same candidates, contribution attribution, scores, and tuple identities.
  Mismatched ACL generations and projection-token substrings are rejected.
- Local latency gate: **FAIL**. 17 of 72 scenarios exceeded the 500 ms
  max-of-five observed-sample check, and all 17 also exceeded 500 ms at the
  median. A median above 500 ms falsifies the binding ADR p95 <= 500 ms gate
  without presenting five observations as a p95 estimate. All 1x and 10x rows
  pass; failures occur at 100x.
- Recall gate: **NOT YET SCORED**. The deterministic scorer and 15-question
  golden-data skeleton are complete, but observations from the current
  keyword-seeded path and raw-query bypass do not exist yet. No recall verdict
  is claimed.
- Production-shaped restored-copy run: not run; it remains plan step 4 and was
  outside this handoff.

ADR 0020 does not permit cutover on this evidence.

## Measurement contract

Each cell below is `median / observed maximum; pool-wait maximum; verdict`, in
milliseconds. Five samples are measured per row; they do not estimate p95 or
p99. The max-of-five check retains the predeclared 500 ms budget without
weakening the binding p95 gate. Concurrency 4 launches up to four samples
together. `COLD` runs `DISCARD PLANS` on the same pooled connection used for the
measured query; PostgreSQL shared buffers are not evicted, so these are
cold-plan rather than cold-buffer numbers. Statements are capped at 5,000 ms,
ten times the gate budget. A timeout is recorded as 5,000 ms and fails the
check.

| Scale | Spaces | Grant | C1 cold | C1 warm | C4 cold | C4 warm |
|---|---:|---|---|---|---|---|
| 1x | 1 | narrow | 8.27 / 10.16; 0.05; PASS | 6.47 / 10.06; 0.05; PASS | 21.90 / 26.93; 2.35; PASS | 11.96 / 12.86; 0.04; PASS |
| 1x | 1 | broad | 6.89 / 9.26; 0.08; PASS | 2.69 / 3.08; 0.04; PASS | 7.67 / 9.41; 0.07; PASS | 14.43 / 17.95; 0.08; PASS |
| 1x | 7 | narrow | 7.77 / 8.68; 0.06; PASS | 4.23 / 4.33; 0.04; PASS | 8.61 / 9.97; 0.13; PASS | 6.95 / 7.64; 0.05; PASS |
| 1x | 7 | broad | 8.93 / 10.93; 0.05; PASS | 5.45 / 6.14; 0.08; PASS | 7.99 / 8.18; 0.11; PASS | 7.35 / 9.41; 0.08; PASS |
| 1x | 20 | narrow | 9.28 / 9.53; 0.09; PASS | 4.58 / 5.68; 0.04; PASS | 8.56 / 9.90; 0.06; PASS | 6.80 / 7.01; 0.07; PASS |
| 1x | 20 | broad | 11.97 / 25.61; 0.06; PASS | 11.34 / 12.04; 0.05; PASS | 15.09 / 15.10; 0.05; PASS | 10.02 / 11.37; 0.08; PASS |
| 10x | 1 | narrow | 10.21 / 12.60; 0.19; PASS | 4.36 / 5.14; 0.06; PASS | 14.04 / 14.17; 2.60; PASS | 4.92 / 5.58; 0.12; PASS |
| 10x | 1 | broad | 12.39 / 12.87; 0.03; PASS | 8.73 / 10.25; 0.05; PASS | 13.33 / 15.19; 0.06; PASS | 12.64 / 12.71; 0.09; PASS |
| 10x | 7 | narrow | 46.24 / 55.05; 0.05; PASS | 36.63 / 38.44; 0.04; PASS | 48.73 / 58.12; 1.77; PASS | 21.05 / 21.53; 0.08; PASS |
| 10x | 7 | broad | 56.16 / 59.39; 0.03; PASS | 51.69 / 59.79; 0.08; PASS | 69.36 / 74.99; 1.24; PASS | 63.02 / 67.25; 0.05; PASS |
| 10x | 20 | narrow | 68.01 / 72.38; 0.03; PASS | 65.63 / 69.97; 0.03; PASS | 81.81 / 88.34; 5.05; PASS | 76.68 / 86.50; 0.06; PASS |
| 10x | 20 | broad | 195.39 / 216.50; 0.08; PASS | 194.97 / 209.93; 0.10; PASS | 249.16 / 259.43; 1.77; PASS | 221.99 / 228.48; 1.44; PASS |
| 100x | 1 | narrow | 96.16 / 102.92; 0.13; PASS | 93.74 / 102.88; 0.02; PASS | 132.66 / 142.76; 3.04; PASS | 103.16 / 119.46; 0.20; PASS |
| 100x | 1 | broad | 375.83 / 384.10; 0.07; PASS | 376.02 / 391.98; 0.02; PASS | 702.52 / 718.91; 1.40; FAIL | 413.16 / 439.96; 1.93; PASS |
| 100x | 7 | narrow | 776.26 / 851.23; 0.03; FAIL | 770.15 / 1069.16; 0.03; FAIL | 821.87 / 876.28; 1.21; FAIL | 804.54 / 854.67; 1.55; FAIL |
| 100x | 7 | broad | 2881.34 / 3200.88; 0.03; FAIL | 2910.10 / 3101.56; 0.04; FAIL | 3212.25 / 3326.90; 2.20; FAIL | 3558.63 / 3622.25; 1.28; FAIL |
| 100x | 20 | narrow | 2473.79 / 2737.32; 0.02; FAIL | 2461.96 / 2661.51; 0.03; FAIL | 2830.71 / 3039.34; 1.32; FAIL | 2883.93 / 3075.60; 1.33; FAIL |
| 100x | 20 | broad | 5000.00 / 5000.00; 0.00; FAIL | 5000.00 / 5000.00; 0.00; FAIL | 5000.00 / 5000.00; 0.00; FAIL | 5000.00 / 5000.00; 0.00; FAIL |

The generated datasets contain 80/800/8,000 vectors, the same number of entity
contributions, and 40/760/7,960 relation contributions at 1x/10x/100x.

## EXPLAIN (ANALYZE, BUFFERS)

The report parser reads the JSON document root and root plan node rather than
depending on textual field order.

| Scale | Spaces | Grant | Root | Planning | Execution | Shared hits | Shared reads |
|---|---:|---|---|---:|---:|---:|---:|
| 1x | 1 | narrow | Sort | 8.47 ms | 2.94 ms | 22 | 0 |
| 1x | 1 | broad | Sort | 3.72 ms | 0.94 ms | 53 | 0 |
| 1x | 7 | narrow | Sort | 3.21 ms | 0.75 ms | 103 | 0 |
| 1x | 7 | broad | Sort | 4.06 ms | 2.23 ms | 341 | 0 |
| 1x | 20 | narrow | Sort | 2.82 ms | 2.12 ms | 285 | 0 |
| 1x | 20 | broad | Sort | 3.64 ms | 3.92 ms | 965 | 0 |
| 10x | 1 | narrow | Sort | 3.70 ms | 2.14 ms | 1,369 | 0 |
| 10x | 1 | broad | Sort | 2.97 ms | 5.83 ms | 5,629 | 0 |
| 10x | 7 | narrow | Sort | 2.62 ms | 17.83 ms | 9,829 | 0 |
| 10x | 7 | broad | Sort | 2.60 ms | 53.76 ms | 38,989 | 0 |
| 10x | 20 | narrow | Sort | 5.01 ms | 75.31 ms | 28,509 | 0 |
| 10x | 20 | broad | Sort | 2.48 ms | 244.93 ms | 111,909 | 0 |
| 100x | 1 | narrow | Sort | 5.41 ms | 105.60 ms | 125,973 | 25 |
| 100x | 1 | broad | Sort | 3.17 ms | 390.78 ms | 502,747 | 10 |
| 100x | 7 | narrow | Sort | 2.91 ms | 916.71 ms | 882,589 | 174 |
| 100x | 7 | broad | Sort | 3.19 ms | 3,404.56 ms | 3,522,296 | 167 |
| 100x | 20 | narrow | Sort | 4.15 ms | 2,791.59 ms | 2,520,703 | 1,389 |
| 100x | 20 | broad | statement timeout | n/a | >5,000 ms | n/a | n/a |

The plan remains rooted at a final sort. Shared-buffer work grows from 965 hits
at 1x/20-space/broad to 111,909 at 10x, and reaches 3,522,296 at
100x/7-space/broad. The set-based shape is therefore semantically viable but
does not satisfy the scale gate without a different physical plan or index
strategy. The threshold was not tuned after observing this result.

## Recall harness status

`evaluation/fixtures/retrieval-recall-golden-v1.json` contains 15 reviewed
question-to-section references over the repository's 40-document demo corpus.
`orgmemory-retrieval-recall` fixes report schema v1 at topK 40, validates
complete observations, computes macro recall@40 for keyword-seeded and bypass
paths, reports diagnostic keyword recall@60, and applies the predeclared
two-percentage-point tolerance. Actual path observations remain required before
the recall gate can be decided.

## Coordinator review amendment (2026-08-05)

1. **Scale labels understate the failure.** The synthetic 1x dataset holds 80
   entity vectors; production `projection_vector_records` holds 10,051.
   Production today therefore sits at approximately the benchmark's 100x row.
   Every 7- and 20-space 100x scenario exceeds 500 ms at the median, and the
   20-space broad scenarios time out at 5,000 ms, while the live per-space path
   serves the same corpus at 166-299 ms per snapshot. The as-built compound
   statement is not merely slow at future scale; it loses to the per-space path
   at the current production size.
2. **The cause is plan shape, not the concept.** `vector_candidates` joins
   authorized scope to vectors first and computes the 1536-dimensional distance
   for every authorized row, then window-ranks (`row_number() OVER (PARTITION BY
   space_id ORDER BY distance)`). No `ORDER BY embedding <=> :query LIMIT k` is
   adjacent to the vector column, so the HNSW index is structurally unusable --
   the exact failure mode the ADR 0020 debate's surviving attack predicted. The
   **store-fanned LATERAL variant** (per space-tuple LATERAL subquery, each an
   index-eligible ordered LIMIT, merged and re-ranked in one statement) is what
   B-R2 specified and has not been measured.

Consequence: gate 2 verdict on the single-scan plan stands as FAIL and cutover
remains blocked. The next evidence step inside this increment is a LATERAL
store-fan benchmark run before any Phase 3 decision, alongside plan steps 3
(recall scoring) and 4 (production-shaped run).
