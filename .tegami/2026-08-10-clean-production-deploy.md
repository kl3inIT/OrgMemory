---
subject: Deploy releases from a clean checkout
packages:
  orgmemory: patch
---

## Fixes

Production deployment now executes the exact released commit from an
ephemeral clean linked worktree. Staged or local operator changes on the host
can no longer replace deployment scripts or image references during rollout.
