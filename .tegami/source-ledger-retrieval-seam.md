---
packages:
  orgmemory: patch
subject: Remove the Source Ledger-to-Retrieval dependency
---

# Remove the Source Ledger-to-Retrieval dependency

## Improvements

Source Ledger now depends on its own visibility and embedding-profile
contracts while Retrieval implements the governed adapters, removing the
reverse module edge without weakening authorization or ingestion behavior.
