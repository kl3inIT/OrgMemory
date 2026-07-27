# Plan

## PR U0 — Trusted Correlation And Invitation Guard

Scope:

- implement the fixed signed trust tuple:
  `(iss, aud, azp, idp alias, directory ID, workforce ID)`;
- resolve organization only through the correlation-active directory
  connection selected by the signed directory ID;
- require a platform-approved broker alias and Keycloak mapper fingerprint;
- restrict automatic new binding to an interactive brokered OIDC session;
- atomically bind only one active, unbound resource with the exact workforce
  key;
- add `VALIDATING` connection state and one allowlisted probe identity;
- guard invitation login before any User endpoint exists: a SCIM-managed actor
  or same-organization SCIM collision cannot be adopted through email;
- add conflict records plus safe suspend and retry-after-upstream-correction
  operations.

Merge gate:

- [ ] existing `(issuer, subject)` binding always wins;
- [ ] email, `userName`, display name, and unapproved claims cannot bind a SCIM
  resource;
- [ ] missing/user-editable claim, wrong issuer, audience/client, broker alias,
  directory, organization, recycled key, inactive resource, tombstone,
  duplicate, and ambiguity fail closed;
- [ ] the same workforce key in two directory connections is disambiguated only
  by the signed directory ID;
- [ ] bearer JWT cannot bootstrap a new binding; browser and bearer resolve the
  same actor after binding;
- [ ] concurrent first login leaves one correct external binding;
- [ ] SCIM-managed user plus same-email invitation plus no approved trust tuple
  denies, leaves the invitation open, and creates no external identity;
- [ ] no conflict action merges users or changes identity from email.

## PR U1 — Dormant User Create, Read, Search, And Projection

Scope:

- implement User DTO/schema mapping and explicit mutability rules;
- reject supplied `password` and all unsupported/authorization attributes;
- implement POST, GET by ID, GET list, and `POST /Users/.search`;
- implement stable bounded pagination, `attributes`, and
  `excludedAttributes`;
- compile filter ASTs into parameterized allowlisted predicates;
- atomically create one `EMPLOYEE` application actor and SCIM resource;
- return RFC media types, ListResponse, Error, uniqueness, and not-found
  behavior;
- keep User ResourceType unadvertised and every real connection unable to use
  these dormant routes until the full U2 profile exists.

Merge gate:

- [ ] Entra and Okta create/query fixtures pass byte/semantic snapshots;
- [ ] empty search is `200` with a correct empty `ListResponse`;
- [ ] page ordering is stable under equal names and concurrent inserts;
- [ ] filters cover escaping, Unicode, compound expressions, value paths,
  maximum depth/nodes, and SQL injection cases;
- [ ] concurrent duplicate POST leaves one app user and one SCIM resource;
- [ ] supplied password returns an explicit SCIM error and is absent from
  persistence/logs;
- [ ] new actor is exactly `EMPLOYEE` and cannot read `RESTRICTED` or
  `CONFIDENTIAL` evidence without independent policy;
- [ ] no POST creates an external identity, OpenFGA tuple, source principal, or
  application authorization grant;
- [ ] no external connection can discover or invoke the partial User profile.

## PR U2 — Full Dormant User Lifecycle And Validation Probe

Scope:

- implement full-resource PUT rules;
- implement atomic case-insensitive PATCH `add`, `replace`, and `remove`;
- cover pathless payloads and provider path/value-path variants;
- maintain directory, local, readiness, and effective access state;
- implement tombstoning DELETE and reserved identifier behavior;
- append sanitized lifecycle events;
- add replay, out-of-order, per-resource concurrency, and partial-failure tests;
- expose the complete User ResourceType only to a connection in `VALIDATING`;
- restrict that state to one declared workforce probe resource;
- run and record the SCIM-to-Keycloak correlation probe, then return the
  connection to `DISABLED` with configuration status `VALIDATED`.

Merge gate:

- [ ] malformed or unsupported paths return the correct SCIM error and commit
  nothing;
- [ ] repeated deactivate/reactivate operations are idempotent;
- [ ] `active=true` cannot clear local suspension;
- [ ] `active=false` denies both an existing browser session and bearer JWT on
  the next request;
- [ ] DELETE preserves every historical application foreign-key reference and
  later SCIM GET returns `404`;
- [ ] tombstoned `externalId`/workforce ID remains reserved;
- [ ] PUT/PATCH cannot change an `externalId` used as the workforce key;
- [ ] no payload, password, profile value, or PII filter appears in logs,
  events, traces, or metrics;
- [ ] a `VALIDATING` credential cannot create, enumerate, or infer any
  non-probe resource;
- [ ] live evidence proves the workforce field is immutable/non-user-editable
  and the exact signed trust tuple matches the SCIM probe;
- [ ] changing the approved mapper fingerprint invalidates validation;
- [ ] previous-binary rollback still denies a deactivated user.

## PR U3 — User Administration, Enablement, And Private-Beta Proof

Scope:

- allow a `VALIDATED` connection to enter `ENABLED` only after explicit pilot
  approval and recovery prerequisites;
- advertise the complete User ResourceType to that enabled connection;
- replace the placeholder SCIM panel with connection and directory-user views;
- show directory/local/readiness/effective access and sign-in-link state
  separately;
- implement searchable conflict diagnosis and audit detail;
- add safe token rotation, read-only, suspend/resume, and pilot controls;
- add browser tests for narrow/mobile, keyboard, loading, error, and dangerous
  confirmations;
- run one complete real-ingress pilot flow.

Live proof:

1. create a disabled connection and one-time credential;
2. designate one probe and move only that connection to `VALIDATING`;
3. provision the probe through the real SCIM provider;
4. broker-login as the same object and record the exact trust-tuple evidence;
5. return to `DISABLED`, approve the pilot, then enter `ENABLED`;
6. create and query a non-probe user through the real SCIM endpoint;
7. broker-login and prove the exact `(issuer, subject)` binding;
8. update profile and prove identity is unchanged;
9. deactivate and prove the existing session and bearer request are denied;
10. locally suspend, send `active=true`, and prove access remains denied;
11. clear local suspension under admin audit;
12. DELETE, prove SCIM `404`, historical ownership retained, and no cross-tenant
    visibility;
13. set `READ_ONLY` and complete the binary rollback rehearsal;
14. set `SUSPENDED` and prove all SCIM credentials are denied.

Merge/exit gate:

- [ ] focused backend, PostgreSQL, contract, web unit, typecheck, build, and
  browser suites pass;
- [ ] the live proof is redacted and reproducible;
- [ ] recovery administrator access and mass-change alerts are proven;
- [ ] Users private beta is enabled for exactly one organization;
- [ ] Groups and authorization remain unavailable.
