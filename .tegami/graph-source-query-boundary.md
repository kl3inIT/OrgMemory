---
packages:
  orgmemory: patch
subject: Remove Graph access to Source revision persistence
---

# Remove Graph access to Source revision persistence

## Improvements

Knowledge Graph indexing now resolves immutable source-revision state through
a Source Ledger-owned query instead of consuming revision repositories, JPA
entities, and persistence status directly. This follows the existing
[Claude Fable 5 architecture verdict](../docs/increments/active/2026-07-31-spring-modulith-package-refactor/challenge-verdict.md)
by removing another implementation edge before the Graph module is closed.
