---
packages:
  orgmemory: patch
subject: Bind independent production AI model routes
---

## Fixes

Production API and worker configuration now bind Keyword Planning independently
to `gpt-5.6-luna` with reasoning `none` instead of silently inheriting the
Answer model from a shared Java default.
