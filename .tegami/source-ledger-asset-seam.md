---
packages:
  orgmemory: patch
subject: Remove the Source Ledger-to-Asset dependency
---

# Remove the Source Ledger-to-Asset dependency

## Improvements

Source Ledger now validates source provenance before calling its own asset
promotion port, while Asset owns promotion persistence and retirement. This
removes the reverse module edge without changing ingestion idempotency,
security lineage, or publication behavior.
