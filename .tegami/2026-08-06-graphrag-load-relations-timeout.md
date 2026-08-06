---
packages:
  orgmemory: patch
subject: Bound authorized GraphRAG relation loading
---

## Fixes

GraphRAG now resolves authorized relation candidates with set-based entity and
relation visibility checks instead of repeating correlated ACL work for each
candidate. Relation reads also use the transaction-scoped PostgreSQL timeout so
a retrieval cancellation cannot leave database work running in the background.
