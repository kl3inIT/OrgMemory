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
