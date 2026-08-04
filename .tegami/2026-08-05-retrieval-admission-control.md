---
packages:
  orgmemory: patch
subject: Faster, fairer assistant retrieval under load
---

## Improvements

Assistant knowledge retrieval now admits snapshot queries through one fair
process-wide limit instead of per-request batches, so concurrent
conversations can no longer exhaust the database connection pool and stall at
the turn timeout. The API connection pool is right-sized for the production
host, retrieval breadth returns to the upstream LightRAG default, and new
payload-free timing stages make the previously unattributed portion of
time-to-first-token observable.
