---
packages:
  orgmemory: patch
subject: Move catalog and chunk values to Knowledge Asset
---

# Move catalog and chunk values to Knowledge Asset

## Improvements

Knowledge Asset now owns its catalog projection, normalized text-chunk value,
and PostgreSQL vector encoding, so Retrieval and other consumers depend on the
domain that persists and publishes those values.
