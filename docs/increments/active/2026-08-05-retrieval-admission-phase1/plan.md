# Retrieval admission control and pool right-sizing plan

Design: [design.md](design.md). Decision: ADR 0020 conditions 1 and 7.

## 1. Lock current behavior

- Add a test that fails if a snapshot task requests a JDBC connection before
  holding an admission permit (admission-before-checkout invariant).
- Add a test proving a snapshot failure under continuous admission cancels
  outstanding tasks and fails the retrieval (fail-closed parity with the
  barrier loop).
- Add a property test for the superseded-but-validated
  `maximumConcurrentSpaces` configuration.

## 2. Admission control

- Introduce the JVM-wide fair 4-permit retrieval semaphore behind a
  configuration property, acquired before connection checkout, released after
  snapshot materialization.
- Replace the batched `future.get()` barrier in
  `GraphRagKnowledgeRetrievalService` with submit-all + semaphore admission,
  preserving consolidation order and cancellation semantics.

## 3. Configuration

- API Hikari `maximum-pool-size` 12 → 8, `minimum-idle` 2 → 8 in
  `application-prod.yml`; leave the worker pool unchanged.
- `topK` default 60 → 40 in `GraphRagQueryRuntimeProperties` and
  `application.yml`; confirm the `topK * 4` ceilings follow.

## 4. TTFT attribution

- Emit payload-free assistant-layer stage durations: grounding-to-prompt
  assembly, history load, retrieval-completion-to-first-token gap.
- Extend the OpenTelemetry sink test for the new closed attribute set.

## 5. Verify and release

- Terminating `clean test` gate; focused core retrieval, API property, and
  OpenTelemetry tests.
- Deploy to production; capture a before/after window in Prometheus:
  turn-latency histogram (expect the 120 s bucket population to disappear),
  TTFT mean, and the new assistant-layer stages accounting for the ~2.4 s
  gap. This measurement also closes the pending live-proof gate of
  `2026-07-28-lightrag-query-latency`.
- If the bimodal timeout signature survives the pool/admission change, record
  that the pool hypothesis is falsified in this plan and open tracing of the
  surviving path before any further latency work.
- Consolidate: spec/test matrix refresh for the retrieval domain, roadmap
  update, move to completed.

## Deferred review finding (PR #292, CodeRabbit)

The turn timeout does not interrupt a turn blocked in admission or in an
in-flight snapshot query; an abandoned turn consumes its permit and one
storage query after the timeout fires (bounded zombie work; permits always
release). Pre-existing in part — the timeout never interrupted the
synchronous search path. Deferred to the Phase 2 compound-query port, whose
design must include deadline-aware admission and cooperative cancellation
between the turn stream and the retrieval future.

## Production before/after (measured 2026-08-05)

Before = 7-day window ending 2026-08-04 (45 turns, organic demo traffic).
After = 75-minute window on 2026-08-05 with driven synthetic traffic
(~47 turns, including 16 turns in four bursts of 4-concurrent — the exact
concurrency shape that previously exhausted the 12-connection pool).

| Metric | Before | After |
| --- | --- | --- |
| Turns at/over the 120 s timeout | 8/45 (~18%) | **0/47** |
| Max turn | ≥120 000 ms (timeout ceiling) | 15 396 ms |
| TTFT mean | 7 677 ms | 4 714 ms |
| TTFT p50 | 7 445 ms | 4 221 ms |
| retrieve stage mean (inclusive) | 4 694 ms | 2 017 ms |
| retrieve_snapshot mean | 299 ms (no queue wait) | 166 ms (includes admission wait) |
| Dropped contributions per truncation | ~433 | ~29 |

New attribution stages (after only): retrieval_to_first_token 2 695 ms,
conversation_history_load 0.5 ms, grounding_to_prompt 0.05 ms. The formerly
unattributed ~2.4 s of TTFT is now measured and is provider first-token
latency after prompt submission, not application code.

Honest caveats: the after-window traffic repeated 10 question templates, so
keyword-cache hits are higher than organic (prepare_query mean fell to
969 ms partly for that reason); TTFT gains are therefore partly cache
warming. The primary criterion is unaffected by that bias: **zero timeouts
under 4-concurrent load** — the bimodal pool-exhaustion signature did not
reproduce under the exact condition that previously produced it. The pool
hypothesis stands confirmed for this window. This measurement also closes
the pending live-proof gate of `2026-07-28-lightrag-query-latency`.

Production config note: pool 8/8 was applied via `.env.production` on the
host (backup `.env.production.bak.pool-20260805`) because a compose-only
change does not trigger a deployment ("no image set"); repo compose
defaults are aligned since PR #294, so subsequent image deploys preserve
8/8.
