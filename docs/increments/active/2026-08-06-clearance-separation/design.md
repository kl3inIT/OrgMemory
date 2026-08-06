# Clearance Separation — Design

## Intent

`app_users.role` mixes three unrelated concepts in one enum: a dead action-role
label (`ADMIN` — real action permissions are OpenFGA relations), HR-style titles
nobody reads (`TEAM_LEAD`, `MANAGER`, `DIRECTOR`), and the one live bit —
`role == EXECUTIVE` — which is a data-clearance attribute feeding the
classification gates in secure retrieval. The admin UI presents all six as one
innocuous dropdown, so an administrator can believe they granted administration
(they did not) or not realize they widened CONFIDENTIAL/RESTRICTED read access
org-wide (they did).

This increment renames the live bit to what it is, deletes the dead labels, and
closes two adjacent gaps that share the same authorization axis: administrators
cannot assign a user's department at all (a user provisioned without one can
never read CONFIDENTIAL evidence and no surface can repair it), and users
cannot see the department/clearance that governs their own retrieval.

## Decision (architecture challenge: two-architect debate, 2026-08-06)

The independent challenge requirement was satisfied by a structured
two-architect debate (Claude subagent defending minimal separation vs Codex
gpt-5.6-sol ultra defending a generic model), judged by a no-tools session from
the record alone. Full record: `tmp/role-model-debate/` in the session
workspace (brief, two rounds each side, verdict).

**Committed outcome — Position A, minimal separation:**

- `app_users.role` becomes clearance with exactly two closed values
  `STANDARD | EXECUTIVE`, database CHECK-constrained. Never an open integer
  column: closed enums fail closed (unknown value cannot materialize), an
  integer mis-seed fails open.
- The four dead labels are deleted. HR titles, if ever wanted, are display-only
  data outside authorization.
- The seven OpenFGA organization relations stay static and release-pinned per
  decision 0017. Zero model-bytes change in this increment.
- The dead `"role"` allowance in `AdministrativeTupleScope.WRITABLE_OBJECT_TYPES`
  is deleted, with the matching `ARCHITECTURE.md` administrative-write
  confinement line corrected. Git history (`76972a71`) shows the FGA `type role`
  was deliberately removed for tenant-scoping; the surviving string is not
  design intent.

**Rejected alternative (recorded fairly):** numeric
`clearance_level >= classification_level` backed by a governed level table,
plus dynamic customer-defined roles via an FGA `type role` with `role#assignee`
accepted by organization permissions and a tenant-owned Postgres catalog
projected through the outbox pattern. Rejected now because: any model rotation
blacks out APPLIED publications until the convergence sweep re-stamps them and
the shipped deployment has no zero-drift gate; the catalog is a second
Postgres↔OpenFGA convergence boundary priced at convergence-service scale; the
additive widening forces no tuple migration, so deferral is near-free — it can
ride along with any future product-driven model rotation. **Revisit condition:**
first concrete customer requirement for admin-defined role bundles. If a new
clearance tier is requested before then, extend the closed ordered enum first.

**Standing follow-up (recorded in roadmap backlog, out of scope here):**
publication validity is pinned to the raw model byte stream
(`publication.authorization_model_id` equality in
`SecureKnowledgeRetrievalStore`), so any legitimate model change browns out
semantically unchanged evidence until convergence. An explicit
compatibility/convergence rollout gate is a prerequisite for any future model
evolution, including the rejected alternative.

## Scope

1. **Clearance rename (behavior-preserving).**
   - Flyway: `app_users.role` → `app_users.clearance` with CHECK
     `IN ('STANDARD','EXECUTIVE')`; backfill `EXECUTIVE→EXECUTIVE`, all other
     values → `STANDARD`. `user_invitations.role` (six-value CHECK at
     `V1__baseline.sql:1399`) migrates the same way.
   - Java: `UserRole` → `Clearance` (two values). The only behavioral reads —
     `KnowledgeEvidenceScopeResolver` and `SourceQueryService` building
     `actorExecutive` — compare against `Clearance.EXECUTIVE`. Retrieval SQL in
     `SecureKnowledgeRetrievalStore` is untouched.
   - `KnowledgePermissionPolicy` / `KnowledgeRole` (eval-dataset mirror) keep
     their four-branch decision table bit-identical; only the subject attribute
     naming follows.
   - Delete `"role"` from `AdministrativeTupleScope`; fix the
     `ARCHITECTURE.md` line documenting administrative-write confinement.
2. **Department administration.** `AdminUserController` PATCH additionally
   accepts `departmentId` (validated against the organization's departments;
   `null`/absent keeps, explicit clear allowed). Admin users table shows a
   Department column (names from the existing organization-context endpoint).
3. **Self-visibility.** `/api/me` adds `departmentName` (and renames `role` →
   `clearance`); the account/user-menu surface displays department and
   clearance read-only.
4. **Contracts/UI.** `contracts/openapi.json` regenerated; web enum shrinks to
   two labeled values with a confirmation dialog when raising to Executive
   (one click currently widens org-wide CONFIDENTIAL + RESTRICTED read access).

Out of scope: audit events for clearance changes (existing audit surface
unchanged), SCIM mapping changes (directory profile fields remain descriptive
only), any OpenFGA model change, the compatibility-gate follow-up.

## Constraints

- `ddl-auto=validate`: schema change is Flyway-only, additive-then-cutover not
  required — the rename is a single transactional migration; no dual-run.
- Self-edit guard stays: an administrator cannot change their own clearance,
  activation, or department.
- Fail-closed invariants preserved: null/unknown clearance denies; missing
  department still denies CONFIDENTIAL (that is the point of making it
  assignable).
- Spec/test pair `docs/{specs,tests}/domains/identity-and-organization.md`
  reconciled in this increment; `docs/roadmap.md` marks the increment active.
