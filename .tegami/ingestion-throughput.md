---
packages:
  orgmemory: patch
subject: Faster document ingestion and graph indexing
---

# Faster document ingestion and graph indexing

## Improvements

Staged projection writes now travel in bounded batches instead of one
statement per row, and the ingestion and graph-indexing workers process a
bounded burst of queued jobs per cycle instead of a single job, so backlogs
drain far sooner while maintenance jobs and the other queue keep running.
Failure behavior, publication atomicity, and stored data are unchanged.
