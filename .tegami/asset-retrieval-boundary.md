---
packages:
  orgmemory: patch
subject: Remove the Knowledge Asset dependency on Retrieval
---

# Remove the Knowledge Asset dependency on Retrieval

## Improvements

Knowledge Asset now owns its compact embedding-profile reference and projection
namespace identity. Connector and Worker translate richer Retrieval profiles at
the orchestration boundary, leaving Asset with no direct Retrieval dependency.
