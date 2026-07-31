---
packages:
  orgmemory: patch
subject: Break the ACL-to-Connector dependency
---

# Break the ACL-to-Connector dependency

## Improvements

ACL now owns its ingestion commands and membership evidence while Connector
maps crawl payloads at the boundary, removing the reciprocal module dependency
without changing connector or source-access behavior.
