---
packages:
  orgmemory: patch
subject: Remove Graph access to embedding profile persistence
---

# Remove Graph access to embedding profile persistence

## Improvements

Knowledge Graph indexing now resolves immutable embedding profiles through the
Retrieval-owned registry instead of consuming the profile repository and JPA
entity. This follows the existing
[Claude Fable 5 architecture verdict](../docs/increments/active/2026-07-31-spring-modulith-package-refactor/challenge-verdict.md)
by removing the final Graph persistence edge before its module-closing cycle.
