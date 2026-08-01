# Challenge Brief — Ingestion Throughput

Your job is to ATTACK the proposal in `design.md` (same directory), not
validate it. Verify every claim in the repository source yourself. You are
read-only: no edits, no mutations. Read `CLAUDE.md`, `docs/conventions.md`,
the knowledge-ingestion domain spec under `docs/specs/domains/`, and scan
`docs/decisions/` filenames before forming a verdict.

## Product context

OrgMemory ingests organizational sources into a governed knowledge layer.
Staging writes feed generation-scoped projections that are published
atomically; ingestion jobs are queued rows claimed under leases by worker
replicas. Correctness promises at stake: staged generations either publish
completely or not at all, job processing is exactly-once under lease
expiry/retry, and a worker shutdown must not strand or duplicate work.

## Decisions under review

1. Replace per-row `jdbc.update` loops in the graph-rag-postgres staging
   stores with `batchUpdate`, preserving SQL, values, order, and the
   callers' observed failure semantics.
2. Replace one-job-per-tick scheduler methods in apps/worker with
   drain-until-empty loops (fixed delay paces only idle polling), preserving
   lease/heartbeat behavior and shutdown responsiveness.

## Questions you must answer with repository evidence

- For each staging store: is per-statement failure attribution actually
  observed by any caller (logs, retry decisions, partial-progress markers),
  or does any failure already fail the whole staging operation? Cite
  file:line.
- Do any of the loops interleave reads with writes or depend on
  per-statement generated keys/update counts in ways `batchUpdate` changes?
- Transaction boundaries: are the loops inside one transaction per batch
  today? Does batching change lock hold time or deadlock exposure against
  the copy-forward/publication path?
- Schedulers: what exactly bounds a tick today (`@Scheduled` fixedDelay
  semantics), how are leases renewed during a long job, and what happens to
  an in-flight drain loop on context shutdown? Is there a starvation risk
  between the two schedulers or against maintenance tasks sharing the pool?
- Does the existing lease machinery make drain-until-empty safe on multiple
  replicas (two replicas draining the same queue)?
- Comparable system: how does Onyx (D:/OrgMemory/tmp/onyx) pace its
  background indexing workers — per-tick, drain, or continuous?

## Required output

Structured verdict in plain Markdown: VERDICT per decision
(approve / approve-with-must-fix / reject), must-fix list, strongest
counterargument, file:line evidence for every claim, scope limits.
