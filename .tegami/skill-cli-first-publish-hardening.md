---
packages:
  orgmemory: patch
subject: Harden the first Skill CLI publication
---

## Fixes

Verify the packed OrgMemory CLI executable before publication and bind npm
provenance to the exact repository, so the first public package cannot succeed
with a missing `orgmemory` command or an unrelated source identity.
