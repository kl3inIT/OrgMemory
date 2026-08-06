---
packages:
  orgmemory: patch
subject: Keep GraphRAG degree ranking within its retrieval budget
---

## Fixes

GraphRAG degree ranking now resolves authorized relation visibility once and
uses indexed source and target endpoint lookups. PostgreSQL also cancels an
abnormally slow degree query before the assistant retrieval deadline, avoiding
orphaned database work that could degrade later chat turns.
