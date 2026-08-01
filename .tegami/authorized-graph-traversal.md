---
packages:
  orgmemory: patch
subject: Unify authorized graph traversal
---

## Fixes

Authorized graph expansion now follows one deterministic core policy across
PostgreSQL, Neo4j, and OpenSearch. Exact snapshot validation, permission-scoped
paging, canonical ordering, and global limits no longer vary by storage
backend, and incomplete native traversal prefixes can no longer become public
query results.
