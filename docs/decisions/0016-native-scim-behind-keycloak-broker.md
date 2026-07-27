# 0016 — Native SCIM Behind The Keycloak Broker

## Status

Accepted on 2026-07-27 by explicit project-owner direction.

The repository-required Claude Fable 5 debate was attempted first, but the
available account had reached its monthly spend limit. Independent enterprise
reader and dependency audits strengthened the proposal. After reviewing the
resulting boundary and the hosted/self-hosted tenancy model, the project owner
explicitly directed H1 implementation to proceed without waiting for Fable.
That debate remains useful follow-up evidence, but it is no longer an
implementation gate for this program.

## Context

OrgMemory currently authenticates browser and bearer users through one
Keycloak issuer. A durable `external_identities` row binds that issuer and
subject to one stable `app_users` actor. OpenFGA owns application
relationships, while sealed source ACL generations own source-system
visibility.

Enterprise identity providers and IGA products also need to create, update,
disable, and group application users. Keycloak 26.7 exposes a SCIM API, but
Keycloak documents it as a preview feature that is disabled by default. It is
realm-scoped, while OrgMemory organizations currently share one realm. Making
it the production provisioning authority would therefore couple OrgMemory to a
preview contract and give a provisioning credential a larger realm-level blast
radius than one OrgMemory organization.

The current identity ledger also has prerequisites that must be fixed before
any SCIM endpoint is exposed:

- email uniqueness and some lookups are global even though invitations and
  departments are organization-owned;
- several organization-owned foreign keys are not composite tenant foreign
  keys;
- the current insert-on-conflict external-identity link does not prove which
  user won a concurrent conflict;
- a SCIM-created user has no safe way to discover the future Keycloak
  `(issuer, subject)`;
- the product has no application directory-group aggregate.

## Proposal

Use separate authorities instead of naming one global source of truth.

| Concern | Authority |
| --- | --- |
| Employment, directory profile, directory lifecycle, directory membership | Upstream IdP or IGA |
| OIDC/SAML federation, MFA, Keycloak subject, browser session | Keycloak |
| Stable application actor, tenant membership, provisioning projection, local suspension, audit | OrgMemory PostgreSQL ledger |
| Current business/classification profile (`app_users.role`) | OrgMemory administrator ledger; never SCIM |
| Organization roles and Knowledge Space relationships | OpenFGA |
| Source-system document visibility | Latest complete sealed Source ACL |

Effective access is their conjunction, not a choice between authorities:

```text
tenant and effective lifecycle
  AND OpenFGA application relationship
  AND classification/policy checks
  AND latest complete sealed Source ACL
```

PostgreSQL owns actor state plus provisioning desired state and tuple ownership.
OpenFGA is the application relationship decision point. Source ACL is a hard
visibility ceiling: it can remove otherwise valid access but cannot grant
application access by itself.

OrgMemory will implement a tenant-bound SCIM 2.0 service provider under
`/scim/v2`. Each provisioning connection and credential belongs to exactly one
organization. Tenant ownership is derived only from that authenticated
credential, never from a path, header, query, or payload field.

`Organization` is the native tenant boundary in every deployment topology; it
is not copied from a Keycloak claim. Hosted SaaS may have many organizations in
one shared-table database. Dedicated enterprise and customer self-hosted
deployments default to one organization in one stack. Bootstrap creates that
organization and initial administrator, and single-organization mode hides
organization creation/switching without removing `organization_id` from the
ledger. A generated database identity is used; no organization UUID is
hardcoded in application code.

Tenant-owned writes assign `organization_id` explicitly from a trusted
application context, provisioning credential, or verified parent resource.
Controllers never accept ownership from a client payload. Organization-scoped
repositories and composite foreign keys are the primary enforcement. A future
PostgreSQL row-level-security layer may add defense in depth after interactive
requests, SCIM, workers, outbox processing, and migrations have a proven
transaction-local tenant context; it is not a substitute for explicit
ownership.

Keeping one Keycloak issuer does not hardcode one workforce provider. Keycloak
may broker Entra ID, Okta, or another upstream IdP, while OrgMemory accepts
provider-profiled SCIM connections. The first release permits one enabled
workforce provisioning connection per organization to keep login correlation
unambiguous; the ledger is connection-scoped so a later multi-directory design
does not require global provider columns.

For the first production release, one Keycloak `(issuer, subject)` may belong
to one OrgMemory organization. Email uniqueness becomes organization-scoped,
but the external subject remains globally bound to one application actor. A
future multi-organization membership requirement must first separate a global
person/external identity from tenant memberships in another ADR.

A trusted, tenant-scoped SCIM request may create a new application user or
adopt exactly one unmanaged user with the same normalized organization email.
Any matching open invitation is consumed in the same transaction. Email is a
brownfield and first-sign-in correlation attribute, not the durable identity:
an existing `(issuer, subject)` binding always wins, and the first verified
email match writes that binding for every later login. Ambiguous matches fail
closed.

Connections may additionally define an immutable workforce correlation key and
have Keycloak emit the same value in an administrator-controlled signed claim.
That mode is preferred for high-assurance deployments but is not mandatory for
the first verified-email profile. One application user can belong to at most
one SCIM provisioning authority; a second connection must not adopt it by
email.

Directory groups are a new identity-provisioning aggregate. They are neither
departments nor sealed Source Groups. Creating a group or changing membership
does not grant application access. A later increment may map an immutable
directory-group ID to an allowlisted organization role or Knowledge Space
relationship through an audited, durable OpenFGA projection. Group names never
become authorization instructions.

SCIM `DELETE` tombstones the SCIM resource and disables the application actor;
it does not delete `app_users`, ownership, conversations, audit, or other
historical foreign-key references. Directory activation and local security
suspension remain separate, so SCIM `active=true` cannot clear a local
suspension.

## Strongest Counterargument

Provisioning Keycloak through its SCIM API and reconciling Keycloak users into
OrgMemory would avoid implementing SCIM discovery, filters, PATCH semantics,
errors, credentials, and conformance. It would also create the Keycloak user
whose ID later appears as OIDC `sub`, which makes login correlation attractive.

That alternative is rejected for the production critical path because the
Keycloak feature is preview, realm-scoped, and protected by broad realm user
management permissions. It does not establish organization isolation inside
OrgMemory's shared realm, nor does it define how Keycloak groups become
OpenFGA relationships or remain separate from Source Groups. Hard deletion and
recreation could also change the Keycloak subject and strand existing
application bindings.

Keycloak SCIM remains eligible for a compatibility spike. It may become an
adapter later if its support level, tenant isolation, upgrade behavior, and
broker-linking semantics are proven.

## Consequences

- Native SCIM is a protocol adapter over an OrgMemory provisioning domain; it
  is not a second authentication system.
- Keycloak remains the only interactive authentication and session boundary.
- SCIM credentials are machine identities, not `CurrentActor` users, and use a
  separate stateless security chain.
- The product must own SCIM conformance, vendor interoperability, token
  security, rate limits, audit, backup, and rollback.
- Users ship before authorization-bearing Groups. Group-to-access mapping is a
  separate reviewable increment.
- Invitation onboarding remains available for unmanaged users. It cannot adopt
  a SCIM-managed user by email.
- SCIM may adopt an unmanaged invitation user by exact organization-scoped
  email; the adoption and invitation consumption are audited as one lifecycle
  transition.
- All schema changes are additive and feature-disabled until their live gates
  pass. Rollback suspends connections and rolls back binaries without dropping
  the provisioning ledger.

## References

- [RFC 7643 — SCIM Core Schema](https://www.rfc-editor.org/rfc/rfc7643)
- [RFC 7644 — SCIM Protocol](https://www.rfc-editor.org/rfc/rfc7644)
- [Keycloak SCIM administration guide](https://www.keycloak.org/docs/latest/server_admin/#_managing_scim)
- [Keycloak feature support levels](https://www.keycloak.org/server/features)
- [AWS SaaS identity](https://docs.aws.amazon.com/whitepapers/latest/saas-architecture-fundamentals/saas-identity.html)
- [Azure multitenant tenancy models](https://learn.microsoft.com/en-us/azure/architecture/guide/multitenant/considerations/tenancy-models)
- [Onyx multi-tenant architecture](https://docs.onyx.app/security/onyx_cloud/multi_tenant)
- [Airbyte create workspace API](https://reference.airbyte.com/reference/createworkspace)
