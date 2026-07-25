# Runtime Knowledge Space Management Plan

## 1. Authorization scope

- [x] `AdministrativeTupleScope`: `knowledge_space` added to `WRITABLE_OBJECT_TYPES`,
      Javadoc rewritten to say why `acl_authority` does not reach a space and why
      `knowledge_asset` still refuses.
- [x] `RoleAdministrationServiceTests`: the `knowledge_space` assertion became a
      positive case with its own reason; the `knowledge_asset` one is unchanged.

## 2. Tuple reads by object

- [x] `RelationshipTupleReconciliationPort.readObject(object, pageSize, token)`.
- [x] `OpenFgaRelationshipTupleReconciliationAdapter` implements it with
      `ClientReadRequest._object(...)`, sharing one private `read` with the
      unfiltered call so both report the same reason codes.
- [x] `UnavailableAuthorizationConfiguration` answers indeterminate for it.

## 3. Core service

- [x] `KnowledgeSpace` package-private constructor; `isActive()`.
- [x] `KnowledgeSpaceRepository.existsByOrganizationIdAndKey`,
      `findByOrganizationIdOrderByName`.
- [x] `KnowledgeSpaceKey` derives the slug and rejects a name with nothing to
      derive from.
- [x] `KnowledgeSpaceSubject` — a closed set of subject shapes, so a tenant check
      is a field comparison rather than parsing an arbitrary reference.
- [x] `KnowledgeSpaceAdministrationService` — create, list, grant, revoke, each
      mutation audited.
- [x] `@Service`; no manual bean wiring needed.

## 4. API

- [x] `AdminKnowledgeSpaceController`: `POST` create, `GET` list,
      `POST /{id}/grants`, `DELETE /{id}/grants`.
- [x] `GET /grant-options` — **added during execution, not in the original plan.**
      Browser verification found the first cut offered every relation against
      every subject while the model accepts only some pairs, so a valid-looking
      grant failed at the store. The table now lives in core and is published.
- [x] Creation checks `can_create_knowledge_space`; grant and revoke check
      `can_manage_acl` on the space.
- [x] Slug collision maps to `409` through a new handler; an unapplied tuple write
      maps to `503` through the existing one.
- [x] `contracts/openapi.json` refreshed.

## 5. Web

- [x] `web/src/lib/hey-api` regenerated.
- [x] `admin-spaces-page.tsx` — create form, spaces with their grants, add and
      remove a grant.
- [x] Subject picker driven by `/grant-options`, so the relation narrows the
      subject list instead of the pair being reconciled by a refusal.
- [x] `DEPARTMENT_MANAGERS` — **added during execution.** Reviewing accepts
      `organizational_unit#manager`, so without it the UI could not express a
      grant the bootstrap file already contains.
- [x] Route `admin/spaces.tsx` and a sidebar entry under Permissions.
- [x] `admin-queries.ts` additions and cache invalidation.

## 6. Tests

- [x] `AdministrativeTupleScope` assertions both ways.
- [x] `KnowledgeSpaceAdministrationServiceTests` — tuple set on create, slug
      collision, unapplied write, relation/subject validation, the grant table
      pinned against the model, listing completeness.
- [x] `KnowledgeSpaceAdminIntegrationTests` — create, structural link, duplicate
      key, grant/revoke round trip, published options matching what is accepted,
      cross-tenant refusal, non-administrator refusal.

## 7. Gates

- [x] Backend `clean test`.
- [ ] **Not done — JetBrains IDE inspection.** The MCP was not connected this
      session. The skill's mechanical floor ran instead: no source file missing a
      package line, no zero-byte source/config/migration, no misnamed Flyway
      migration.
- [x] Oxlint, TypeScript typecheck, production build.
- [x] Browser pass over create, grant, revoke against the running stack, which is
      what found the grant-options defect.
- [x] `ARCHITECTURE.md` and `docs/roadmap.md` consolidated.
- [x] PR to `main`.

## Not attempted

- Deactivating or archiving a space; moving assets between spaces; per-asset
  grants. Recorded in the design as out of scope and in the roadmap as next.
- No Flyway migration: `knowledge_spaces` already had every column, and the
  entity gained only a constructor and a getter.
