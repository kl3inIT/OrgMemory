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
contracts. This completes Graph closure under the existing
[Claude Fable 5 architecture verdict](../docs/increments/active/2026-07-31-spring-modulith-package-refactor/challenge-verdict.md).
