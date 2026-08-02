---
packages:
  orgmemory: patch
subject: Repair existing-version CLI publication verification
---

## Fixes

Make the retry-safe npm publication path parse correctly when the exact CLI
version already exists, and prevent indented nested heredoc terminators from
reaching the workflow again.
