---
packages:
  orgmemory: patch
subject: Close the Knowledge Graph module boundary
---

# Close the Knowledge Graph module boundary

## Improvements

Knowledge Graph now enforces a closed public API and an exact outgoing
dependency allowlist after Asset, Source Ledger, ACL, Space, and embedding
profile persistence access was replaced with owned query and registry
contracts. This completes the independently reviewed Graph closure.
