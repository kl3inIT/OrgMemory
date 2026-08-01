# Plan — Ingestion Throughput

Design: [design.md](design.md). Verdict: [challenge-verdict.md](challenge-verdict.md).
Working branch: `increment/ingestion-throughput` in
`D:/OrgMemory-worktrees/full-codebase-review`.

## Step 1 — Staging batch conversion (graph-rag-postgres)

- Content, lexical, vector stores: convert per-row `jdbc.update` loops to
  bounded `batchUpdate` (configurable bound, default ≤ 500 rows per batch)
  with the existing parameter sources; SQL and values unchanged; any batch
  failure fails the whole staging operation exactly as today.
- Graph store: dependency-phased bounded batches in the existing order —
  delete revision → entity base rows → entity contributions → relation base
  rows → relation contributions → orphan cleanup.
- Cache store: batch ONLY the ordered evidence inserts; keep
  `INSERT ... RETURNING id`, delete-before-insert, and ordinal assignment
  untouched.
- Tests: extend the existing PostgreSQL adapter tests (Testcontainers) to
  cover multi-row staging through the batched paths, a mid-batch failure
  failing the whole stage, and graph-store phase ordering (contributions
  never precede their base rows). Read-back equality against pre-change
  behavior for a representative staged generation.

Gate: `.\gradlew.bat :integrations:graph-rag-postgres:test` green.

## Step 2 — Bounded-burst schedulers (apps/worker)

- Change `SourceIngestionProcessor.processNext()` and
  `GraphIndexingProcessor.processNext()` to return a result (`PROCESSED` |
  `EMPTY`/`DEFERRED`) without changing claim/lease/heartbeat behavior.
- Schedulers loop up to `maxJobsPerInvocation` (new config per queue,
  conservative defaults, e.g. source 10 / graph 5) within a wall-clock
  budget; stop early on empty/deferred, `Thread.currentThread()
  .isInterrupted()`, or context shutdown; then return.
- Tests: burst stops on empty; burst honors the cap; interrupt before next
  claim stops the loop leaving the in-flight job retryable; simultaneous
  source+graph backlog still lets both queues progress across ticks
  (fairness via bounded bursts); config binding test for the new
  properties.

Gate: `.\gradlew.bat :apps:worker:test` green.

## Step 3 — Full gates

Terminating clean `.\gradlew.bat --no-daemon test`. No frontend or contract
changes expected; if any config keys are added, document them in the worker
configuration reference if one exists.

## Step 4 — Consolidation (after merge)

- knowledge-ingestion domain spec: record bounded-burst scheduling and
  batched staging writes; refresh `Source:`/`Reconciled:`.
- Decision entry for the bounded-burst-over-drain choice with the
  starvation counterargument and the dedicated-consumer alternative.
- Move increment to completed; roadmap row to shipped.

## Out of scope

LLM extraction parallelism, dedicated continuous consumers, copy-forward
protocol, any persisted bytes/digests/cursors.
