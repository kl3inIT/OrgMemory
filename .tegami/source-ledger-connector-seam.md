---
packages:
  orgmemory: patch
subject: Remove the Source Ledger-to-Connector dependency
---

# Remove the Source Ledger-to-Connector dependency

## Improvements

The current source head projection now belongs to Source Ledger, allowing
Connector reconciliation to consume it without creating a reverse module
dependency.
