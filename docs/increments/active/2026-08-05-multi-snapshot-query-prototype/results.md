# ADR 0020 Phase 2 local gate evidence

Date: 2026-08-05. Environment: disposable Testcontainers PostgreSQL 18 with
pgvector, a fixed four-connection Hikari pool, fixed synthetic seed, and
1536-dimensional vectors. The benchmark never registers a Spring runtime query
bean and cannot address a non-Testcontainers database.

## Verdict

- Shadow equivalence: **PASS** for 1, 7, and 20 spaces under narrow and broad
  grants. The compound and independently parameterized per-space paths returned
  the same candidates, contribution attribution, scores, and tuple identities.
- Local latency gate: **FAIL**. 20 of 72 scenarios exceeded the predeclared
  compound p95 threshold of 500 ms. All failures are at 100x except the four
  100x/1-space/narrow scenarios, which passed.
- Recall gate: **NOT YET SCORED**. The deterministic scorer and 15-question
  golden-data skeleton are complete, but observations from the current
  keyword-seeded path and raw-query bypass do not exist yet. No recall verdict
  is claimed.
- Production-shaped restored-copy run: not run; it remains plan step 4 and was
  outside this handoff.

ADR 0020 does not permit cutover on this evidence.

## Measurement contract

Each cell below is `p50 / p95 / p99; pool-wait p95; verdict`, in milliseconds.
Five samples are measured per row. Concurrency 4 launches up to four samples
together. `COLD` discards prepared plans before each sample; PostgreSQL shared
buffers are not evicted, so these are cold-plan rather than cold-buffer numbers.
Statements are capped at 5,000 ms, ten times the gate budget. A timeout is
recorded as 5,000 ms and fails the threshold.

| Scale | Spaces | Grant | C1 cold | C1 warm | C4 cold | C4 warm |
|---|---:|---|---|---|---|---|
| 1x | 1 | narrow | 6.95 / 14.31 / 14.31; 0.03; PASS | 5.45 / 7.02 / 7.02; 0.04; PASS | 17.76 / 23.04 / 23.04; 0.04; PASS | 11.78 / 13.30 / 13.30; 0.06; PASS |
| 1x | 1 | broad | 6.00 / 7.53 / 7.53; 0.07; PASS | 2.15 / 3.06 / 3.06; 0.04; PASS | 8.35 / 10.05 / 10.05; 0.01; PASS | 6.98 / 12.53 / 12.53; 0.06; PASS |
| 1x | 7 | narrow | 7.33 / 9.34 / 9.34; 0.02; PASS | 3.21 / 3.47 / 3.47; 0.04; PASS | 8.96 / 10.01 / 10.01; 0.02; PASS | 7.43 / 8.33 / 8.33; 0.07; PASS |
| 1x | 7 | broad | 8.25 / 10.85 / 10.85; 0.04; PASS | 5.85 / 6.41 / 6.41; 0.04; PASS | 9.75 / 12.00 / 12.00; 0.02; PASS | 8.75 / 10.05 / 10.05; 0.05; PASS |
| 1x | 20 | narrow | 8.85 / 11.83 / 11.83; 0.02; PASS | 6.99 / 7.96 / 7.96; 0.10; PASS | 11.66 / 14.53 / 14.53; 0.02; PASS | 6.73 / 13.16 / 13.16; 0.08; PASS |
| 1x | 20 | broad | 15.16 / 18.21 / 18.21; 0.03; PASS | 12.21 / 13.25 / 13.25; 0.06; PASS | 17.06 / 17.59 / 17.59; 0.24; PASS | 13.79 / 14.78 / 14.78; 0.18; PASS |
| 10x | 1 | narrow | 8.13 / 8.79 / 8.79; 0.04; PASS | 4.66 / 5.04 / 5.04; 0.02; PASS | 11.49 / 11.75 / 11.75; 0.01; PASS | 6.06 / 6.06 / 6.06; 0.05; PASS |
| 10x | 1 | broad | 15.40 / 16.02 / 16.02; 0.01; PASS | 8.99 / 12.10 / 12.10; 0.03; PASS | 16.83 / 17.07 / 17.07; 0.01; PASS | 9.55 / 12.26 / 12.26; 0.07; PASS |
| 10x | 7 | narrow | 23.59 / 31.17 / 31.17; 0.02; PASS | 24.13 / 29.35 / 29.35; 0.02; PASS | 28.42 / 32.02 / 32.02; 0.01; PASS | 25.60 / 28.69 / 28.69; 0.08; PASS |
| 10x | 7 | broad | 69.80 / 74.41 / 74.41; 0.01; PASS | 57.39 / 60.48 / 60.48; 0.07; PASS | 81.39 / 86.10 / 86.10; 0.01; PASS | 77.87 / 79.54 / 79.54; 0.05; PASS |
| 10x | 20 | narrow | 79.25 / 83.52 / 83.52; 0.02; PASS | 58.87 / 70.68 / 70.68; 0.04; PASS | 74.91 / 77.77 / 77.77; 0.01; PASS | 68.66 / 77.43 / 77.43; 0.04; PASS |
| 10x | 20 | broad | 196.91 / 211.81 / 211.81; 0.00; PASS | 199.41 / 206.25 / 206.25; 0.03; PASS | 232.38 / 239.04 / 239.04; 0.00; PASS | 248.97 / 253.10 / 253.10; 1.37; PASS |
| 100x | 1 | narrow | 162.50 / 170.90 / 170.90; 0.00; PASS | 156.69 / 186.50 / 186.50; 0.03; PASS | 200.50 / 210.64 / 210.64; 0.01; PASS | 233.61 / 251.84 / 251.84; 5.23; PASS |
| 100x | 1 | broad | 1009.56 / 5000.00 / 5000.00; 0.00; FAIL | 5000.00 / 5000.00 / 5000.00; 0.00; FAIL | 5000.00 / 5000.00 / 5000.00; 0.00; FAIL | 5000.00 / 5000.00 / 5000.00; 0.00; FAIL |
| 100x | 7 | narrow | 5000.00 / 5000.00 / 5000.00; 0.00; FAIL | 5000.00 / 5000.00 / 5000.00; 0.00; FAIL | 5000.00 / 5000.00 / 5000.00; 0.00; FAIL | 5000.00 / 5000.00 / 5000.00; 0.00; FAIL |
| 100x | 7 | broad | 5000.00 / 5000.00 / 5000.00; 0.00; FAIL | 5000.00 / 5000.00 / 5000.00; 0.00; FAIL | 5000.00 / 5000.00 / 5000.00; 0.00; FAIL | 5000.00 / 5000.00 / 5000.00; 0.00; FAIL |
| 100x | 20 | narrow | 5000.00 / 5000.00 / 5000.00; 0.00; FAIL | 5000.00 / 5000.00 / 5000.00; 0.00; FAIL | 5000.00 / 5000.00 / 5000.00; 0.00; FAIL | 5000.00 / 5000.00 / 5000.00; 0.00; FAIL |
| 100x | 20 | broad | 5000.00 / 5000.00 / 5000.00; 0.00; FAIL | 5000.00 / 5000.00 / 5000.00; 0.00; FAIL | 5000.00 / 5000.00 / 5000.00; 0.00; FAIL | 5000.00 / 5000.00 / 5000.00; 0.00; FAIL |

The generated datasets contain 80/800/8,000 vectors, the same number of
entity contributions, and 40/760/7,960 relation contributions at 1x/10x/100x.

## EXPLAIN (ANALYZE, BUFFERS)

| Scale | Spaces | Grant | Root | Planning | Execution | Shared hits | Shared reads |
|---|---:|---|---|---:|---:|---:|---:|
| 1x | 1 | narrow | Sort | 5.50 ms | 1.75 ms | 22 | 0 |
| 1x | 1 | broad | Sort | 2.83 ms | 0.76 ms | 53 | 0 |
| 1x | 7 | narrow | Sort | 2.45 ms | 0.69 ms | 103 | 0 |
| 1x | 7 | broad | Sort | 2.91 ms | 2.22 ms | 341 | 0 |
| 1x | 20 | narrow | Sort | 4.34 ms | 2.97 ms | 285 | 0 |
| 1x | 20 | broad | Sort | 2.65 ms | 4.56 ms | 965 | 0 |
| 10x | 1 | narrow | Sort | 2.99 ms | 2.03 ms | 1,412 | 0 |
| 10x | 1 | broad | Sort | 4.20 ms | 7.99 ms | 5,481 | 0 |
| 10x | 7 | narrow | Sort | 2.68 ms | 12.33 ms | 9,671 | 0 |
| 10x | 7 | broad | Sort | 3.98 ms | 55.66 ms | 37,835 | 0 |
| 10x | 20 | narrow | Sort | 3.30 ms | 69.56 ms | 27,651 | 0 |
| 10x | 20 | broad | Sort | 2.90 ms | 230.62 ms | 108,153 | 0 |
| 100x | 1 | narrow | Sort | 4.37 ms | 185.75 ms | 127,566 | 32 |
| 100x | 1 | broad | Sort | 5.75 ms | 1,118.69 ms | 988,354 | 3 |
| 100x | 7 | narrow | statement timeout | n/a | >5,000 ms | n/a | n/a |
| 100x | 7 | broad | statement timeout | n/a | >5,000 ms | n/a | n/a |
| 100x | 20 | narrow | statement timeout | n/a | >5,000 ms | n/a | n/a |
| 100x | 20 | broad | statement timeout | n/a | >5,000 ms | n/a | n/a |

The plan remains rooted at a final sort, but shared-buffer work grows from 965
hits at 1x/20-space/broad to 108,153 at 10x and 988,354 already at
100x/1-space/broad. The set-based shape is therefore semantically viable but
does not satisfy the scale gate without a different physical plan or index
strategy. The threshold was not tuned after observing this result.

## Recall harness status

`evaluation/fixtures/retrieval-recall-golden-v1.json` contains 15 reviewed
question-to-section references over the repository's 40-document demo corpus.
`orgmemory-retrieval-recall` validates complete observations, computes macro
recall@40 for keyword-seeded and bypass paths, reports diagnostic keyword
recall@60, and applies the predeclared two-percentage-point tolerance. Actual
path observations remain required before the recall gate can be decided.
