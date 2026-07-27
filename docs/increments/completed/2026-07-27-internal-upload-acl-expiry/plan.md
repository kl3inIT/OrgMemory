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
- [x] Merge, deploy, and verify Documents and Knowledge Graph with the affected
  production account.

Production evidence: PR #74 merged as `4d907f154c937365a790238f2d8161d7e3cd7614`;
CI `30233923642`; image build `30234114183`; deployment `30234348176`. The
affected Bùi Đức Minh account regained 21 ready Documents. Graph access then
returned its 92 entities and 129 relations; the separate frontend rendering
regression was completed in PR #75.
