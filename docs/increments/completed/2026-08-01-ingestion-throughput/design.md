# Ingestion Throughput

Source: full-codebase simplify review (2026-08-01), deferred efficiency items.
Evidence: `docs/increments/completed/2026-08-01-authz-consolidation/references/`
(phase 2 report item B7, phase 3 report items B-4/B-5; file:line references
predate later merges — verify against current source).

## Problem

Ingestion throughput is capped by two mechanisms that are not load-bearing
policy, just first-implementation shape:

1. **Per-row JDBC staging writes.** The graph-rag-postgres staging stores
   (graph store, vector index, lexical index, content store, cache store)
   execute one `jdbc.update` per record inside batch loops — hundreds to
   thousands of round trips per ingestion batch. The module contains zero
   `batchUpdate` uses.
2. **One job per scheduler tick.** The worker's source-ingestion and
   graph-indexing schedulers claim at most one job per fixed-delay tick
   (2 s / 3 s), so a backlog of 100 small documents needs ≥ 200 s of wall
   clock per replica regardless of capacity. The fixed delay is doing double
   duty as idle-poll pacing and as a throughput ceiling.

## Proposal (revised after architecture challenge)

The original shape (order-preserving batchUpdate everywhere;
drain-until-empty) was challenged and reshaped — see
[challenge-verdict.md](challenge-verdict.md).

1. **Bounded, dependency-phased JDBC batching.** Content, lexical, and
   vector staging loops convert directly to bounded `batchUpdate` (callers
   already observe whole-stage failure and ignore per-row counts). The
   graph store batches per dependency phase — delete revision, entity base
   rows, entity contributions, relation base rows, relation contributions,
   orphan cleanup — proven by a PostgreSQL integration test. The cache
   store keeps its `INSERT ... RETURNING id`, delete-before-insert order,
   and ordinals; only its ordered evidence inserts batch. Batch failures
   map to the same whole-stage failure the projection lifecycle persists
   today; transaction boundaries unchanged.
2. **Bounded-burst scheduling (drain-until-empty rejected).** The processor
   contract changes from `void processNext()` to a result reporting
   `PROCESSED` vs `EMPTY/DEFERRED`. Each `@Scheduled` tick claims and
   processes up to a configured `maxJobsPerInvocation` with a wall-clock
   budget (separate budgets for the source and graph queues), stopping
   early on empty/deferred, interrupt, or application stop, then returning
   the shared scheduling thread so other queues and maintenance jobs run.
   Lease/heartbeat machinery unchanged.

## Non-goals

- No change to persisted bytes, digests, fingerprints, cursors, or
  copy-forward/publication protocols (off-limits list from the review).
- No parallelization of LLM extraction (phase 3 item B-5, semaphore-bounded
  submission) — separate increment; it changes ordering/cancellation
  semantics.
- No db-scheduler or new scheduling infrastructure.

## Architecture challenge

Required by AGENTS.md (persistence write path and worker concurrency).

- Status: **completed 2026-08-01** — cross-model adversarial review at
  `488a122c`; JDBC batching approved with dependency-phasing and cache-store
  exclusions, drain-until-empty rejected for bounded burst on the shared
  scheduler thread. Record: [challenge-verdict.md](challenge-verdict.md),
  [challenge-verdict-codex.md](challenge-verdict-codex.md).
