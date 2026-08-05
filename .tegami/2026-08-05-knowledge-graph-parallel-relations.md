---
packages:
  orgmemory: patch
subject: Keep parallel knowledge graph relations visible
---

## Fixes

The Knowledge graph no longer fails to load when two distinct semantic
relations connect the same directed pair of entities. Parallel relations remain
separate and visible in the graph and entity inspector.
