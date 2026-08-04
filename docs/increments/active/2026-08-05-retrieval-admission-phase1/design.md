# Retrieval admission control and pool right-sizing (ADR 0020 Phase 1)

Date: 2026-08-05

## Outcome

Ship Phase 1 of [ADR 0020](../../../decisions/0020-authorized-multi-snapshot-query-plane.md):
remove the connection-pool exhaustion failure mode behind the bimodal
turn-timeout signature, replace batch-barrier snapshot scheduling with fair
continuous admission, revert `topK` to the upstream LightRAG default, and make
the currently unattributed ~2.4 s of assistant TTFT traceable — all without
touching the per-space query plane, authorization sequence, or cache identity.

The compound `AuthorizedMultiSnapshotQuery` target, its shadow prototype, and
the keyword-bypass recall evaluation are **out of scope**; they are Phase 2
work gated by ADR 0020 conditions 2–3 and require their own increment.

## Production evidence (Prometheus, 7-day window ending 2026-08-04)

- 45 assistant turns; mean TTFT 7.4 s; 8/45 turns (~18%) die at the 120 s
  `turnTimeout` with nothing observed between 15.7 s and 120 s.
- Per-request snapshot fan-out holds up to `maximumConcurrentSpaces = 4`
  connections; the API pool is 12 (`application-prod.yml`); three concurrent
  turns can exhaust the pool. The bimodal fast-or-timeout distribution is the
  expected signature of admission starvation, not slow retrieval.
- Stage means: retrieve 4.7 s (inclusive timer — wraps authorize, prepare,
  embed, snapshots), prepare_query 2.1 s at ~4% keyword-cache hit rate,
  embed 1.2 s, retrieve_snapshot 299 ms × ~7 calls/turn.
- Pipeline accounts for ≈ 5.0 s of the 7.4 s TTFT; ~2.4 s sits above
  `GraphRagKnowledgeRetrievalService` and is unattributed by current
  telemetry.
- Host: 4 vCPU EPYC slice, SSD, PostgreSQL container shared with other
  stacks. HikariCP About-Pool-Sizing formula: `(4 × 2) + 0 ≈ 8`.

## Decisions

All four were settled by the two-model architecture debate consolidated in
ADR 0020; this design binds them to code.

### Fixed pool 8/8

`maximum-pool-size` 12 → 8 and `minimum-idle` 2 → 8 for the API service.
The HikariCP guidance is a small saturated fixed pool; the shared-host caveat
argues against rounding up. The worker service pool is unchanged — its
workload (extraction) is not part of this failure mode.

### Host-wide fair retrieval semaphore, 4 permits, acquired before checkout

One JVM-wide fair `Semaphore(4)` at the snapshot-execution boundary in
`GraphRagKnowledgeRetrievalService`. A permit is acquired **before** any JDBC
connection is requested and released after the snapshot result is
materialized. This caps retrieval's total pool draw at 4 of 8 connections,
leaving 4 for scope resolution, BatchCheck support queries, conversation
writes, and unrelated API traffic. Replicas would divide, never multiply,
this budget; the current deployment is a single API replica, and the permit
count is a property so a future replica count change is a config edit plus
this documented rule.

Rejected variant (debate A-R1): pool 10 with a 6-permit semaphore — rejected
because it contradicts the sizing formula on a shared host and lets one
seven-space turn monopolize permits ahead of a second turn's first query.

### Barrier to continuous admission

The current loop executes spaces in batches of `maximumConcurrentSpaces`
with a `future.get()` barrier per batch; a straggler in batch one delays
batch two, and the second batch of a seven-space turn runs three-wide under a
four-slot budget. Replace with submit-all + global-semaphore admission:
every space task is submitted to the virtual-thread executor immediately and
blocks on the fair semaphore, preserving result ordering at consolidation.
`maximumConcurrentSpaces` stops governing scheduling; it is superseded by the
semaphore permits (validation retained so existing configuration does not
break).

The fail-closed behavior is unchanged: any snapshot failure cancels
outstanding work and fails the whole retrieval, exactly as the barrier loop
does today.

### topK 60 → 40 and TTFT attribution

`ORGMEMORY_GRAPH_QUERY_TOP_K` default reverts to the upstream LightRAG
v1.5.4 default of 40 (the 60 has no recorded rationale; the debate found
none). Graph expansion ceilings follow as `topK * 4` = 160.

Add stage timing above the retrieval service so the ~2.4 s gap becomes
attributable: emit assistant-layer durations for grounding-to-prompt
assembly, conversation-history loading, and the delay between retrieval
completion and the first model token. Payload-free attributes only, matching
the existing OpenTelemetry event-sink discipline.

## Safety argument

- No authorization boundary moves: the sequence documented in the
  `2026-07-28-lightrag-query-latency` design (organization check → scoped
  ListObjects → filtered GraphRAG → scope comparison → final BatchCheck →
  canonical recheck) is untouched.
- Admission control is above the storage port; per-space statements, cache
  keys, and snapshot pinning are byte-identical.
- The semaphore cannot deadlock the pool: each admitted task holds at most
  one connection (`C_m = 1`), so the HikariCP deadlock floor is 1 and the
  4-permit budget is far above it.
- Risk: a fair semaphore serializes admission order under contention; at 45
  turns/week the contention window is small, and fairness is exactly what
  removes the starvation mode.

## Relationship to open increments

`2026-07-28-lightrag-query-latency` is merged (PR #102) with only its live
before/after production proof pending. This increment builds on its
prepare-once and stage-telemetry work. The production verification step here
supersedes that increment's pending timing capture: one deployment, one
before/after measurement window serves both.
