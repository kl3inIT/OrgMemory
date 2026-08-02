---
packages:
  orgmemory: patch
subject: Recover graph publication safely across worker restarts
---

# Recover graph publication safely across worker restarts

## Fixes

Graph indexing now binds each cross-store publication to a durable commit
permit and claim epoch. Retries resume the exact permitted PostgreSQL and
OpenSearch attempt after a worker restart, never discard staging whose
visibility is uncertain, fence abandoned copy-forward work, invalidate graph
caches before completing the job, and require durable proof before cleaning up
a competing attempt.
