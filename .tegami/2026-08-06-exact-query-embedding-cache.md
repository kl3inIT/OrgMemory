---
packages:
  orgmemory: patch
subject: Reuse exact query embeddings across retrieval requests
---

## Improvements

GraphRAG and hybrid knowledge retrieval now reuse exact query embeddings within
an explicit projection namespace. Repeated requests avoid duplicate embedding
provider work while authorization, evidence selection, and citation verification
continue to run normally. Cached vectors remain isolated by embedding profile,
provider version, and dimensions, with bounded PostgreSQL retention and expiry.
