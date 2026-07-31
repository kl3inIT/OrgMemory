---
packages:
  orgmemory: patch
subject: Faster, leaner storage and connector adapters
---

## Improvements

Graph storage adapters now avoid repeated remote round trips on hot paths:
vector staging verifies each physical index once per batch, entity degrees are
computed in a single pass, and Slack crawls build their member directory once
per crawl instead of per thread. Duplicate and dead adapter code paths were
removed and model-key handling was hardened, with no change to stored data,
cursors, or fingerprints.
