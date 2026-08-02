---
packages:
  orgmemory: patch
subject: Correct the missing-version npm publication probe
---

## Fixes

Allow a new exact CLI version to reach npm Trusted Publishing while retaining
the immutable-integrity comparison for versions that already exist.
