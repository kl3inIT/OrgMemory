---
packages:
  orgmemory: patch
subject: Route source ingestion through the ACL facade
---

# Route source ingestion through the ACL facade

## Improvements

Source ingestion now uses an ACL-owned transactional facade instead of
coordinating ACL repositories and persistence entities directly.
