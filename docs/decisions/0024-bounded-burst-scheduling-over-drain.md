# 0024 — Worker queues process bounded bursts, never unbounded drains

Status: accepted (2026-08-01)

## Decision

The source-ingestion and graph-indexing schedulers claim and process a
bounded burst of jobs per `@Scheduled` tick — a per-queue job cap inside a
wall-clock budget, stopping early on an empty queue, interrupt, or
application shutdown — and then return the scheduling thread. Projection
staging writes execute as bounded, dependency-phased JDBC batches whose
failure still fails the whole staging operation.

## Why

One job per tick made the fixed delay a throughput ceiling (a backlog of
100 documents needed over 200 seconds per replica regardless of capacity).
The obvious fix, drain-until-empty, was rejected by an independent
cross-model architecture challenge: the worker runs every scheduled
callback on Spring's single default scheduling thread, so an unbounded
drain of one queue starves the other queue and every maintenance job.
Database lease exclusivity (`FOR UPDATE SKIP LOCKED`) protects claims
across replicas; it provides no executor fairness. Bounded bursts raise
throughput by the burst factor while preserving fairness, shutdown
responsiveness, and the existing lease/heartbeat machinery.

## Rejected alternatives

- Drain-until-empty inside the shared scheduled callback (starvation of
  sibling queues and maintenance work).
- Dedicated continuous consumers per queue (the architecture comparable
  systems use, but new scheduling infrastructure — a future increment if
  burst capacity proves insufficient).
- Literal order-preserving single-batch graph writes (not implementable —
  `batchUpdate` batches one SQL shape; dependency phases replace it).

Challenge record:
`docs/increments/completed/2026-08-01-ingestion-throughput/`.
