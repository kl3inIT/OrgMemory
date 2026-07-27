# SCIM User Lifecycle Private Beta

## Outcome

One enabled organization can provision, query, update, deactivate, reactivate,
and tombstone users through a conformant SCIM 2.0 endpoint. A provisioned user
can bind safely to the Keycloak subject at first login through a trusted
immutable workforce key.

The feature remains a private beta until the live proof at the end of this
increment. No SCIM operation creates an OpenFGA grant, changes
an existing `app_users.role`, changes a Knowledge Space, or writes Source ACL
evidence. New actors are initialized server-side as `EMPLOYEE`; the SCIM client
cannot choose that value.

## Dependency

The [SCIM Provisioning Foundation](../2026-07-27-scim-provisioning-foundation/plan.md)
must be complete. Its connection credential, security chain, dual-axis
lifecycle, resource ledger, and protocol conformance harness are reused rather
than reimplemented.

## Supported User Profile

The first profile supports the RFC core User schema:

- `id`, `externalId`, `userName`;
- `name.givenName`, `name.familyName`, `name.formatted`;
- `displayName`, `title`;
- `active`;
- `emails` with a required canonical primary work email for the current
  application ledger;
- read-only `groups` only after the Groups increment.

Password provisioning is unsupported because Keycloak/upstream IdPs own
authentication. `password` is not part of OrgMemory's supported writable
profile; any supplied value is rejected with a SCIM error and is never
persisted or logged. Provider setup must disable password mapping rather than
receiving false success for a discarded secret.

The enterprise extension initially permits allowlisted profile metadata such
as `employeeNumber`, `department`, `division`, `organization`, `costCenter`,
and, only if the conformance spike proves the shape, `manager.value` and
`manager.displayName`.

These values are directory profile data. `department` does not select an
OrgMemory department foreign key. `roles`, `entitlements`, group names, and
custom JSON do not become authorization.

The primary work email requirement is an OrgMemory profile constraint, not an
identity rule. A provider that cannot supply one receives an explicit
`invalidValue`; the server never substitutes an unrelated username silently.

The server owns `id`, `meta`, lifecycle timestamps, version, and the
application-user link. It uses explicit protocol DTOs and mutability rules;
SCIM JSON is never mass-assigned to JPA entities.

## HTTP Capability

Under `/scim/v2/Users`:

- `POST /Users`;
- `GET /Users/{id}`;
- `GET /Users` with filter and pagination;
- `POST /Users/.search`;
- `PUT /Users/{id}`;
- `PATCH /Users/{id}`;
- `DELETE /Users/{id}`.

List responses use one-based `startIndex`, bounded `count`, stable ordering,
and integer `totalResults`, `itemsPerPage`, and `startIndex`. A query with no
match returns `200` and an empty SCIM `ListResponse`.

Filtering is parsed into an AST and translated to parameterized, allowlisted
queries. It covers the RFC grammar and provider profiles selected by the F1
corpus, including compound `and`, the Entra primary-email value-path form, and
Okta `userName eq`. Invalid or unsupported syntax returns `400` with
`scimType=invalidFilter`; it never falls back to an unfiltered list.

`attributes` and `excludedAttributes` shape both list and single-resource
responses. URL filter logging is redacted; `.search` is available when the
provider can avoid PII in query strings.

PATCH operations are case-insensitive and applied atomically. The server
supports `add`, `replace`, and `remove`, pathless objects, and allowlisted path
filters. If one operation fails, none of the operations commit. PUT follows an
explicit replacement/defaulting contract and cannot replace immutable fields.

The initial truthful capability profile keeps Bulk, sort, ETag, and password
change unsupported. Internal optimistic versioning still prevents lost
application updates.

## Creation And Lifecycle

SCIM POST creates:

1. one stable SCIM resource ID;
2. one application actor in the connection organization;
3. a directory-owned projection linked to that actor;
4. an append-only provisioning event.

The application actor receives `UserRole.EMPLOYEE` and no OpenFGA tuple. This
role is not accepted from SCIM. Because current retrieval code also uses the
role in classification checks, tests must prove that a newly provisioned user
does not gain `RESTRICTED` or `CONFIDENTIAL` evidence through the default. Its
OrgMemory department is null unless an administrator applies a separate
application rule later.

Connection-scoped `externalId`, workforce key, `userName`, and normalized
primary email rules are checked under concurrency. A duplicate returns the
SCIM uniqueness error and never creates a second actor. A POST does not silently
adopt an existing manually invited or already bound user by email. Such a
collision becomes an explicit conflict for administrator resolution.

`active=false` updates directory state and the effective `app_users.active`
latch in the same PostgreSQL transaction. Browser and bearer requests deny on
their next canonical-actor lookup; this does not wait for session expiry or
OpenFGA cleanup.

`active=true` changes only directory state. Local suspension still wins.

DELETE:

- records a tombstone and sanitized event;
- sets directory inactive and effective access false;
- preserves `app_users`, external bindings, ownership, and audit;
- removes the resource from list responses;
- returns `404` for later direct SCIM GET;
- does not make the server SCIM ID reusable.

A later POST with the tombstoned `externalId` or workforce key returns a
uniqueness conflict. Restoring the same workforce identity requires a separate
administrator-reviewed action that retains the original server ID and history;
automatic resurrection and identifier recycling are out of scope for beta.

## Trusted First-Login Correlation

Email matching is explicitly forbidden for SCIM login binding.

The first release requires a dedicated Keycloak broker alias and approved
protocol-mapper profile for each correlation-active OrgMemory provisioning
connection. The signed browser identity token carries three fixed claims:

- `orgmemory_directory_id`: the connection's public immutable ID;
- `orgmemory_workforce_id`: the upstream immutable object ID that the
  provisioning client sends as the SCIM workforce key, normally `externalId`;
- `orgmemory_idp_alias`: the approved Keycloak broker alias.

The complete trust tuple is:

```text
(iss, aud, azp, orgmemory_idp_alias,
 orgmemory_directory_id, orgmemory_workforce_id)
```

`iss` must be the configured Keycloak issuer. `aud` must contain the approved
OrgMemory BFF client, and `azp`, when present, must equal it. The directory ID
selects exactly one correlation-active connection, and that ledger row supplies
the organization; no token claim or request field selects an organization
independently. The IdP alias and mapper fingerprint must match the connection's
platform-approved evidence.

The claim values come from a Keycloak administrator-controlled broker/session
mapper, not a normal user-profile attribute. Validation evidence proves the
upstream field is immutable and non-user-editable, records a redacted mapper
configuration fingerprint, and becomes stale if that configuration changes.

Resolution order is:

1. Resolve an existing `(issuer, subject)` binding; it always wins.
2. Only an interactive brokered OIDC session may bootstrap a new SCIM binding;
   a bearer JWT cannot.
3. Validate the complete trust tuple and resolve its directory ID to one
   connection/organization.
4. Find exactly one active, non-tombstoned, unbound SCIM resource by connection
   and workforce key.
5. Atomically insert the external binding and re-read the winner.
6. Deny wrong audience/client, issuer, IdP alias, directory, missing/untrusted
   claim, ambiguity, inactive resource, tombstone, existing conflict, or
   concurrent mismatch.
7. Only when no SCIM-managed collision exists may the existing verified-email
   invitation flow run for an unmanaged user.

After binding, changes to email, username, display name, or workforce profile do
not change identity. A correlation-key change is an administrator-reviewed
rebind/migration event, not an ordinary SCIM PATCH.

When `externalId` is the connection's workforce key, it becomes immutable after
creation for both bound and unbound resources. PUT/PATCH attempts to change it
return the profile's mutability error.

## Administration And Recovery

The Identity & Provisioning surface shows:

- directory source and connection state;
- directory active, local access, effective access, and tombstone separately;
- linked Keycloak issuer/subject status without exposing sensitive claims;
- last successful operation and a sanitized conflict reason;
- credential health and kill switch;
- explicit collision diagnosis and safe suspend/retry-after-upstream-correction
  actions with audit.

Beta does not merge users, adopt an existing actor by email, or rebind a
workforce key from this screen. Those mutations need their own identity-proof
contract.

Before general enablement, the connection enters `VALIDATING` with one
administrator-declared workforce probe ID. In that state:

1. only one upstream-assigned probe may be created/read/updated/deleted;
2. the full User profile is advertised only after all User methods exist;
3. the same upstream object completes broker login;
4. OrgMemory compares the SCIM workforce key with the signed trust tuple;
5. the operator records provider mapping, Keycloak mapper/read-only evidence,
   token audience/client, timestamp, and configuration fingerprints;
6. the probe is tombstoned or explicitly retained without application grants;
7. configuration becomes `VALIDATED`, while operational state returns to
   `DISABLED` until the pilot is approved.

Before enabling one organization:

- a non-SCIM recovery administrator must be tested;
- the correlation probe must pass;
- the connection starts with a bounded pilot allowlist;
- mass deactivation alerts are configured;
- connection suspension and invitation fallback are rehearsed.

Terminations are never blocked merely because the user is the last SCIM-managed
administrator. Revocation wins; the recovery operator and alert provide
recovery.

## Rollback

Set the connection `READ_ONLY` to stop writes during an operational rollback,
or `SUSPENDED` to deny all SCIM credentials during a security incident.
Preserve the ledger and tombstones and roll back the binary without a down
migration. The compatibility `active` latch continues to deny
SCIM-deactivated users.

Invitation onboarding stays deployed throughout the beta. It is fallback for
unmanaged identities, not an email-based recovery path for a SCIM resource.
Rollback evidence includes create, deactivate, binary rollback, login denial,
forward redeploy, and controlled reactivation.
