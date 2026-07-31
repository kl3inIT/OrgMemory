---
packages:
  orgmemory: patch
subject: Remove the ACL-to-Source Ledger dependency
---

# Remove the ACL-to-Source Ledger dependency

## Improvements

ACL heads now consume an ACL-owned source target and preserve stable conflict
semantics without importing Source Ledger entities or exceptions.
