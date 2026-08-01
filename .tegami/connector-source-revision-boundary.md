---
packages:
  orgmemory: patch
subject: Isolate connector source revision transactions
---

# Isolate connector source revision transactions

## Improvements

Source Ledger now owns connector revision lookup, evidence staging, completion,
and atomic graph-job scheduling behind revision commands and immutable draft
facts. Connector no longer consumes Source Ledger repositories/entities or the
Graph queue, while independent transaction boundaries remain enforced.
