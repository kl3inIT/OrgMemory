---
packages:
  orgmemory: patch
subject: Use the LightRAG mini model for graph extraction
---

## Fixes

Default graph extraction independently to `gpt-5.4-mini` so changing the
Assistant model no longer changes indexing behavior, while preserving a
dedicated deployment override and immutable processing profiles.
