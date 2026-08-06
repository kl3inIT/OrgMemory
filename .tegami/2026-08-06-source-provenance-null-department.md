---
packages:
  orgmemory: patch
subject: Keep the document list working for organization-wide sources
---

## Fixes

The Documents list no longer fails when a source belongs to an
organization-wide Knowledge Space. Those sources carry no owning department,
and the provenance lookup rejected the missing identifier.
