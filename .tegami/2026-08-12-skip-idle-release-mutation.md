---
packages:
  orgmemory: patch
subject: Skip release mutation for an idle phase
---

## Operations

A completed release with no newer entry now stops after phase detection. The
release workflow no longer invokes versioning or publication for that idle
state, while pending recovery and new version work retain their exact existing
gates.
