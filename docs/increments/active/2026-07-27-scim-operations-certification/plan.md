# Plan

## PR O1 — Entra And Okta Conformance

Scope:

- run the Microsoft SCIM Validator and store sanitized regression fixtures;
- run one real Entra non-gallery user/group lifecycle;
- run the Okta specification and CRUD lifecycle suites;
- run one real Okta user/group lifecycle;
- fix only interoperability behavior that remains within the accepted profile;
- version a provider capability matrix and setup runbook;
- verify discovery remains truthful for Users-only and Users+inert-Groups
  states, plus mapped Groups only when A1-A3 are included;
- attach the required evidence manifest with provider versions and artifact
  hashes.

Merge gate:

- [ ] Microsoft validator has no unresolved failure in the supported profile;
- [ ] Entra create/update/deactivate/reactivate/group/remove flows pass;
- [ ] Okta specification and CRUD suites pass;
- [ ] Okta PUT/PATCH and membership variants pass;
- [ ] retry after timeout creates no duplicate resource or membership;
- [ ] unsupported Bulk, sort, ETag, password change, and nested groups return
  documented behavior;
- [ ] repository fixtures contain no live tenant data, PII, or secret.
- [ ] every live result has a complete, independently approved, unexpired
  evidence manifest.

## PR O2 — Resilience, Limits, Security, And Observability

Scope:

- benchmark representative, boundary, and overload datasets;
- publish and enforce the operating envelope through typed configuration;
- add request/rate/filter/PATCH/group bounds and `Retry-After`;
- add PII-safe metrics, traces, dashboards, and alerts;
- test token theft response, rotation, expiry, revoke, and key-version change;
- run cross-tenant enumeration, timing, overposting, parser, and mass-change
  security tests;
- add out-of-order and database failure injection;
- on the mapped branch, add OpenFGA/worker failure injection and measure
  mapped-grant convergence;
- attach evidence manifests for load, security, deprovision, and optional
  mapping convergence.

Merge gate:

- [ ] boundary load meets the published latency/error envelope;
- [ ] overload is bounded and recovers without queue/resource corruption;
- [ ] mass deactivation applies safely, alerts, and remains recoverable;
- [ ] a stolen/revoked credential loses access immediately within the defined
  cache bound;
- [ ] no metric label, log, trace, event, or error contains raw PII or secret;
- [ ] two-tenant negative suite shows no data/count/error-category leak;
- [ ] deprovision service level is proven;
- [ ] on the mapped branch, authorization convergence service level is proven.

## PR O3 — Restore Rehearsal, Canary, And GA Consolidation

Scope:

- restore backup into an isolated PostgreSQL candidate and, on the mapped
  branch, an isolated OpenFGA candidate;
- on the mapped branch, reconcile managed assignments and compare exact
  ownership/model IDs;
- rehearse credential recovery/rotation without documenting secret values;
- run the bounded production smoke for one canary organization;
- set `READ_ONLY`, roll back the binary, prove denial and data preservation,
  redeploy, repair, and resume; separately prove `SUSPENDED` denies all SCIM
  credentials;
- complete operator, incident, offboarding, correlation conflict, drift, and
  recovery runbooks;
- consolidate architecture/spec/test/roadmap current facts only after evidence;
- move completed increment docs after all gates pass.
- attach approved restore, rollback, canary, limited-availability soak, and GA
  evidence manifests.

Repository gate:

- [ ] focused unit/integration/contract/browser suites pass;
- [ ] `.\gradlew.bat clean build` passes;
- [ ] Spring Modulith verification passes;
- [ ] PostgreSQL migration and previous-binary compatibility pass;
- [ ] OpenFGA model tests pass; mapped-branch convergence tests pass when A1-A3
  are included;
- [ ] committed product and SCIM contract drift tests pass;
- [ ] web lint, typecheck, unit, build, and browser tests pass;
- [ ] IDE/static inspection is clean for edited backend Java; frontend-native
  lint, typecheck, build, and browser gates are clean for edited web files.

Production gate:

- [ ] Entra and Okta live evidence is accepted;
- [ ] one canary organization completes the full smoke;
- [ ] non-SCIM recovery administrator is proven;
- [ ] PostgreSQL backup/restore is proven; managed-assignment rebuild is proven
  on the mapped branch;
- [ ] connection and global operational/security controls are proven;
- [ ] read-only freeze and all-credential security cutoff are proven as
  distinct controls;
- [ ] mass-change and credential-attack alerts fire; mapped-branch outbox-lag
  and drift alerts fire when A1-A3 are included;
- [ ] rollback preserves tombstones, audit, identity bindings, and effective
  deactivation;
- [ ] on the mapped branch, no Source ACL or manual OpenFGA relationship is
  altered by repair.
- [ ] every production claim points to an accepted, unexpired evidence
  manifest.

Increment exit:

- [ ] O1, O2, and O3 are merged in order.
- [ ] limited availability met its declared soak/incident thresholds before
  promotion.
- [ ] certified provider profiles are generally available, while every new
  organization connection still starts disabled.
- [ ] Deferred capabilities remain disabled and truthfully advertised.
- [ ] ADR 0016 remains `Accepted`; any superseding decision is linked.
