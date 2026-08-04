---
packages:
  orgmemory: patch
subject: Remove Graph access to Source revision persistence
---

# Remove Graph access to Source revision persistence

## Improvements

Knowledge Graph indexing now resolves immutable source-revision state through
a Source Ledger-owned query instead of consuming revision repositories, JPA
entities, and persistence status directly. This follows the independently
reviewed architecture direction by removing another implementation edge before
the Graph module is closed.
