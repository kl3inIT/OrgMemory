---
packages:
  orgmemory: patch
subject: Verify retrieval recall before query cutover
---

## Improvements

Operators can now capture and score authorization-preserving retrieval recall
against an explicitly restored projection copy without generating answers or
touching the live database. The recorded 43-case comparison confirms that the
raw-query bypass stays level with the current keyword-seeded path and preserves
the evidence needed to diagnose shared misses before any query-plane cutover.
