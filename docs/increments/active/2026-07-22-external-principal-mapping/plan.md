# External Principal Mapping Plan

## 1 — Schema And Model

- [ ] Add Flyway migration for `source_principals` (organization, source type,
  external key, kind `SOURCE_USER|SOURCE_GROUP`, observed email/display,
  `last_seen_at`; unique per organization + source type + external key).
- [ ] Add `source_principal_mappings` (source principal, internal user,
  `method`, evidence, `verified_at`, status; at most one ACTIVE mapping per
  source principal enforced by a partial unique index).
- [ ] Add sealed per-generation group membership storage following the existing
  append-only ACL sealing conventions; no UPDATE/DELETE path.
- [ ] Add `SOURCE_USER` to `SourcePrincipalType` and verify existing ACL entry
  handling stays fail-closed for the new kind.
- [ ] JPA entities and repositories in `core.knowledge`; Modulith verification
  stays green.

## 2 — Mapping Service And Matcher

- [ ] `SourcePrincipalService`: idempotent upsert of observed principals and
  group membership from an identity payload; observation grants nothing.
- [ ] `SourcePrincipalMappingService` commands: `applyIdpJoin`,
  `applySsoEmailJoin`, `selfClaim`, `adminConfirm`, `revoke`; each validates an
  active internal user, enforces single-active-mapping, and appends a
  permission audit event in `REQUIRES_NEW`.
- [ ] Matcher runs tier 1 (issuer/subject join evidence) then tier 2 (email
  join only for SSO-verified source identities); everything else stays
  unmapped.

## 3 — Retrieval Enforcement

- [ ] Extend the authorization predicate so `SOURCE_USER` entries resolve
  through an ACTIVE mapping to the querying user, and `SOURCE_GROUP` entries
  resolve through the enforced generation's sealed membership joined to ACTIVE
  mappings.
- [ ] Extend `SourceAclPolicy` so the Java recheck reproduces the same
  resolution; SQL/Java drift keeps recording `POLICY_PREDICATE_DRIFT` denies.
- [ ] Missing, revoked, or inactive mapping and unknown principal kinds all
  resolve to deny.

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
