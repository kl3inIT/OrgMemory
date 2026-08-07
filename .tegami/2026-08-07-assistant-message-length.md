---
packages:
  orgmemory: patch
subject: Enforce the supported Assistant question length
---

## Fixes

The Assistant composer now displays and enforces the 1,000-character question
limit before a turn starts. Questions at the boundary remain accepted, while
longer input is blocked instead of opening a stream that later fails.
