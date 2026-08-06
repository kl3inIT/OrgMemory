---
packages:
  orgmemory: patch
subject: Restore fast authorized GraphRAG relation scoring
---

## Fixes

GraphRAG now limits relation contribution and relation-weight authorization work
to the requested candidate relations before checking independently visible source
and target entities. These reads also use the transaction-scoped PostgreSQL
budget, preventing an expensive plan from continuing after retrieval is
cancelled.

## Improvements

Snapshot retrieval now reports bounded, payload-free timings for each graph
storage operation under the existing retrieval operation identifier, making
future latency regressions attributable without recording prompts, answers, or
evidence identifiers.
