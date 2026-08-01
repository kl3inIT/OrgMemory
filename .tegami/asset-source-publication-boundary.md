---
packages:
  orgmemory: patch
subject: Isolate Asset publication from Source Ledger persistence
---

# Isolate Asset publication from Source Ledger persistence

## Improvements

Knowledge Asset promotion now receives validated source facts through public
Source Ledger contracts, while publication advances the source revision through
an owner-defined transaction-aware service instead of cross-module repository
access.
