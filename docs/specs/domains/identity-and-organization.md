# Identity And Organization Spec

## Current Behavior

OIDC issuer/subject is the durable external identity for both browser sessions
and bearer JWTs. The binding must already exist in `external_identities`; email,
display name, and identity-provider roles never create or elevate an OrgMemory
identity. Unknown and inactive identities fail closed.

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
External source-system users and groups are resolved into knowledge ACL
principals through the verified mapping ledger described in the
[knowledge ingestion spec](knowledge-ingestion.md).

An administration surface under `/api/admin/**` governs that identity layer.
Every endpoint is gated on OpenFGA `can_manage_members` against the actor's
organization; the app role carried by `/api/session` and `/api/me` is a browser
rendering hint and never a boundary. Administration lists internal users with
their role, activation, whether an `external_identities` row exists at all, and
how many source principals resolve to them; it changes role and activation but
never creates users, refusing self-edits so an organization cannot be locked out
of its own administration. It lists observed source principals with the tier that
mapped them, records administrator-confirmed mappings and revocations through the
existing mapping service, records the per-connection identity trust decision, and
exposes sealed source-group membership read-only. There is no in-application
invitation, registration, or application-managed group: accounts come from the
identity provider and group membership is fixed at ACL seal time. SCIM
provisioning is not implemented.

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
- Email and Keycloak roles are display/authentication claims, not authorization
  grants.
- Browser and bearer paths must resolve the same `CurrentActor`.
- Unknown, inactive, stale, or ambiguous identity state denies access.
- Administration is authorized by OpenFGA, never by the app role a browser reads.
- Administration resolves existing sealed grants; it never creates a grant, an
  account, or a group.

## Related Decisions

- [0003](../../decisions/0003-postgresql-ledger-openfga-authorization.md)
