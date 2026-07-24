# Admin Permission Surface Plan

Four phases, each a commit that leaves the build green.

## Phase 1 — Expansion port, model, fixtures

- [ ] `RelationshipExpansionPort` in `core/authorization`: takes a `ResourceRef`
  and `PermissionKey`, returns a userset tree of assignable leaves and computed
  nodes. Model the tree as a sealed record so core can walk it without knowing
  OpenFGA's wire shape.
- [ ] `OpenFgaRelationshipExpansionAdapter` alongside the existing four adapters,
  and a matching stub in `UnavailableAuthorizationConfiguration` so a missing
  OpenFGA still fails closed rather than at injection.
- [ ] `model.fga`: add assignable `knowledge_curator` to `type organization` and
  computed `can_curate_graph: knowledge_curator or administrator`. Additive only —
  no existing relation is renamed or removed.
- [ ] `store.fga.yaml`: assert the administrator resolves `can_curate_graph` with no
  `knowledge_curator` tuple present, and that a plain member does not.
- [ ] `local-demo-tuples.csv`: make the two demo employees diverge. Both are currently
  `member` of organization `1111…` and unit `2222…`, so every verdict comes out
  identical and there is nothing to demonstrate. Minh becomes `manager` of the unit and
  the unit's managers become `reviewer` of space `802…` — which is what his `TEAM_LEAD`
  record already claims — so he gains `can_publish` where Linh does not, through a
  three-hop derivation the inspector has something to explain. Only the two spaces that
  exist in the database are used; inventing a third would hand the API a container it
  cannot name. Linh already carries an explicit `DENY` from `V12`, so the denial case
  needs no new fixture, and a container-reach difference belongs to the graph increment
  that actually needs one.

Gate: `.\gradlew.bat :core:test` and, from
`integrations\authorization-openfga\src\test\openfga`,
`& '..\..\..\..\..\.tools\openfga\fga.exe' model test --tests store.fga.yaml`.

## Phase 2 — Effective permission reads

- [ ] `AdminPermissionService` in `core`: resolve the six organization `can_*`
  permissions in one `BatchCheck`; resolve reachable containers through
  `RelationshipAuthorizationSetPort`, joining `source_acl_entries` for
  `acl_authority`, generation, and sync age; and turn an `Expand` tree plus the
  actor's tuples into one decisive path.
- [ ] Verdict resolution returns `UNKNOWN` when the governing ACL generation is past
  TTL. Do not collapse it to `DENIED`; Decision 6 exists because that collapse is a
  falsehood.
- [ ] `AdminPermissionController` guarded by the existing `AdminAccessGuard`:
  `GET /api/admin/users/{id}/permissions`, `GET /api/admin/users/{id}/containers`,
  `POST /api/admin/access/explain`.
- [ ] Nothing is persisted. Every response carries the evaluation timestamp and the
  `authorization_model_id` it resolved against.
- [ ] Regenerate `contracts/openapi.json` and run `pnpm -C web gen:api`.

Gate: `.\gradlew.bat :apps:api:test`.

## Phase 3 — Role assignment

- [ ] `GET /api/admin/roles` listing role objects with their assignee counts, read
  through `RelationshipTupleReconciliationPort`.
- [ ] `POST /api/admin/roles/{role}/members` and
  `DELETE /api/admin/roles/{role}/members/{userId}` writing `role#assignee` through
  `RelationshipTupleWritePort`, each appending a permission audit event.
- [ ] A guard rejecting any write whose object type is not `organization` or `role`.
  Test it explicitly against a `knowledge_asset` target — Decision 7 is the one an
  agent adding "just one more endpoint" will otherwise break.
- [ ] Regenerate the contract and client.

Gate: `.\gradlew.bat :apps:api:test`.

## Phase 4 — Web

- [ ] `AccessVerdict`: the three-state atom carrying state, authority, origin,
  generation, and sync age. Every surface renders a verdict through it; no screen
  composes its own tick.
- [ ] `AccessPath`: the vertical rail for an allow — node, relation label, node —
  ending in the model rule that granted it. Plain DOM; no graph library.
- [ ] `AccessDenied`: the flat list of evaluated branches, visually separating a
  missing relation from an explicit `DENY`, and naming where the fix belongs.
- [ ] User permission profile under `web/src/features/admin`: organization
  permissions, roles (the only editable block, `command` inside `dialog`), external
  identities, reachable containers.
- [ ] Access inspector route: pick a user and a container or document, run, render
  the verdict. A "copy as diagram" action emits mermaid text for a ticket; it is not
  the primary render.
- [ ] The existing role PATCH control is relabelled as a business attribute so it
  stops reading as a grant.
- [ ] Light and dark, keyboard access, visible loading and error states.

Gate: `corepack pnpm -C web typecheck`, `corepack pnpm -C web build`, then a browser
run proving an allow path, an explicit denial, and a mirrored verdict showing its
source and sync age.
