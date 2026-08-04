---
packages:
  orgmemory: patch
subject: Remove Graph access to Asset persistence
---

# Remove Graph access to Asset persistence

## Improvements

Knowledge Graph indexing and curation now consume immutable Asset-owned query
contracts instead of Asset repositories, JPA entities, and the chunk projection
store. This follows the independently reviewed architecture direction by
removing an implementation edge before the Graph module is closed.
