---
packages:
  orgmemory: patch
subject: Keep public docs hydration stable
---

# Keep public docs hydration stable

## Fixes

The public documentation now renders its category selector consistently during
static generation and browser hydration. English and Vietnamese documentation
routes no longer emit React hydration errors when opened from a fresh page.
