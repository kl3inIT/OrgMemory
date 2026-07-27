# Plan

## PR F1 — Protocol Profile, SDK Decision, And Conformance Harness

Scope:

- freeze the supported User and Group attribute/mutability profile;
- freeze password as unsupported input with explicit rejection and provider
  setup guidance;
- add RFC 7643/7644 request/response fixtures and SCIM error snapshots;
- add sanitized Entra and Okta dialect fixtures, including case-insensitive
  PATCH operations and membership path forms;
- compare candidate SDK/parser dependencies through executable tests;
- run the isolated Keycloak 26.7 alternative spike for broker subject,
  realm/organization isolation, permissions, deactivate/delete, feature-disable,
  and upgrade behavior;
- prove the fixed correlation claims can be emitted from supported,
  non-user-editable broker/session mapper inputs;
- record the selected dependency, license, transitive surface, CVE scan, and
  reasons rejected;
- define a dedicated SCIM contract generation/drift strategy.

Merge gate:

- [x] filter corpus covers escaping, Unicode, compound expressions, value paths,
  invalid grammar, depth, and injection;
- [x] PATCH corpus covers pathless and path forms without a regex parser;
- [x] candidate runs within Spring Boot 4/Jackson 3 tests without a second HTTP
  runtime;
- [x] Keycloak results are recorded without turning its preview API into a
  production dependency;
- [x] required correlation claims use a supportable mapper path, or a new
  Keycloak-extension decision blocks U0 explicitly;
- [x] dependency/static analysis and license review pass;
- [x] unsupported capabilities are explicitly listed and not advertised.
- [x] password fixtures prove explicit rejection rather than silent discard.

## PR F2 — Provisioning Ledger And Dual-Axis Lifecycle

Scope:

- create the provisioning core module and public application API;
- add connection, credential metadata, SCIM User resource, and append-only event
  persistence;
- add organization-composite foreign keys and connection-scoped uniqueness;
- enforce global public-token-ID uniqueness and one correlation-active
  connection per organization with database constraints;
- add local, directory, readiness, and materialized effective-active state;
- add the backward-compatible active-state trigger;
- add repository compare-and-set/version semantics;
- add redacted audit-event construction.

Merge gate:

- [x] Flyway upgrade and `ddl-auto=validate` pass on PostgreSQL;
- [x] one organization cannot access another connection by every repository
  method;
- [x] concurrent duplicate `externalId`, `userName`, or workforce key leaves
  one resource;
- [x] concurrent connection enable and public-token-ID collision are constrained
  by PostgreSQL rather than an application-only check;
- [x] no raw SCIM payload or credential is persisted;
- [x] local suspension wins over directory activation;
- [x] previous-binary activation cannot revive a directory-disabled user;
- [x] Spring Modulith dependency verification passes.

Rollback gate:

- [ ] previous binary starts and resolves active/inactive users correctly;
- [x] connection tables remain inert with no API route enabled;
- [ ] database restore rehearsal preserves resource IDs and tombstones.

## PR F3 — Machine Security, Discovery, And Credential Administration

Scope:

- add the highest-priority stateless `/scim/v2/**` security chain;
- implement connection-token issue, one-time display, rotate, revoke, expiry,
  last-used, scopes, and immediate cache invalidation;
- enforce TLS and request/rate bounds through typed configuration;
- add SCIM media type, error mapper, request correlation, and safe logging;
- implement authenticated discovery from the actual capability registry;
- generate and verify the separate SCIM protocol contract;
- add the disabled connection administration page and APIs.

Merge gate:

- [ ] missing, malformed, expired, revoked, wrong-scope, and old-overlap tokens
  return the expected generic `401` or `403`;
- [ ] an organization A token cannot distinguish any organization B state;
- [ ] token value is absent from PostgreSQL plaintext, logs, events, traces, and
  metrics;
- [ ] SCIM token on `/api/**`, and OIDC JWT/browser cookie on `/scim/v2/**`,
  are rejected without fallback to another chain;
- [ ] CSRF remains disabled only for the SCIM chain;
- [ ] production startup fails when the configured verifier key/current key
  version is missing;
- [ ] credential administration derives tenant from `CurrentActor` and requires
  OpenFGA `can_manage_members`;
- [ ] rotation overlap and immediate revocation pass concurrency tests;
- [ ] discovery snapshots exactly match implemented capabilities;
- [ ] product OpenAPI/browser client generation does not include SCIM routes;
- [ ] new chain precedence/token-confusion tests pass, and existing browser
  security, bearer `/api/**`, CSRF, session, and logout behavior remains green.

## Increment Exit

- [ ] F1, F2, and F3 are merged in order.
- [ ] No User or Group mutation is reachable.
- [ ] Every connection remains disabled by default.
- [ ] A two-organization negative test, credential-rotation rehearsal, and
  previous-binary rollback rehearsal are attached to the increment evidence.
- [ ] The next increment may create users only behind a private-beta feature
  gate.
