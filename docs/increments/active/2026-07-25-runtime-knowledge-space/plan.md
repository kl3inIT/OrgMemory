# Runtime Knowledge Space Management Plan

## 1. Authorization scope

- [ ] `AdministrativeTupleScope`: add `knowledge_space` to `WRITABLE_OBJECT_TYPES`,
      rewrite the Javadoc to say why `acl_authority` does not reach a space and why
      `knowledge_asset` still does.
- [ ] `RoleAdministrationServiceTests`: flip the `knowledge_space` assertion, keep
      the `knowledge_asset` one.

## 2. Tuple reads by object

- [ ] `RelationshipTupleReconciliationPort.readObject(String object, int pageSize,
      String continuationToken)`.
- [ ] `OpenFgaRelationshipTupleReconciliationAdapter`: implement with
      `ClientReadRequest._object(...)`, same indeterminate reason codes as `read`.
- [ ] Any existing test double implementing the port gains the method.

## 3. Core service

- [ ] `KnowledgeSpace`: package-private factory taking id, organization, department,
      key, name; `active = true`.
- [ ] `KnowledgeSpaceRepository`: `existsByOrganizationIdAndKey`,
      `findByOrganizationIdOrderByName`.
- [ ] `KnowledgeSpaceAdministrationService` in `core.knowledge`:
      - `create(actor, name, departmentId)` — slug, collision check, insert, flush,
        write the three tuples, fail closed, audit.
      - `list(actor)` — every space in the organization with its grants.
      - `grant(actor, spaceId, relation, subject)` / `revoke(...)` — validate the
        relation is one of viewer/contributor/reviewer/administrator, validate the
        subject reference, audit.
- [ ] `KnowledgeSpaceSlug` — lowercase, non-alphanumeric to `-`, collapse, trim,
      reject empty, cap at 128.
- [ ] Register the service bean where `RoleAdministrationService` is registered if it
      needs constructor wiring, otherwise `@Service`.

## 4. API

- [ ] `AdminKnowledgeSpaceController` at `/api/admin/knowledge-spaces`:
      `POST` create, `GET` list, `POST /{id}/grants`, `DELETE /{id}/grants`.
- [ ] Creation checks `can_create_knowledge_space` on the organization; grant and
      revoke check `can_manage_acl` on the space.
- [ ] Slug collision maps to `409`; a non-applied tuple write maps to `503`.
- [ ] `OpenApiContractTests` refresh of `contracts/openapi.json`.

## 5. Web

- [ ] Regenerate `web/src/lib/hey-api` from the refreshed contract.
- [ ] `admin-spaces-page.tsx`: create form (name, optional department), list of
      spaces with grants, add and remove a grant.
- [ ] Subject picker covering the four practical kinds: everyone in the organization,
      a department, a role, a named user.
- [ ] Route `admin/spaces.tsx` and a sidebar entry under Permissions.
- [ ] `admin-queries.ts` additions.

## 6. Tests

- [ ] `AdministrativeTupleScope` unit assertions.
- [ ] `KnowledgeSpaceAdministrationServiceTests`: tuple set on create, slug
      collision, indeterminate write leaves no row, relation and subject validation.
- [ ] `KnowledgeSpaceAdminIntegrationTests`: create → grant → visible to grantee →
      revoke → invisible; non-administrator refused.

## 7. Gates

- [ ] Backend clean test.
- [ ] JetBrains IDE inspection on edited Java files.
- [ ] Oxlint, TypeScript typecheck, production build.
- [ ] Browser pass over create → grant → revoke.
- [ ] Consolidate `ARCHITECTURE.md` and `docs/roadmap.md`, move the increment to
      `completed`.
- [ ] PR to `main`.
