# Identity Tenant Hardening

## Outcome

The existing invitation and OIDC identity path becomes tenant-safe and
race-safe before any SCIM schema or endpoint is introduced.

This is Increment 0 of the
[native SCIM program](../../../decisions/0016-native-scim-behind-keycloak-broker.md).
It changes no authentication provider and exposes no provisioning protocol.

## Why It Must Land First

The current baseline contains identity assumptions that become dangerous under
automated provisioning:

- `lower(app_users.email)` is globally unique, while a user belongs to an
  organization;
- existing-user lookup during invitation provisioning is global by email;
- an invitation from organization B can encounter an existing user from
  organization A;
- department, inviter, and accepted-user references do not all prove tenant
  equality at the database boundary;
- invitation creation does not consistently prove that the selected department
  belongs to the invitation organization;
- `ExternalIdentityRepository.linkIfAbsent` uses `ON CONFLICT DO NOTHING`, and
  its caller accepts the invitation without re-reading and validating the
  winning `(issuer, subject)` binding.

SCIM would turn these edge cases into repeatable cross-tenant or concurrency
failures. This increment repairs the existing seam first.

## Identity Cardinality

The first SCIM release preserves a deliberate product invariant:

> One Keycloak `(issuer, subject)` binds to one OrgMemory application actor and
> therefore one organization.

Email is not a durable identity and may appear in different organizations.
Email uniqueness becomes `(organization_id, normalized_email)`. The global
`(issuer, subject)` uniqueness remains.

If a future requirement allows one human to join multiple OrgMemory
organizations, the correct change is a global person/external-identity record
plus organization memberships. Duplicating the same subject into multiple
`app_users` rows is not an accepted shortcut.

## Tenant Integrity Migration

Use the next available Flyway versions at merge time; `V8` is next in the
current branch. The rollout is an expand/contract sequence:

1. Run explicit preflight queries for duplicate normalized emails within one
   organization and for cross-tenant department/invitation references.
2. Verify and reuse the existing `(id, organization_id)` unique keys on
   `departments` and `app_users`.
3. Add organization-scoped composite foreign keys for:
   - `app_users(department_id, organization_id)`;
   - invitation department;
   - invitation inviter;
   - invitation accepted user.
4. Add the composite foreign keys as `NOT VALID`, validate them, then remove
   the weaker single-column foreign keys.
5. Deploy organization-scoped repository/service readers while retaining the
   old global email unique index. The pre-H1 binary remains rollback-compatible
   during this expand window.
6. After the new reader artifact is deployed, soaked, and designated as the
   minimum rollback binary, replace the global email index with an
   organization-scoped normalized-email unique index in a separate contract
   PR.

The migration aborts with an actionable diagnostic if existing data violates
the target invariant. It never guesses a tenant or rewrites ownership.

## Service And Repository Rules

- Every email/user lookup used by an organization-owned flow accepts the
  organization ID explicitly. This includes invitation provisioning and
  trusted source-principal email mapping; removing global email uniqueness
  without scoping both callers would create a new ambiguity.
- Invitation creation verifies the organization of department and inviter
  before persistence, even though the database also enforces it.
- Invitation acceptance locks or otherwise serializes the invitation and
  identity winner inside one transaction.
- External-identity linking returns the actual winning binding. The service
  verifies both that `(issuer, subject)` points to the intended app user and
  that the app user is not bound to a different subject for the same issuer.
- A unique-constraint race is translated into a deterministic conflict or a
  verified idempotent success; it is never silently accepted.
- Verified email remains only the invitation matching mechanism. The durable
  result is still `(issuer, subject)`.
- If the same verified email has valid pending invitations in more than one
  organization, first login fails as ambiguous. The server never chooses an
  organization from ordering or a client-supplied hint.

## Invitation And Future SCIM Precedence

This increment records the future coexistence rule without implementing SCIM:

1. an existing external binding always wins;
2. a future trusted SCIM workforce-key match is evaluated next;
3. invitation provisioning is allowed only when it would not collide with a
   SCIM-managed application user;
4. no path adopts or merges a SCIM user by email.

The current identity spec incorrectly says invitations do not exist. The final
PR in this increment corrects the current-fact spec and test matrix before SCIM
design becomes their consumer.

## Rollback

The expand migration is additive until validation has completed. A pre-deploy
duplicate/cross-tenant report is retained with the release evidence.

The pre-H1 binary remains a valid rollback target only while global email
uniqueness remains. Before the contract PR permits duplicate emails across
organizations, the H2 artifact becomes the recorded rollback floor and is
tested against a fixture that already contains such duplicates. Rolling back
further would make the old global `findByEmailIgnoreCase` reader ambiguous and
is therefore unsupported; recovery is roll-forward or database restore.

The composite constraints and per-organization index remain in place during
binary rollback. No down migration removes constraints or rewrites identity
bindings.

## Completion Proof

- Two organizations may contain the same normalized email without colliding.
- One organization cannot contain two normalized copies of that email.
- A department, inviter, or accepted user from another organization is rejected
  by both service validation and PostgreSQL.
- Two concurrent first-login attempts produce one verified binding and one
  invitation acceptance.
- A conflicting `(issuer, subject)` never accepts the wrong invitation.
- Two valid invitations for the same email in different organizations fail
  closed without accepting either.
- Source-principal email mapping can resolve only a user in the source
  connection's organization.
- Existing browser and bearer login behavior remains green.
