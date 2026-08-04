---
packages:
  orgmemory: patch
subject: Route Space reads through an owned query boundary
---

# Route Space reads through an owned query boundary

## Improvements

Graph and Retrieval now resolve Knowledge Space existence and active status
through the Space-owned `KnowledgeSpaceQuery` API instead of consuming the
Space repository directly. This follows the independently reviewed architecture
direction to remove implementation edges before closing a nested module.
