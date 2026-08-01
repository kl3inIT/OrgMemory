# Challenge Verdict — Ingestion Throughput

- Date: 2026-08-01
- Commit reviewed: `488a122c` (branch `increment/ingestion-throughput`)
- Reviewer: independent adversarial session on a different model family
  (Codex gpt-5.6-sol, reasoning high), read-only, driven by
  [challenge-brief.md](challenge-brief.md). Full verdict:
  [challenge-verdict-codex.md](challenge-verdict-codex.md).

## Committed recommendation

1. **JDBC batching: proceed, reshaped (approve-with-must-fix).**
   Content/lexical/vector staging loops already have whole-stage failure
   semantics and ignore per-row counts — safe to batch. The graph store is
   NOT one homogeneous loop: it interleaves two SQL shapes per record, so
   the batching must be dependency-phased (delete revision → entity rows →
   entity contributions → relation rows → relation contributions → orphan
   cleanup) and proven by a PostgreSQL integration test. The cache store is
   excluded except its ordered evidence inserts: the `INSERT ... RETURNING
   id` key dependency, delete-before-insert order, and ordinals are
   contract-bearing. All batches are bounded in size; batch failures map to
   the same whole-stage failure the lifecycle persists today.
2. **Drain-until-empty: rejected.** The worker runs every `@Scheduled`
   callback on Spring's single default scheduling thread; an unbounded drain
   starves the other queue and all maintenance jobs, and leases protect
   claims, not fairness or shutdown. Replacement: **bounded burst** — the
   processor contract changes to report `PROCESSED` vs `EMPTY/DEFERRED`, and
   each tick claims up to a configured `maxJobsPerInvocation` (separate
   budgets per queue) with a wall-clock budget, stopping early on
   empty/deferred, interrupt, or application stop, then returning the
   thread. Continuous dedicated consumers are the long-term alternative but
   out of scope (new scheduling infrastructure).

## Strongest counterargument (recorded)

"Batching stays inside existing transactions and SKIP LOCKED makes claims
replica-safe, so both changes are mechanical." Accepted for bounded,
dependency-aware batching; rejected for scheduling — claim exclusivity in
the database says nothing about executor fairness on one shared thread.

## Rejected alternatives

- Literal per-statement-order-preserving batchUpdate for the graph store
  (not implementable — batchUpdate batches one SQL shape).
- Drain-until-empty inside the shared `@Scheduled` callback (starvation).
- Dedicated continuous consumers per queue (safe but new infrastructure —
  deferred, recorded for a future increment).

## Scope limits

Static read-only review at `488a122c`; no load/deadlock experiments. LLM
extraction parallelism and broker/worker subsystems not assessed.
