# Multi-snapshot query prototype and retrieval evaluation harness (ADR 0020 Phase 2 gates)

Date: 2026-08-05

## Outcome

Produce the evidence that ADR 0020 conditions 2 and 3 demand before the
`AuthorizedMultiSnapshotQuery` plane may replace per-space retrieval: a
shadow prototype of the compound authorized query with load-shaped latency
measurements, and a retrieval evaluation set that scores the keyword-bypass
miss path. This increment delivers **evidence, not cutover** — the live
retrieval path is not modified. If either gate fails, the increment's output
is a recorded falsification and Phase 1 remains the standing architecture,
per the ADR's rejected-alternative clause.

## What is being proven or falsified

1. **Latency gate (ADR 0020 condition 2).** One set-based SQL statement over
   a sorted vector of per-space `(spaceId, batch, generation, ACL
   generation, authorized asset ids)` tuples — seed ranking, depth-1 graph
   expansion, contribution aggregation, global LIMIT — meets
   **p95 ≤ 500 ms** at current, 10×, and 100× projection sizes, under
   concurrency 1 and 4, for 1/7/20 spaces and narrow/broad grants, with
   `EXPLAIN (ANALYZE, BUFFERS)` captured. The strongest surviving attack
   from the ADR debate is precisely that a mis-planned multi-tuple join
   ahead of vector distance ordering can lose to seven indexed point
   queries; this harness is that attack given teeth.
2. **Recall gate (ADR 0020 condition 3).** On a fixed evaluation set, the
   cache-miss bypass (raw-query embedding seeds plus deterministic lexical
   terms, no keyword LLM call) does not regress **recall@40** against the
   current keyword-seeded retrieval. Production measured the keyword cache
   at ~4% hits, so this bypass carries B's entire TTFT claim; it is a
   semantic change and must be scored, not assumed.

## Design decisions

### Prototype lives in the storage adapter's test scope, not the runtime

The compound statement and its benchmark harness are built under
`integrations/graph-rag-postgres` as a Testcontainers-backed benchmark
(same pattern as the existing PostgreSQL integration tests), plus an
optional ops-profile runner for the production-shaped shadow run on zm.
Nothing registers into Spring runtime wiring; no live query path changes.
Rationale: the debate's verdict forbids production exposure before gates
pass, and adapter test scope already has schema, fixtures, and pgvector.

### Synthetic scale-up is generated, deterministic, and disposable

10× and 100× datasets are generated from the current projection shape
(entity/relation/chunk distributions, embedding dimensionality 1536) with a
fixed seed, loaded into the benchmark database only. Authorization skew is
generated two ways: narrow (one space, few assets) and broad (20 spaces,
most assets) to expose planner sensitivity to predicate selectivity.

### Shadow-compare defines correctness, the runbook defines measurement

Every benchmark scenario runs both paths — N per-space queries (current
semantics) and the compound statement — and asserts result-set equivalence
(same candidates, same per-space attribution) before timing counts.
Measurement follows `docs/runbooks/graph-rag-production-hardening.md`:
cold/warm phases, ≥5 repetitions, p50/p95/p99, buffers, pool waits.

### Evaluation set is answerable-question golden data over the real corpus

~50 questions authored against the actual 52-source corpus (each with the
source chunks that should ground the answer), stored under
`docs/tests/domains` conventions as data, scored recall@40 for: current
keyword-seeded path, bypass path, and (diagnostic only) topK 60 vs 40.
This also retroactively validates Phase 1's topK revert.

### Deferred cancellation finding becomes a design input, not a patch

PR #292 review recorded that the turn timeout interrupts neither admission
wait nor in-flight snapshot queries. The compound-query port design in this
increment must specify deadline-aware admission (bounded acquire) and
cooperative cancellation between the turn stream and the retrieval future,
so the eventual Phase 3 cutover increment implements it natively rather
than retrofitting.

## Out of scope

Any change to the live retrieval path, cache identity, authorization
sequence, or deployment; the cutover itself (Phase 3, its own increment,
conditional on this one's evidence).

## Relationship to open increments

`2026-08-05-retrieval-admission-phase1` remains active until its production
before/after measurement is consolidated. This increment consumes Phase 1's
production stage telemetry (admission wait visibility) as benchmark inputs
where useful but does not block on it.
