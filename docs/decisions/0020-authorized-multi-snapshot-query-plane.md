# 0020 — Authorized Multi-Snapshot Query Plane As Gated Retrieval Target

## Status

Accepted on 2026-08-04 via the required independent architecture challenge,
run as a two-model debate (`orgmemory-agent-debate`): Claude Fable 5 defended
the per-space status quo with mechanical fixes, GPT-5.6-sol ultra defended a
single authorized query plane, and a no-tools judge committed from the record
alone. Debate record and verdict: `tmp/retrieval-perf-debate-2026-08-04-*.md`
(untracked reference space; the substance is consolidated here).

## Context

Production assistant TTFT averages 7.4 s, of which the retrieval pipeline is
nearly all: keyword planning 2.1 s (cache hit rate ~4%), embedding 1.2 s, and
per-knowledge-space snapshot queries executed in barrier-synchronized batches
of `maximumConcurrentSpaces=4` against a 12-connection pool. ~18% of turns
die at the 120 s turn timeout with a bimodal latency distribution consistent
with pool exhaustion (4 connections × 3 concurrent turns = the whole pool).
Per-space top-k results are merged without score normalization, over-retrieve
by construction (~433 contributions dropped per turn), and multi-space rerank
fails closed.

Facts established during the challenge that changed the analysis:

- Isolation is already predicate-based, not physically structural: vector
  rows share one table and every query applies organization, authorized-asset,
  and pinned-batch predicates before distance ordering
  (`PostgresVectorIndex`).
- The `RETRIEVE` telemetry stage is inclusive (it wraps authorize, prepare,
  embed, and snapshot execution), and ~2.4 s of TTFT is above the retrieval
  service, unattributed by current telemetry.
- Embedding consumes the keyword plan (`LightRagQueryEngine.prepare`), so
  keyword planning cannot simply be parallelized away.
- No atomic cross-space snapshot instant exists today; each space is pinned
  sequentially.
- ADR 0013 already requires an explicit multi-snapshot contract before
  cross-namespace retrieval is exposed.

## Decision

The target retrieval architecture is one typed `AuthorizedMultiSnapshotQuery`
store operation: the application issues one JDBC call whose immutable input
carries the organization, authorization model, a sorted vector of per-space
`(spaceId, batch, generation, manifest, ACL generation, authorized asset ids)`
tuples, and a canonical scope fingerprint; the store returns one globally
scored candidate band; adapter and core independently validate per-row
space/batch/asset membership and reject the entire result on any mismatch.
Per-space namespaces remain the publication and rollback unit for writes.
Existing scope re-resolution, OpenFGA BatchCheck, and canonical
evidence-closure recheck before egress are unchanged.

Cutover is gated, and the gates are binding:

1. **Phase 1 ships first, independent of the target**: Hikari fixed pool
   8/8 (down from 12/2, per the HikariCP sizing formula on the 4-vCPU shared
   host); one fair host-wide retrieval semaphore of 4 permits acquired before
   connection checkout (replicas divide the permits); batch barriers replaced
   by continuous admission; `topK` reverted 60 → 40 (upstream LightRAG
   default); retrieval-stage tracing added, including the unattributed ~2.4 s
   above the retrieval service.
2. **Latency gate**: a shadow prototype of the compound query on identical
   hardware, embeddings, corpus, authorization scopes, and query set, per the
   production-hardening runbook (1/7/20 spaces; narrow and broad grants;
   current/10×/100× projection sizes; concurrency 1 and 4 under
   shared-Postgres load; ≥5 repetitions; `EXPLAIN (ANALYZE, BUFFERS)`).
   Predeclared thresholds: compound-query p95 ≤ 500 ms; cold-keyword-miss
   median TTFT ≈ 2 s. Failure means no cutover.
3. **Recall gate**: an evaluation set must show that the cache-miss keyword
   bypass (raw-query embedding seeds plus deterministic lexical terms) does
   not regress recall@40 before it becomes the default miss path.
4. **Isolation parity gate**: negative isolation, revocation-during-query,
   stale-snapshot, and poisoned-cache tests pass; no unscoped read overload
   exists anywhere in the new port.
5. **Cache discipline**: the cold path is authoritative; the composite
   retrieval-result cache (sorted snapshot vector + authorization fingerprint
   + query semantics + model route) is optional and is disabled rather than
   weakened if its hit rate proves useless.

Until every gate passes, the per-space structural path remains production and
is the rollback.

## Rejected Alternative

Keeping the per-space query plane as the terminal architecture (semaphore
admission, post-merge rerank, pool and topK tuning only). Its strongest
argument — the compound query's performance is unproven, and a mis-planned
multi-tuple join ahead of vector distance ordering can be slower than seven
indexed point queries — is preserved as gates 2–3 rather than as grounds to
retain a design whose cross-space ranking is unsound by construction
(incomparable per-space scores, per-space truncation before merge) and whose
~2 s TTFT target is unreachable while the keyword LLM call sits on a ~96%
miss path.

## Consequences

- Phase 1 changes production configuration immediately and is expected to
  eliminate the pool-exhaustion timeout signature; if the bimodal timeouts
  survive Phase 1, the pool hypothesis is falsified and tracing decides next.
- The compound storage port, multi-snapshot cache contract, shadow-compare
  harness, and retrieval eval set are new work items and belong to a future
  increment; this decision does not authorize skipping its design/plan cycle.
- The debate corrected earlier telemetry interpretation; stage dashboards
  should distinguish inclusive from exclusive stage timers before further
  latency conclusions are drawn.
