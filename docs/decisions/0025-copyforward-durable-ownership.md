# 0025 — Copy-forward has one coordinator with durable ownership; _reindex is rejected

Status: accepted (2026-08-01)

## Decision

OpenSearch generation copy-forward runs through one
`OpenSearchCopyForwardCoordinator` with a durable
`COPYING`/`READY`/`FAILED` marker state machine: a live `COPYING` marker is
never taken over; takeover requires an explicit durable `FAILED` written by
the failing owner's cleanup, which also removes the failing projection's
partial output. Local lock retirement uses remove-before-unlock with
current-map-entry validation; marker/lock keys use exact canonical target
identity; copy units stay per-projection (graph entity and relation remain
distinct). The copy step streams page→bulk under count and byte bounds.
Server-side `_reindex` is rejected for the current index layout.

## Why

The protocol existed as three per-adapter copies. An independent
cross-model architecture challenge showed none was complete: the durable
marker had no failure state, and any non-READY marker — including a LIVE
copy in another process — was CAS-replaced by a new owner, a real
cross-process corruption hazard. The challenge also showed no-script
`_reindex` cannot implement the contract: copy-forward rewrites
`batch_id`/`generation`/sometimes `_id`, and vector/content copy within one
physical index while `_reindex` requires distinct source and destination.
Full client-side materialization of the previous generation (including
embeddings) was replaced by bounded streaming.

## Rejected alternatives

- Standardizing on any one existing adapter protocol verbatim (all
  incomplete).
- No-script server-side `_reindex` (cannot rewrite coordinates or copy
  in-place; may be revisited only after an index-layout redesign with
  secured-cluster permission and task-semantics tests).
- Status quo (three drifting copies of concurrency-critical code).

Challenge record:
`docs/increments/completed/2026-08-01-copyforward-coordinator/`.
