---
packages:
  orgmemory: patch
subject: Rebuild AI gateway consumers for production
---

## Fixes

Production releases now rebuild the API and worker whenever their shared AI
gateway integration changes, so approved Assistant and model-routing fixes are
included in the immutable image set instead of being treated as deployment
no-ops.
