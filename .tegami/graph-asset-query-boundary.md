---
packages:
  orgmemory: patch
subject: Remove Graph access to Asset persistence
---

# Remove Graph access to Asset persistence

## Improvements

Knowledge Graph indexing and curation now consume immutable Asset-owned query
contracts instead of Asset repositories, JPA entities, and the chunk projection
store. This follows the existing
[Claude Fable 5 architecture verdict](../docs/increments/active/2026-07-31-spring-modulith-package-refactor/challenge-verdict.md)
by removing an implementation edge before the Graph module is closed.
