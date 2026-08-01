---
packages:
  orgmemory: patch
subject: Close the Knowledge Connector module boundary
---

# Close the Knowledge Connector module boundary

## Improvements

Knowledge Connector now enforces a closed Spring Modulith boundary and an exact
outgoing dependency allowlist after its ACL, Source Ledger, storage, Asset, and
Retrieval interactions were reduced to intentional public contracts.
