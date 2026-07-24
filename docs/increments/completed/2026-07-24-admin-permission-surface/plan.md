# Admin Permission Surface Plan

Four phases, each a commit that leaves the build green.

## Phase 1 — Expansion port, model, fixtures

- [x] `RelationshipExpansionPort` in `core/authorization`: takes a `ResourceRef` and a
  `RelationName`, returns a userset tree of assignable leaves and computed nodes, modelled
  as a sealed interface so core can walk it without knowing OpenFGA's wire shape. The
  relation is a `RelationName` rather than a `PermissionKey` because a walk descends from a
  permission into the nouns composing it, and `PermissionKey` still admits only `can_*`.
- [x] `OpenFgaRelationshipExpansionAdapter` alongside the existing four adapters, and a
  matching stub in `UnavailableAuthorizationConfiguration` so a missing OpenFGA still fails
  closed rather than at injection.
- [x] `model.fga`: assignable `knowledge_curator` on `type organization` and computed
  `can_curate_graph: knowledge_curator or administrator`. Additive only.
- [x] `store.fga.yaml`: the administrator resolves `can_curate_graph` with no
  `knowledge_curator` tuple present, a curator resolves it without gaining
  `can_manage_members`, and a plain member resolves neither.
- [x] `local-demo-tuples.csv`: make the two demo employees diverge. Both were `member` of
  organization `1111…` and unit `2222…`, so every verdict came out identical and there was
  nothing to demonstrate. Minh becomes `manager` of the unit and the unit's managers become
  `reviewer` of space `802…` — what his `TEAM_LEAD` record already claims — so he gains
  `can_publish` where Linh does not, through a derivation the inspector can explain. Only
  the two spaces that exist in the database are used; inventing a third would hand the API a
  container it cannot name. Linh already carries an explicit `DENY` from `V12`.

Gate: `:core:test` green; `fga model test --tests store.fga.yaml` 6/6, 23 checks.

## Phase 2 — Effective permission reads

- [x] `AccessExplanationService` in `core/authorization`: resolve the organization `can_*`
  permissions, and turn an `Expand` tree into the one derivation that reached this
  principal, following usersets recursively with a bounded depth.
- [x] It checks `RelationshipAuthorizationPort` directly rather than through
  `EffectiveAuthorizationService`, which collapses an unanswered check into a denial. That
  collapse is right for enforcement and wrong for an explanation. The organization guard is
  kept.
- [x] `UNKNOWN` when the supplied ACL provenance is past its validity. Not collapsed to
  `DENIED`; Decision 6 exists because that collapse is a falsehood.
- [x] `AdminPermissionController` behind `AdminAccessGuard`:
  `GET /api/admin/users/{id}/permissions` and `POST /api/admin/access/explain`.
- [x] Nothing persisted. Every response carries the evaluation instant and the
  `authorization_model_id` it resolved against.
- [x] Regenerate `contracts/openapi.json` and `pnpm -C web gen:api`.
- [ ] **Not done — `GET /api/admin/users/{id}/containers`.** Reaching `acl_authority`,
  generation, and validity means joining `knowledge_assets` through `source_objects` and
  `raw_source_objects` to `source_acl_snapshots`, which cannot be honestly verified without
  a live database. `AclProvenance` is already a parameter of `explain` and rides every
  response, so this is wiring rather than redesign. Until it lands, provenance is
  `ORGMEMORY` and nothing claims a mirrored verdict it has not read.

Gate: `:core:test` and `:apps:api:test` green, including `OpenApiContractTests`.

## Phase 3 — Role assignment

- [x] `GET /api/admin/roles` listing roles with their assignees, paging the store because
  OpenFGA has no call for it, and reporting when it stopped rather than presenting a
  truncated list as the whole set.
- [x] `POST /api/admin/roles/{role}/members` and
  `DELETE /api/admin/roles/{role}/members/{userId}` writing `role#assignee`.
- [x] `AdministrativeTupleScope` rejects any write whose object type is not `organization`
  or `role`, tested explicitly against a `knowledge_asset` target and against a role name
  trying to smuggle one.
- [x] Revoking your own role is refused — unlike the existing self-edit guard on user role,
  nobody would be left who could undo it.
- [x] Regenerate the contract and client.
- [ ] **Not done — a permission audit event per mutation.** `PermissionAuditService` lives
  in `core.permission`, which `core.authorization` must not depend on without inverting the
  module direction Modulith enforces. It belongs in the API layer, which already holds the
  actor; left out rather than bent.

Gate: `:core:test` and `:apps:api:test` green.

## Phase 4 — Web

- [x] `AccessVerdict`: the three-state atom carrying state, authority, origin, generation
  and sync age. Every surface renders a verdict through it, so none can drop the part that
  makes it safe to act on.
- [x] `AccessPath`: the vertical rail for an allow — object, relation, how it was reached —
  ending at the derivation that granted it. Plain DOM; no graph library, because the layout
  is fixed and there is no layout problem to solve.
- [x] `AccessDenied`: the flat list of evaluated branches, separating a missing relationship
  from an explicit deny and saying the latter cannot be fixed by granting one.
- [x] `/admin/users/$userId`: organization permissions, roles (the only editable block,
  `command` inside `dialog`), and a scoped resource check. The users list links each name to
  it. `users.tsx` became `users.index.tsx` so the detail route is a sibling rather than a
  child of a parent with no `Outlet`.
- [x] No new dependency. Existing `table`, `card`, `badge`, `command`, `dialog`, `select`,
  `separator`, `skeleton`, `tooltip`.
- [ ] **Not done — external identities and reachable containers on the profile.** Both
  depend on the deferred containers endpoint.
- [ ] **Not done — the "copy as diagram" mermaid export.**
- [ ] **Not done — relabelling the `app_users.role` control.** It still reads as a grant
  while granting nothing; the profile page now shows what actually decides, but the list
  control is unchanged.

Gate: `pnpm -C web typecheck` and `pnpm -C web build` green, then a browser run against the
live stack (Keycloak, OpenFGA, Postgres, API, Vite):

- the org administrator resolves `can_curate_graph` **Allowed** with no `knowledge_curator`
  tuple present, proving the `or administrator` branch;
- Minh resolves `can_publish` on space `802…` **Allowed**, rendering the three-step
  derivation `can_publish` → `reviewer` → `organizational_unit … manager`;
- a user with no tuples resolves every permission **Denied**;
- no console errors.

Not proven in a browser: a mirrored verdict showing source and sync age, because provenance
is `ORGMEMORY` until the containers endpoint lands. The three-state rendering is covered by
unit tests instead.
