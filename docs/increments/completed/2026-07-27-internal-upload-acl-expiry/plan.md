# Internal upload ACL expiry regression

- [x] Reproduce the empty Documents and Knowledge Graph state for a synthetic
  employee in production.
- [x] Prove that projections and documents still exist and isolate the failure
  to expired current ACL snapshots.
- [x] Judge the hard TTL against the practical connector lifecycle and supersede
  the fail-closed-on-time policy.
- [x] Keep enforcing the latest sealed connector permission generation after
  its freshness timestamp while preserving OpenFGA, ACL, classification, and
  department checks.
- [x] Add PostgreSQL-backed regression coverage for stale connector and
  OrgMemory-owned evidence.
- [ ] Merge, deploy, and verify Documents and Knowledge Graph with the affected
  production account.
