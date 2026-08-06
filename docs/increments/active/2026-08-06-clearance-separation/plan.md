# Clearance Separation — Plan

Design: [design.md](design.md). One PR. Steps ordered so every gate can run at
the narrowest useful scope while iterating.

## 1. Database + core rename

- [x] Flyway migration `V<next>__clearance_separation.sql`:
  `app_users.role` → `clearance` (varchar, NOT NULL, CHECK
  `IN ('STANDARD','EXECUTIVE')`), backfill `EXECUTIVE`→`EXECUTIVE`, else
  `STANDARD`; same treatment for `user_invitations.role` → `clearance`
  (drop/replace the six-value CHECK).
- [x] `UserRole` → `Clearance {STANDARD, EXECUTIVE}` in
  `core/.../organization`; update `AppUser` (field, column mapping,
  `changeRole` → `changeClearance`), `UserInvitation`,
  `UserProvisioningService` (directory default becomes `STANDARD`),
  and `CurrentActor`.
- [x] `JpaKnowledgeAccessSubjectQuery`: compare `Clearance.EXECUTIVE` when it
  constructs the persisted active subject; retrieval SQL untouched.
- [x] Delete `"role"` from `AdministrativeTupleScope.WRITABLE_OBJECT_TYPES`;
  update its javadoc and the `ARCHITECTURE.md` administrative-write line.
- [x] Gate: JVM context test (terminating clean test), migration applies on a
  fresh DB.

## 2. API surface

- [x] `AdminUserController`: request/response rename `role` → `clearance`;
  PATCH accepts optional `departmentId` validated via
  `DepartmentRepository.existsByIdAndOrganizationId`; explicit-clear semantics
  documented; self-edit guard unchanged and now also covers department.
- [x] `AdminInvitationController` + invitation flow renamed accordingly.
- [x] `MeController`: `role` → `clearance`, add `departmentName` (join through
  existing repositories; null-safe).
- [x] `UserResponse` / organization user listing renamed.
- [x] Gate: integration tests for admin PATCH (clearance change, department
  assign/clear, cross-org department rejected, self-edit rejected), `/api/me`
  shape; regenerate `contracts/openapi.json` via OpenApiContractTests.

## 3. Web

- [x] Regen hey-api client from refreshed contract.
- [x] `admin-labels.ts`: two clearance values (Standard / Executive) with
  labels; remove six-value array.
- [x] Users table: clearance select (2 values) with confirmation dialog when
  raising to Executive (state the blast radius: org-wide CONFIDENTIAL +
  RESTRICTED read access); Department column + assign control fed by the
  organization-context departments list.
- [x] Invitations card: clearance selector (2 values) + optional department.
- [x] Account/user menu: show own department name and clearance, read-only.
- [x] Gate: lint, typecheck, unit tests, production build; browser check of
  the admin users flow.

## 4. Docs + consolidation

- [x] `docs/roadmap.md`: increment active; add backlog entry for the
  publication/model compatibility gate follow-up.
- [x] Reconcile `docs/specs/domains/identity-and-organization.md` and its test
  mirror (clearance terminology, department administration, `/api/me`);
  refresh `Source:` / `Reconciled:` lines.
- [x] New decision entry: clearance separation, with the debate verdict and
  rejected alternative + revisit condition.
- [x] Full gates green; move increment to completed in the merge PR only if
  the loop's post-merge verification passes.

## Blockers

- The second database/core checklist item names `ScimUserDirectoryService`,
  but no such class or symbol exists on this branch. The implemented directory
  default is owned by `UserProvisioningService`; all existing `UserRole`
  production and fixture references were migrated there and at their actual
  call sites.
- The retrieval checklist names `KnowledgeEvidenceScopeResolver` and
  `SourceQueryService` as the two Executive comparisons. On current repository
  code neither class constructs that flag. The single live comparison is
  centralized in the Organization-owned `JpaKnowledgeAccessSubjectQuery` and
  was renamed there. `SecureKnowledgeRetrievalStore` remains byte-identical.
