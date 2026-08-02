---
packages:
  orgmemory: patch
subject: Restore production AI gateway configuration binding
---

## Fixes

Production API and worker processes now retain the complete configured AI
gateway when profile-specific credentials are applied, preventing startup
failure after adding gateway capability flags.
