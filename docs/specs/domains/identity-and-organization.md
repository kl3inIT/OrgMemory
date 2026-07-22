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
External source-system users/groups are not yet resolved into knowledge ACL
principals.

OpenAPI and Swagger are disabled by default and public only in the `dev`
profile. Production configuration has mandatory environment-backed database,
OIDC, OpenFGA, object-storage, and AI settings; invalid or known local values
abort API startup before traffic is accepted.

## Source Modules

- `core.organization`
- `apps.api.security`

## Invariants

- Authentication answers who the external principal is; OpenFGA answers what
  the canonical internal actor may do.
- `(issuer, subject)` is the only automatic identity lookup key.
- Email and Keycloak roles are display/authentication claims, not authorization
  grants.
- Browser and bearer paths must resolve the same `CurrentActor`.
- Unknown, inactive, stale, or ambiguous identity state denies access.

## Related Decisions

- [0003](../../decisions/0003-postgresql-ledger-openfga-authorization.md)
