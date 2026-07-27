# Plan

## Dependency And Merge Rule

This increment has no SCIM dependency and must merge before every other native
SCIM PR. Each PR branches from the latest `main`; migration versions are
reallocated after rebase rather than kept on a long-lived stack.

ADR 0016 was accepted on 2026-07-27 by explicit project-owner direction after
the attempted Claude Fable 5 review was blocked by the account spend limit.
The owner waived that review as an implementation gate; its eventual result is
follow-up evidence and cannot silently rewrite already-shipped behavior.

## PR H1 — Tenant-Scoped Readers And Composite Integrity

Scope:

- add the preflight and next Flyway migration for composite tenant foreign keys;
- replace global application-user email repository methods with
  organization-scoped methods in invitation and source-principal mapping flows;
- validate invitation organization, department, and inviter in the service;
- retain the global normalized-email unique index during this reader-first
  compatibility window;
- add PostgreSQL integration tests for every new uniqueness and foreign-key
  invariant;
- add an upgrade test over a populated pre-migration fixture.

Out of scope:

- SCIM tables or endpoints;
- multi-organization membership for one OIDC subject;
- automatic repair of invalid production rows.

Merge gate:

- [ ] preflight reports clean data or an approved manual remediation;
- [x] Flyway upgrade and `ddl-auto=validate` pass on PostgreSQL;
- [x] same email in two organizations still fails while the global rollback
  constraint is deliberately retained;
- [x] same normalized email in one organization fails;
- [x] every cross-tenant reference fails at service and database layers;
- [x] trusted source-principal email mapping cannot resolve a same-email user in
  another organization;
- [x] focused tests and Spring Modulith verification pass.

Rollback gate:

- [x] the expanded schema keeps every column and the global email index used by
  the previous binary;
- [x] existing invitation and current-actor suites pass with the expanded
  schema;
- [x] no down migration is required.

Implementation checkpoint on 2026-07-27:

- V8 upgrades a populated V7 PostgreSQL 18 fixture, runs an explicit preflight,
  installs and validates four composite tenant foreign keys, and rejects
  invalid pre-existing ownership instead of repairing it.
- Invitation provisioning and trusted source-principal email matching now query
  by `(organization_id, email)`. Invitation creation verifies organization,
  optional department, and inviter ownership before persistence.
- Focused service/migration tests, all `:core:test`, and the terminating
  repository-wide `clean test` pass. JetBrains inspection was unavailable
  because the IDE had the main repository open rather than this worktree, so
  the documented Gradle and mechanical fallback gates were used.
- The local shared runtime was not running and Compose interpolation requires
  an unavailable local MCP client secret. Deployment-data preflight remains an
  environment release gate. Per project-owner direction, an exact old-binary
  rehearsal is not an H1 PR blocker: compatibility is bounded to the unchanged
  columns/global email index, existing suites, and forward-only recovery.

## PR H2 — Atomic External Identity Binding

Scope:

- replace `linkIfAbsent` with a transaction that inserts or re-reads the winner;
- distinguish idempotent replay, subject-already-bound, and
  user-already-bound-to-another-subject outcomes;
- fail closed when one verified email has eligible invitations in more than one
  organization;
- accept an invitation only after the final binding points to the intended
  application user;
- lock or compare-and-set invitation acceptance;
- return stable business error codes without exposing another tenant's user;
- cover issuer/subject and invitation races with real PostgreSQL concurrency
  tests.

Merge gate:

- [ ] 50 or more concurrent identical attempts leave one binding and one
  accepted invitation;
- [ ] conflicting users cannot both claim the same subject;
- [ ] one user cannot acquire two subjects for the same issuer;
- [ ] a losing transaction re-reads and validates the winner;
- [ ] ambiguous cross-organization invitations accept neither invitation;
- [ ] no conflict response reveals foreign user or organization identifiers;
- [ ] browser and bearer current-actor suites remain green.

## PR H3 — Organization Email Constraint Cutover

Precondition:

- H1 and H2 are deployed and soaked;
- the H2 artifact/build ID is retained as the minimum supported rollback
  binary;
- repository search proves no production email reader is global.

Scope:

- add the organization-scoped normalized-email unique index;
- remove the old global normalized-email unique index;
- run a cutover preflight for duplicates within one organization;
- add a fixture with the same email in two organizations;
- test the H2 rollback artifact against the post-cutover schema and duplicate
  fixture;
- document that pre-H1 binaries are no longer rollback targets.

Merge gate:

- [ ] same normalized email in two organizations succeeds;
- [ ] same normalized email twice in one organization fails;
- [ ] invitation and source-principal lookup resolve only within their owning
  organization;
- [ ] the retained H2 rollback artifact handles the duplicate-email fixture;
- [ ] roll-forward and database-restore procedures cover accidental rollback
  below the compatibility floor.

## PR H4 — Current Identity Contract Consolidation

Scope:

- correct the identity specification to describe shipped invitation-gated
  provisioning;
- update identity coverage with current invitation and concurrency evidence;
- record the one-subject/one-organization invariant and the future trusted
  workforce-key seam;
- remove any current-fact statement that treats email, Keycloak role, or a
  future SCIM attribute as a durable identity;
- describe `app_users.role` accurately as a local business/classification
  attribute rather than an OpenFGA grant or harmless rendering-only hint;
- add an operational query/runbook for migration preflight and conflict
  diagnosis.

Merge gate:

- [ ] architecture, spec, tests, and roadmap use the same authority names;
- [ ] every current-fact claim points to implemented code or test evidence;
- [ ] future SCIM behavior remains only in the ADR, roadmap, and active
  increments;
- [ ] Markdown links and repository diff checks pass.

## Increment Exit

- [ ] H1, H2, H3, and H4 are merged in order.
- [ ] The clean-schema and populated-schema migration rehearsals pass.
- [ ] Existing invitation, browser login, bearer login, admin-user, and logout
  flows pass.
- [ ] ADR 0016 was `Accepted` with its Fable review record before H1
  implementation started, or the project owner explicitly recorded an
  exception to that review gate.
