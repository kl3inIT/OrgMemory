---
subject: Preserve rollback evidence after bootstrap cleanup
packages:
  orgmemory: patch
---

## Operations

Production deployment now tolerates cleanup of the completed PostgreSQL
bootstrap container when the previous release is already pinned by exact
digest and that same image remains available locally for no-pull rollback.
Mutable, missing, or mismatched rollback images remain blocked before rollout.
