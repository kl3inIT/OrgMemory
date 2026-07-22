# Secure Hybrid Retrieval Plan

## Authorization Contract

- [x] Add provider-neutral ListObjects and BatchCheck contracts.
- [x] Implement them with the pinned OpenFGA model and validate all returned
  object references.
- [x] Add adapter tests for allow, deny, malformed response, partial batch,
  interruption, and provider outage.

## Retrieval Core

- [x] Add a provider-neutral query embedding contract.
- [x] Add PostgreSQL FTS projection/index and FTS + pgvector RRF query.
- [x] Enforce tenant, lifecycle, current generation, embedding profile, source
  ACL freshness/seals, and publication model before ranking/limit.
- [x] BatchCheck and canonically recheck every returned citation.
- [x] Persist structured retrieval audit evidence without raw query text.

## Delivery And Cleanup

- [x] Add the new secure search HTTP contract and Spring AI embedding adapter.
- [x] Remove prototype Knowledge Asset list/detail retrieval and Java role-policy
  consumers without deleting the canonical ingestion/publication ledger.
- [x] Update OpenAPI/generated clients if the delivery contract changes.

## Verification

- [x] Test cross-tenant and unauthorized candidates never enter results.
- [x] Test pending assets, inactive/old generations, stale ACL, model/profile
  mismatch, OpenFGA outage, and citation-time revocation.
- [x] Run OpenFGA model tests, Java static checks, Gradle clean test, generated
  API drift checks, and web typecheck/build.

## Backlog After This Slice

- [ ] Converge persistent Capability Asset tuples, then replace its list-path
  per-row OpenFGA checks and per-row usage counts with ListObjects/BatchCheck,
  pagination, and one aggregate count query.
- [x] Make direct upload target a Knowledge Space and authorize the command at
  its parent with `can_create_asset`; reserve organization
  `can_manage_sources` for administrative source operations.
- [ ] Build the in-app assistant on the verified retrieval service, then expose
  only read-only search/evidence tools through authenticated, audited MCP.
- [ ] Add graph retrieval only after secure hybrid retrieval and graph evidence
  contracts are proven; keep graph data a rebuildable projection.
