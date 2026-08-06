# Clearance Separation — Plan

Design: [design.md](design.md). One PR. Steps ordered so every gate can run at
the narrowest useful scope while iterating.

## 1. Database + core rename

- [ ] Flyway migration `V<next>__clearance_separation.sql`:
  `app_users.role` → `clearance` (varchar, NOT NULL, CHECK
  `IN ('STANDARD','EXECUTIVE')`), backfill `EXECUTIVE`→`EXECUTIVE`, else
  `STANDARD`; same treatment for `user_invitations.role` → `clearance`
  (drop/replace the six-value CHECK).
- [ ] `UserRole` → `Clearance {STANDARD, EXECUTIVE}` in
  `core/.../organization`; update `AppUser` (field, column mapping,
  `changeRole` → `changeClearance`), `UserInvitation`,
  `UserProvisioningService` (directory default becomes `STANDARD`),
  `ScimUserDirectoryService`, `CurrentActor`.
- [ ] `KnowledgeEvidenceScopeResolver` and `SourceQueryService`: compare
  `Clearance.EXECUTIVE`; retrieval SQL untouched.
- [ ] Delete `"role"` from `AdministrativeTupleScope.WRITABLE_OBJECT_TYPES`;
  update its javadoc and the `ARCHITECTURE.md` administrative-write line.
- [ ] Gate: JVM context test (terminating clean test), migration applies on a
  fresh DB.

## 2. API surface

- [ ] `AdminUserController`: request/response rename `role` → `clearance`;
  PATCH accepts optional `departmentId` validated via
  `DepartmentRepository.existsByIdAndOrganizationId`; explicit-clear semantics
  documented; self-edit guard unchanged and now also covers department.
- [ ] `AdminInvitationController` + invitation flow renamed accordingly.
- [ ] `MeController`: `role` → `clearance`, add `departmentName` (join through
  existing repositories; null-safe).
- [ ] `UserResponse` / organization user listing renamed.
- [ ] Gate: integration tests for admin PATCH (clearance change, department
  assign/clear, cross-org department rejected, self-edit rejected), `/api/me`
  shape; regenerate `contracts/openapi.json` via OpenApiContractTests.

## 3. Web

- [ ] Regen hey-api client from refreshed contract.
- [ ] `admin-labels.ts`: two clearance values (Standard / Executive) with
  labels; remove six-value array.
- [ ] Users table: clearance select (2 values) with confirmation dialog when
  raising to Executive (state the blast radius: org-wide CONFIDENTIAL +
  RESTRICTED read access); Department column + assign control fed by the
  organization-context departments list.
- [ ] Invitations card: clearance selector (2 values) + optional department.
- [ ] Account/user menu: show own department name and clearance, read-only.
- [ ] Gate: lint, typecheck, unit tests, production build; browser check of
  the admin users flow.

## 4. Docs + consolidation

- [ ] `docs/roadmap.md`: increment active; add backlog entry for the
  publication/model compatibility gate follow-up.
- [ ] Reconcile `docs/specs/domains/identity-and-organization.md` and its test
  mirror (clearance terminology, department administration, `/api/me`);
  refresh `Source:` / `Reconciled:` lines.
- [ ] New decision entry: clearance separation, with the debate verdict and
  rejected alternative + revisit condition.
- [ ] Full gates green; move increment to completed in the merge PR only if
  the loop's post-merge verification passes.
