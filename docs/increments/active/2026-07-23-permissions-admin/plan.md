# Permissions Admin Plan

Three phases, each a commit that leaves the build green.

## Phase 1 — Ledger and core facade

1. `V20__source_connection_identity_trust.sql`: `source_connections` keyed by
   `(organization_id, source_system, source_connection_key)` with
   `identity_trust`, the deciding user, and the decision timestamp.
2. `SourceIdentityTrust` enum, `SourceConnection` entity, repository.
3. `AppUser` mutators: `activate()`, `deactivate()`, `changeRole(UserRole)`.
4. `SourcePrincipalAdminService` (public) with public view records:
   principals with mapping and resolved user, connections with observed counts,
   groups with latest sealed membership, `confirmMapping`, `revokeMapping`,
   `setIdentityTrust`.
5. Group membership query: for each `SOURCE_GROUP` principal, members from the
   most recently sealed snapshot that carries it.

Gate: `.\gradlew.bat :core:compileJava`.

## Phase 2 — Admin API

1. `AdminAccessGuard` resolving the actor and asserting OpenFGA
   `can_manage_members` on the organization, throwing
   `OrgMemoryAccessDeniedException` (403 via the existing handler).
2. `AdminUserController` — list, patch role/activation, refuse self-edit.
3. `AdminSourceAccessController` — connections, identity trust, principals,
   confirm, revoke, groups.
4. `role` added to `SessionResponse` and `MeResponse`, read from `AppUser`.
5. Regenerate `contracts/openapi.json` and `pnpm -C web gen:api`.

Gate: `.\gradlew.bat :apps:api:compileJava` then the API test slice.

## Phase 3 — Admin web area

1. `routes/_authenticated/admin/route.tsx`: admin layout, `ADMIN` role guard,
   redirect to `/` otherwise; sidebar group `Permissions`.
2. Pages: `users.tsx`, `mappings.tsx`, `groups.tsx`, `scim.tsx`.
3. `features/admin/`: query options, view-model mapping, and the shared table /
   empty / error composition reused by all pages.
4. Account-menu entry rendered only for `ADMIN`.
5. Reference `tmp/onyx` for layout language only; primitives stay shadcn.

Gate: `pnpm -C web lint`, `typecheck`, `build`, `check:api`.

## Phase 4 — Proofs and consolidation

1. `PermissionsAdminIntegrationTests` in `apps/api`:
   - non-admin gets 403 on every admin endpoint;
   - an unmapped principal denies retrieval, `confirmMapping` makes the same
     query return the evidence, `revokeMapping` takes it away;
   - `setIdentityTrust` persists and is readable.
2. Full `.\gradlew.bat test`.
3. Consolidate into `docs/specs`, `docs/tests`, `ARCHITECTURE.md`; move the
   increment to `docs/increments/completed`.
