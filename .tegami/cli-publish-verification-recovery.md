---
packages:
  orgmemory: patch
subject: Make CLI publication verification retry-safe
---

## Fixes

Recover a successful immutable CLI publication when npm provenance propagates
after the package manifest, while refusing any existing version whose registry
integrity differs from the reviewed tarball.
