---
packages:
  orgmemory: patch
subject: Isolate retrieval and embedding contracts
---

# Knowledge retrieval contracts

## Improvements

Query embedding contracts, embedding profiles, and projection namespaces now
share an explicit retrieval module boundary, making provider integration and
future retrieval changes easier to verify safely.
