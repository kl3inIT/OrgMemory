---
packages:
  orgmemory: patch
subject: Repair published graph snapshots after Apache AGE cutover
---

## Fixes

Production deployment now reconstructs retained relational graph publications
in Apache AGE through a bounded, verified one-shot before the API and worker
start. Graph exploration and Assistant retrieval no longer remain unavailable
when published snapshots predate the AGE topology backend.
