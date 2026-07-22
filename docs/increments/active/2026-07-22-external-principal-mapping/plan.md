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
- [x] Runtime verification of this SQL is Phase 4's Testcontainers proof
  (`ExternalPrincipalRetrievalIntegrationTests` builds a Slack ledger and calls
  the real store).

## 4 — Fixture And Proofs

- [x] Slack-shaped ledger built directly via JDBC (no connector exists yet):
  SLACK `source_objects`/revisions/chunks/publication, a sealed ACL generation
  with `SOURCE_GROUP` + `SOURCE_USER` ALLOW entries, sealed channel membership,
  and `source_principals`. A second ledger adds a deny-all ingestion generation
  and a granting current generation for the ceiling proof.
- [x] Integration proofs in `core` `ExternalPrincipalRetrievalIntegrationTests`
  (real PostgreSQL via Testcontainers, calling `SecureKnowledgeRetrievalStore`
  directly): unmapped denies; a group-membership mapping grants the existing
  document without re-ingestion; revoke closes access; a direct `SOURCE_USER`
  entry resolves through its mapping; a mapped non-member is denied; and the
  ADR 0009 ceiling grants through the current generation despite a deny-all
  ingestion snapshot for a live source. Inactive-user denial and
  unverified-email non-mapping are covered by
  `SourcePrincipalMappingServiceTests`; sealed immutability is enforced by the
  V11/V19 triggers.
- [x] Regression: `:core:test` and `OrgMemoryApiContextLoadTests` green (Flyway
  applies V19, Hibernate `validate` passes, existing suites unaffected).

## Completion

- [x] Update the knowledge-ingestion spec with the shipped external-principal
  mapping and ADR 0009 ceiling behavior; secure-retrieval already links ADR 0009.
- [ ] Tick the corresponding external-principal items in the vertical-slice
  plan and record evidence in docs/tests.
- [ ] Move this increment to `completed` (after the vertical-slice ties off).

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
