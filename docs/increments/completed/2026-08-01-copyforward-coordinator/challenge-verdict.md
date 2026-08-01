# Challenge Verdict — Copy-Forward Coordinator

- Date: 2026-08-01
- Commit reviewed: `0cb8a187` (branch `increment/copyforward-coordinator`)
- Reviewer: independent adversarial session on a different model family
  (Codex gpt-5.6-sol, reasoning high), read-only, driven by
  [challenge-brief.md](challenge-brief.md). Full verdict:
  [challenge-verdict-codex.md](challenge-verdict-codex.md).

## Committed recommendation

1. **One coordinator: proceed, reframed (approve-with-must-fix).**
   "Choose one of the three protocols" was the wrong framing: NONE is
   complete. All three share a process-local lock map plus a durable
   COPYING/READY marker with no FAILED/lease/heartbeat state, and a
   cross-JVM second publisher CAS-steals a LIVE `COPYING` marker (any
   non-READY marker is replaced). Only the staged helper has the safe
   local retired-lock handoff (remove-before-unlock + current-map-entry
   validation). The coordinator therefore standardizes a protocol that
   exists nowhere yet: durable marker state machine (never steal live
   COPYING; explicit failure/abandonment path), staged-style local
   retirement, collision-resistant canonical target identity (not
   `hashCode()` base36), per-copy-unit keys preserved (no global lock; no
   collapsing graph entity/relation), cleanup of the failing projection
   itself, and crash/partial-page/split-process/retired-lock tests before
   the protocol counts as standardized.
2. **`_reindex`: rejected; streaming approved.** Copy-forward rewrites
   `batch_id`, `generation`, and sometimes `_id`, and vector/content copy
   within the SAME physical index while `_reindex` requires distinct
   source/destination — a no-script `_reindex` cannot implement the
   contract. Replace full client-side materialization with page→bulk
   streaming under count and byte bounds, asserting every non-coordinate
   `_source` field is preserved (vectors, nested maps/lists, numerics,
   IDs). `_reindex` may only be reconsidered after an index-layout
   redesign, with secured-cluster permission/task-semantics tests first.

## Strongest counterargument (recorded)

The three implementations differ for real structural reasons (staged backs
multiple projections per instance; vector spans multiple physical indexes),
so a shared coordinator risks flattening genuine per-index requirements.
Accepted response: per-copy-unit keys and adapter-supplied target identity
stay as typed parameters; only the lock/marker/retry mechanics unify.

## Rejected alternatives

- Standardizing on any one existing protocol verbatim (all incomplete;
  live-COPYING steal is a real cross-process hazard).
- No-script server-side `_reindex` (cannot rewrite coordinates or copy
  within one index).
- Status quo (three drifting copies of concurrency-critical code).

## Scope limits

Static read-only review at `0cb8a187`; no runtime/cluster experiments. Onyx
comparison is evidence, not authority over the immutable-generation
contract.
