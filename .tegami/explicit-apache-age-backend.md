---
packages:
  orgmemory: patch
subject: Select an explicit Apache AGE topology backend
---

## Features

PostgreSQL GraphRAG now selects either Apache AGE or relational topology as an
explicit runtime backend. Apache AGE is the production default, fails startup
instead of silently falling back, and serves publication-batch-pinned,
authorization-filtered topology while PostgreSQL retains canonical evidence.
