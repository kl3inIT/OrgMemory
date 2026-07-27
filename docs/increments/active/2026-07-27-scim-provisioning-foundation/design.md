# SCIM Provisioning Foundation

## Outcome

OrgMemory gains a disabled-by-default, tenant-bound SCIM machine-identity
boundary, a durable provisioning ledger, truthful discovery endpoints, and
administrator credential lifecycle.

No SCIM User or Group mutation is enabled at the end of this increment.

## Dependency

The [Identity Tenant Hardening](../2026-07-27-identity-tenant-hardening/plan.md)
increment and the architecture gate in
[ADR 0016](../../../decisions/0016-native-scim-behind-keycloak-broker.md)
must be complete.

## Module Boundary

The provisioning domain belongs in a cohesive Spring Modulith package such as
`core.identityprovisioning`. It owns connections, SCIM resources, lifecycle,
credentials, and audit intent. It may depend on the organization module's
public API for application actors, but protocol DTOs and Spring Security types
do not enter the core.

`apps.api.scim` is the inbound SCIM adapter. The existing `/api/**` bearer chain
and browser BFF remain unchanged. A later worker adapter owns durable OpenFGA
convergence; the SCIM HTTP request never commits PostgreSQL and OpenFGA as
though they were one transaction.

## Protocol Dependency Spike

The first PR compares the current Ping Identity SCIM 2 SDK and Apache SCIMple
against a small executable conformance corpus. The selection gate is:

- Spring Boot 4, Jakarta, and Jackson 3 compatibility;
- RFC filter parser and AST access;
- PATCH path coverage needed by Entra and Okta;
- ability to use Spring MVC without embedding a second JAX-RS runtime;
- bounded parsing and explicit attribute allowlists;
- active maintenance, license compatibility, dependency size, and known CVEs.

If neither candidate passes, OrgMemory implements only the thin protocol shell
and uses a generated/parser-library grammar. It does not replace SCIM filters
with regular expressions.

The spike is a mergeable test harness and decision record, not throwaway
production code.

## Reference Implementation Boundary

Onyx is useful evidence for SCIM-specific error/media handling, hash-only
one-time credentials, and separate User/Group correlation records. OrgMemory
does not copy its partial filter parser, automatic basic-access grant from a
directory group, deletion of correlation evidence, or unauthenticated discovery
behavior. Those choices conflict with this program's tenant, authorization, and
tombstone boundaries.

The same spike runs Keycloak 26.7 SCIM in an isolated realm as the strongest
alternative. It records:

- the Keycloak user ID created by SCIM and the subject emitted after broker
  login;
- whether supported Keycloak broker/session mappers can emit the fixed
  non-user-editable directory ID, upstream IdP alias, and immutable workforce
  ID claims to the OrgMemory client;
- whether one credential can be constrained to one OrgMemory organization in a
  shared realm;
- deactivate versus delete/recreate subject behavior;
- required realm-management permissions;
- behavior when the preview feature is disabled or the server is upgraded.

This evidence may justify a future Keycloak adapter. It cannot become the
production provisioning path without a new accepted decision.

If the required signed correlation claims need an unversioned custom Keycloak
SPI rather than supported configuration, implementation pauses for a separate
deployment/upgrade decision; the plan does not hide that dependency in U0.

## Provisioning Ledger

The next available migrations introduce at least:

### `identity_provisioning_connections`

- stable UUID and organization composite ownership;
- public connection alias used only as an identifier, never as a secret;
- configuration status: `DRAFT` or `VALIDATED`;
- operational state: `DISABLED`, `VALIDATING`, `ENABLED`, `READ_ONLY`, or
  `SUSPENDED`;
- provider profile and enabled SCIM resource types;
- fixed trusted correlation-claim contract and approved Keycloak
  realm/client mapper fingerprint;
- correlation-probe status and timestamps;
- optimistic version and audit timestamps.

The first release allows one enabled workforce connection per organization.
That limit avoids ambiguous correlation while preserving a schema that can
support more later.

Configuration status, operational state, and rollout maturity are independent.
`VALIDATED` means the provider/Keycloak correlation configuration was proven.
`READ_ONLY` and `SUSPENDED` control current traffic. `PILOT`,
`LIMITED_AVAILABILITY`, and `GENERAL_AVAILABILITY` are certification evidence,
not connection lifecycle values.

### `identity_provisioning_credentials`

- connection and organization ownership;
- globally unique non-secret public token ID;
- verifier and verifier-key version, never the raw secret;
- method scopes (`users`, later `groups`);
- created, expiry, revoked, overlap, and last-used timestamps;
- creator/revoker audit references.

The token format separates a public lookup ID from at least 256 bits of random
secret material. Verification uses a deployment-managed keyed verifier (or an
equivalently reviewed construction), a constant-time comparison, and per-token
rate limits. The raw token is shown once.

### `scim_user_resources`

- stable SCIM resource UUID distinct from `app_users.id`;
- connection and organization ownership;
- application-user link;
- connection-scoped `external_id` and normalized `user_name`;
- normalized primary email and allowlisted profile attributes;
- directory lifecycle, tombstone, internal version, and timestamps;
- trusted workforce correlation key in a connection namespace;
- no raw payload or arbitrary extension JSON.

Unique constraints cover resource ID and connection-scoped `externalId`,
`userName`, and workforce key. The server ID is never reassigned. A tombstoned
workforce key or `externalId` remains reserved in the first release; restoring
the same person is a separate audited operation, not an automatic POST
side-effect.

### `identity_provisioning_events`

Append-only sanitized events record connection, operation, outcome, request
correlation ID, resource ID, credential public ID, and an allowlisted set of
changed field names. Raw tokens, passwords, request bodies, filters containing
PII, and attribute values are never stored.

## Access State Compatibility

SCIM directory lifecycle and local emergency suspension are independent:

```text
effective_active =
  local_access_enabled
  AND coalesce(directory_access_enabled, true)
  AND provisioning_access_ready
```

`directory_access_enabled` is null for unmanaged invitation users.
`provisioning_access_ready` defaults true until authorization mappings exist.

The existing `app_users.active` remains the materialized compatibility latch
read by old and new current-actor code. A database trigger maintains it. If an
old binary changes only `active`, the trigger interprets that as a local access
change, then recomputes the effective value. It can never turn a
directory-disabled user back on.

This behavior is covered by forward-upgrade and previous-binary rollback tests
before any connection is enabled.

## Cross-Feature State Contract

| Object | Durable state | Security transition | Delete/rollback behavior |
| --- | --- | --- | --- |
| Connection | Configuration plus operational state | `DISABLED` allows discovery only; `VALIDATING` allows one User probe; `ENABLED` allows its scoped profile; `READ_ONLY` rejects mutations; `SUSPENDED` rejects all SCIM credentials | Preserve ledger; recover through `/api/admin/**` |
| User | Directory, local, readiness, effective, tombstone | Any false access axis denies on the next actor lookup | Tombstone SCIM view; retain actor, binding, ownership, audit |
| Group | Active/tombstone plus versioned direct memberships | No access effect without an active mapping | Tombstone memberships; mapped Groups revoke before completion |
| Mapping | Draft, active, revoking, suspended, tombstoned | Reductions add per-user access blockers before tuple cleanup | Retain ownership/outbox until verified revoke |

`provisioning_access_ready` initially means that no blocking projection work
exists. When mappings ship, it is derived from the absence of unresolved
per-user access blockers. One completed outbox item cannot reactivate a user
while another blocking revocation remains.

## SCIM Security Boundary

A highest-priority security chain matches `/scim/v2/**`:

- stateless, no browser session or cookie authentication;
- CSRF disabled only for this chain;
- bearer connection credential authentication;
- tenant and connection derived only from the verified credential;
- TLS required outside test/development;
- bounded body, page count, filter depth/nodes, PATCH operations, and group size;
- per-connection rate limiting with `429` and `Retry-After`;
- no request-body or PII-bearing query logging.

Authentication failures are generic. A credential from organization A cannot
learn whether a resource, count, external ID, username, or connection exists in
organization B.

The connection and credential tables enforce, rather than merely document:

- global uniqueness of the public token lookup ID;
- one correlation-active connection per organization through a partial unique
  index;
- compare-and-set or row-locked operational-state transitions;
- current verifier-key version presence at production startup.

The dispatch boundary rejects token confusion in both directions: a SCIM token
cannot authenticate `/api/**`, while an OIDC JWT, browser cookie, or CSRF token
cannot authenticate `/scim/v2/**`.

## Truthful Discovery And Contract

Authenticated endpoints provide:

- `/scim/v2/ServiceProviderConfig`;
- `/scim/v2/ResourceTypes`;
- `/scim/v2/Schemas`.

They use `application/scim+json` and the RFC SCIM Error schema. Discovery is
derived from enabled, tested capabilities. No User ResourceType is advertised
until the complete User method/lifecycle profile and invitation guard exist;
the full profile is visible first only in bounded `VALIDATING` mode and then to
an approved `ENABLED` connection. Groups remain hidden until their complete
membership profile exists.

Initial planned values are:

- `filter.supported=true` only after the parser and User query path ship;
- `patch.supported=true` only after atomic PATCH ships;
- `bulk.supported=false`;
- `sort.supported=false`;
- `etag.supported=false` while internal optimistic locking remains private;
- `changePassword.supported=false`.

SCIM uses a separate generated/snapshotted contract such as
`contracts/scim-openapi.json`. The product `contracts/openapi.json` continues
to drive the browser client and remains limited to `/api/**`.

## Administrative Surface

An organization administrator can:

- create a disabled connection;
- view its endpoint and capability state;
- create a credential and copy the token once;
- rotate with a bounded overlap;
- revoke immediately;
- place a connection in `READ_ONLY` to freeze mutations during diagnosis;
- place it in `SUSPENDED`, or revoke its credentials, to deny every SCIM
  request after a security incident;
- see last use and sanitized authentication/protocol failures.

Every credential/connection administration endpoint derives the organization
from `CurrentActor` and requires OpenFGA `can_manage_members`. A SCIM token
cannot call those browser administration endpoints.

General enablement is unavailable until tenant-isolation tests, a trusted
correlation probe, and a non-SCIM recovery administrator are recorded.
`VALIDATING` is reserved for one allowlisted probe resource and is defined by
the User increment. There is no Group or authorization mapping UI in this
increment.

## Rollback

- All schema is additive and retained.
- Connections default to `DISABLED`.
- `READ_ONLY` stops mutation without deleting resources, events, or
  credentials. `SUSPENDED` denies all SCIM credential use while browser
  administrators retain recovery through `/api/admin/**`.
- The previous binary starts against the expanded schema and continues to deny
  any user whose compatibility `active` latch is false.
- Raw credentials cannot be recovered after creation; rollback rotates or
  revokes them rather than exporting them.
- No down migration drops the provisioning ledger.
