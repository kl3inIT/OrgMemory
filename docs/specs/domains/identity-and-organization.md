# Identity And Organization Spec

Source: `core/src/main/java/com/orgmemory/core/organization`,
`core/src/main/java/com/orgmemory/core/identityprovisioning`,
`apps/api/src/main/java/com/orgmemory/api/organization`,
`apps/api/src/main/java/com/orgmemory/api/security`,
`apps/api/src/main/java/com/orgmemory/api/scim`,
`apps/api/src/main/java/com/orgmemory/api/admin`, and
`core/src/main/resources/db/migration`.

Reconciled: `2026-08-01-spring-modulith-package-refactor (00aabe15)`.

## Current Behavior

OIDC issuer/subject is the durable external identity for both browser sessions
and bearer JWTs. An existing `external_identities` binding always wins. An
unlinked identity may consume exactly one open invitation selected by a
verified email claim; the email only selects that expectation and the durable
result is still `(issuer, subject)`. Zero or multiple eligible invitations fail
closed. Display name, email, and identity-provider roles never elevate
OrgMemory access. Unknown and inactive identities fail closed.

One Keycloak `(issuer, subject)` binds to one application user and therefore
one organization. Email uniqueness is organization-scoped. Supporting one
human in multiple organizations requires a future global-person plus membership
model; duplicating the same external subject is not supported.

The browser uses Spring as a confidential OIDC BFF. Keycloak authenticates the
user, Spring Session JDBC stores the server-side session, and React reads only
the canonical actor through `/api/session`. The browser never stores OAuth access
or refresh tokens. Browser writes require Spring Security SPA CSRF; logout is a
CSRF-protected POST followed by OIDC provider logout. The provider receives the
exact registered application redirect ending in `/login`.

Anonymous protected routes start OIDC immediately and preserve only a validated
same-application return path in the server session. `/login` remains the explicit
fallback after logout or authentication failure; absolute, protocol-relative,
backslash, and malformed return targets fall back to `/`.

Stateless bearer requests remain available for MCP, CLI, and integration clients.
The server derives user, organization, and department from the canonical actor;
client payloads cannot choose them. There is no offline/permit-all profile.
Knowledge reads then reload the active persisted subject through the
Organization-owned `KnowledgeAccessSubjectQuery`; its department and Executive
flag come from current Organization state rather than actor claims. Canonical
organization and department existence checks cross `OrganizationResourceQuery`.
Knowledge Retrieval imports no Organization entity, role, or repository.
External source-system users and groups are resolved into knowledge ACL
principals through the verified mapping ledger described in the
[knowledge ingestion spec](knowledge-ingestion.md).

An administration surface under `/api/admin/**` governs that identity layer.
Every endpoint is gated on OpenFGA `can_manage_members` against the actor's
organization. `app_users.role` is a local business/classification profile used
by application policy and presentation; it is neither a Keycloak role nor an
OpenFGA relationship and cannot grant organization or Knowledge Space access.
Administration lists internal users with
their role, activation, whether an `external_identities` row exists at all, and
how many source principals resolve to them; it changes role and activation but
never creates users, refusing self-edits so an organization cannot be locked out
of its own administration. It lists observed source principals with the tier that
mapped them, records administrator-confirmed mappings and revocations through the
existing mapping service, records the per-connection identity trust decision,
and exposes each source group's native ID plus its canonical active membership
snapshot ID and generation read-only. Administrators can create,
list, and revoke invitation expectations; first verified sign-in atomically
creates or links the application user and accepts the invitation. There is no
open registration or application-managed directory group.

The native SCIM foundation stores organization-bound provisioning connections,
hashed credential metadata, independently versioned local/directory/readiness
state, SCIM User resource identities, and append-only redacted provisioning
events. A highest-priority stateless `/scim/v2/**` security chain accepts only
the connection token, applies media-type, request-size, rate, TLS, scope,
expiry, rotation, and revocation guards, and never falls through to browser or
OIDC bearer authentication. Authenticated discovery reflects the implemented
capability registry. Product OpenAPI excludes SCIM routes and the separate SCIM
contract covers them. User and Group mutation endpoints remain disabled, and
every connection defaults disabled.

OpenAPI and Swagger are disabled by default and public only in the `dev`
profile. The committed `contracts/openapi.json` is generated from the running
application and verified against it, so the browser client cannot be generated
from a stale contract. Production configuration has mandatory environment-backed database,
OIDC, OpenFGA, object-storage, and AI settings; invalid or known local values
abort API startup before traffic is accepted.

## Source Modules

- `core.organization`
- `apps.api.security`
- `apps.api.admin`
- `core.knowledge` `SourcePrincipalAdminService`

## Invariants

- Authentication answers who the external principal is; OpenFGA answers what
  the canonical internal actor may do.
- `(issuer, subject)` is the only automatic identity lookup key.
- Verified email may select one invitation but never becomes a binding key.
- One external subject belongs to one application user and one organization.
- Application-user email uniqueness is scoped to its organization.
- Email and Keycloak roles are display/authentication claims, not authorization
  grants.
- `app_users.role` is local business/classification state, not an OpenFGA grant.
- Knowledge access reloads current active department and Executive state from
  Organization persistence; `CurrentActor` claims cannot widen it.
- Browser and bearer paths must resolve the same `CurrentActor`.
- Unknown, inactive, stale, or ambiguous identity state denies access.
- Administration is authorized by OpenFGA, never by the app role a browser reads.
- Administration resolves existing sealed grants; it never creates a grant, an
  account, or a group.
- Source email and display name are mutable observations; authorization identity
  is the typed source-native principal ID.
- SCIM credentials are organization/connection bound, stored only as verifier
  material, and valid only on the dedicated SCIM chain.
- SCIM discovery may advertise only implemented capabilities; User and Group
  mutation remains unreachable until its later gated increment.

## Related Decisions

- [0003](../../decisions/0003-postgresql-ledger-openfga-authorization.md)
- [0016](../../decisions/0016-native-scim-behind-keycloak-broker.md)
- [Identity tenant migration operations](../../guidelines/identity-tenant-migration-operations.md)
