---
packages:
  orgmemory: patch
subject: Route Space reads through an owned query boundary
---

# Route Space reads through an owned query boundary

## Improvements

Graph and Retrieval now resolve Knowledge Space existence and active status
through the Space-owned `KnowledgeSpaceQuery` API instead of consuming the
Space repository directly. This follows the existing
[Claude Fable 5 architecture verdict](../docs/increments/active/2026-07-31-spring-modulith-package-refactor/challenge-verdict.md)
to remove implementation edges before closing a nested module.
