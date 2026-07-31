---
packages:
  orgmemory: patch
subject: Remove the Source Ledger-to-Space dependency
---

# Remove the Source Ledger-to-Space dependency

## Improvements

Source Ledger now uses its own compact Space target port for upload and
promotion validation, while Space retains authorization and active-directory
policy behind the adapter.
