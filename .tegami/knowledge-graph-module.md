---
packages:
  orgmemory: patch
subject: Isolate Knowledge Graph lifecycle and processing boundaries
---

## Improvements

Knowledge Graph indexing, processing profiles, lifecycle operations, curation,
exploration, and export now share an explicit module boundary, reducing coupling
and making future graph changes safer to verify and release.
