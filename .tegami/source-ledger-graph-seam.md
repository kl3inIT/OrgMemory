---
packages:
  orgmemory: patch
subject: Remove the Source Ledger-to-Graph dependency
---

# Remove the Source Ledger-to-Graph dependency

## Improvements

Source publication now schedules graph indexing through a Source-Ledger-owned
port, while Graph keeps target validation, profile selection, idempotency, and
durable queue persistence behind its adapter.
