---
packages:
  orgmemory: patch
subject: Keep completed releases idle on later commits
---

## Operations

After a product release is fully published, a later green-main change with no
release entry now remains an explicit release no-op instead of retrying the old
release against newer repository content. Pending releases still resume and
continue to block newer version work until recovery.
