---
packages:
  orgmemory: patch
subject: Isolate connector read views from Source Ledger persistence
---

# Isolate connector read views from Source Ledger persistence

## Improvements

Connector inventory and activity views now use a Source Ledger-owned read
boundary instead of consuming its repository, entity status, and aggregate
projection types directly. The immutable query result preserves active and
archived counts, latest activity time, and active external object ids.
