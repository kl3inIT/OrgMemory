---
packages:
  orgmemory: patch
subject: Isolate connector source lifecycle operations
---

# Isolate connector source lifecycle operations

## Improvements

Connector reconciliation now resolves source identity, diffs active inventory,
and retires tombstoned sources through Source Ledger-owned query and lifecycle
APIs. Connector no longer consumes the Source Object repository, entity, or
status enum for these flows.
