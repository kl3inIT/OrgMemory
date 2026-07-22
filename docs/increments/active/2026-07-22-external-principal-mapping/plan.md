# External Principal Mapping Plan

## 1 — Schema And Model

- [x] Add Flyway migration `V19__external_principal_mapping.sql`:
  `source_principals` (unique per organization + source system + connection key
  + external key; kind `SOURCE_USER|SOURCE_GROUP`; `sso_verified`;
  `last_seen_at`), `source_principal_mappings` (kind-constrained composite FK
  so only `SOURCE_USER` maps to `app_users(id, organization_id)`; partial
  unique index allows one ACTIVE mapping per principal),
  `source_acl_group_members` (sealed per snapshot; reuses
  `reject_entry_insert_into_sealed_acl` and
  `reject_source_acl_evidence_mutation` triggers), and the widened
  `chk_source_acl_principal_type` CHECK on `source_acl_entries`.
- [x] Add `SOURCE_USER` to `SourcePrincipalType`; extend the
  `SourceAclPolicy.matches` switch with `case SOURCE_USER, SOURCE_GROUP ->
  false` so the Java recheck stays fail-closed until phase 3.
- [x] Extend the `KnowledgeIngestionService` complete-ACL guard that rejects
  `SOURCE_GROUP` today so it rejects `SOURCE_USER` for the same reason until
  mapping enforcement lands.
- [x] JPA entities and repositories in `core.knowledge`: `SourcePrincipal`
  (mutable observation, extends `BaseEntity` like `KnowledgeSpace`),
  `SourcePrincipalMapping` (extends `BaseEntity`), `SourceAclGroupMember`
  (immutable, own `@Id` + `created_at` like `SourceAclEntry`), plus enums
  `SourcePrincipalKind`, mapping method, and mapping status; Modulith
  verification stays green.
- [x] Gates: `compileJava`, `ModulithVerificationTests`, and the
  `OrgMemoryApiContextLoadTests` Testcontainers run — Flyway applies V19 on
  real PostgreSQL and Hibernate `validate` confirms the three new entities
  match the schema.

## 2 — Mapping Service And Matcher

- [x] `SourcePrincipalService`: idempotent `observe` upsert of observed
  principals plus `recordGroupMembership` for a pre-seal snapshot; observation
  grants nothing.
- [x] `SourcePrincipalMappingService` commands: `autoMap` (tiers), `selfClaim`,
  `adminConfirm`, `revoke`; each validates an active internal user, enforces
  single-active-mapping, and appends a permission audit event via
  `PermissionAuditService.record` (`REQUIRES_NEW`).
- [x] Matcher runs tier 1 (issuer/subject IdP join) then tier 2 (email join
  only for SSO-verified source identities); everything else stays unmapped.
  Locked by `SourcePrincipalMappingServiceTests` (8 cases).

## 3 — Retrieval Enforcement

Enforcement is SQL-only in `SecureKnowledgeRetrievalStore` (one `ELIGIBLE_FROM`
shared by lexical/semantic/recheck/visibleSourceObjectIds). The legacy Java
`SourceAclPolicy`/`SourceAclEvaluation` were dead code (no callers, no tests) and
are removed rather than extended.

- [x] Extend `PRINCIPAL_MATCH` so `SOURCE_USER` entries resolve through an ACTIVE
  `source_principal_mappings` row to `:actorUserId`, and `SOURCE_GROUP` entries
  resolve through the same snapshot's sealed `source_acl_group_members` joined to
  an ACTIVE mapping. Add the `:actorUserId` UUID parameter.
- [x] Remove dead `SourceAclPolicy`/`SourceAclEvaluation`; enforcement +
  recheck both flow through the single store SQL, so there is no SQL/Java drift
  surface to reconcile.
- [x] Missing, revoked, or inactive mapping and unknown principal kinds all
  resolve to deny (the EXISTS subqueries require `status = 'ACTIVE'`; no branch
  grants otherwise).
- [x] Implement the [ADR 0009](../../../decisions/0009-dynamic-source-acl-ceiling.md)
  ceiling for live sources: the store now enforces only the current sealed
  generation for non-`UPLOAD` sources (the ingestion DENY contribution and the
  ingestion ALLOW gate are gated on `so.source_type = 'UPLOAD'`), while `UPLOAD`
  sources keep the ingestion∩current intersection unchanged.
- [ ] Runtime verification of this SQL is Phase 4's Testcontainers proof (no
  existing test executes the store SQL — the service test stubs the store).

## 4 — Fixture And Proofs

- [ ] Slack-shaped fixture: workspace users with and without SSO-verified
  email, IdP join evidence for at least one user, one public channel, one
  private channel with sealed membership, ACL generations referencing
  `SOURCE_GROUP` and `SOURCE_USER` principals.
- [ ] Integration proofs: unmapped-denies end to end; mapping grants existing
  documents without re-ingestion; revoke closes access; inactive internal user
  denied despite mapping; unverified email never auto-maps; sealed
  generation/membership immutable at the database level.
- [ ] Regression: full clean test, OpenFGA model tests, web typecheck/build.

## Completion

- [ ] Update knowledge-ingestion and secure-retrieval specs plus
  ARCHITECTURE.md with the shipped behavior only.
- [ ] Tick the corresponding external-principal items in the vertical-slice
  plan and record evidence in docs/tests.
- [ ] Move this increment to `completed`.

## Execution Notes

- Only phase 3 changes retrieval behavior; phases 1-2 must leave every
  existing test green and every new code path denying.
- Style anchors: `KnowledgeSpace`/`BaseEntity` for mutable entities,
  `SourceAclEntry` for immutable evidence rows, V11 for tenant-safe composite
  FK chains and seal triggers, `PermissionAuditService.record`
  (`REQUIRES_NEW`) for audit events.
- V19 is already written in this branch; it only adds tables/constraints and
  widens one CHECK, so it deploys before any entity exists.
- Local gates: `.\gradlew.bat --no-daemon compileJava`, then
  `.\gradlew.bat --no-daemon :core:test`, web untouched until phase 4.
