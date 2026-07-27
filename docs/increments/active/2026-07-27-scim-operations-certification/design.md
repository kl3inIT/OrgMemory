# SCIM Operations And Certification

## Outcome

The native SCIM service moves from one-organization private beta to a controlled
production capability with measured limits, vendor conformance, monitoring,
backup/restore, credential recovery, and rehearsed rollback.

This increment does not add protocol features merely to increase a capability
count. It certifies only behavior already implemented and truthfully advertised.

## Dependency

The User and Directory Group increments must be complete. The
[Directory Group Authorization Mapping](../2026-07-27-directory-group-authorization/plan.md)
increment is required only if group-to-access mapping is included in the GA
offering; otherwise Groups remain inert directory evidence.

## Provider Certification

The certification matrix covers:

### Microsoft Entra

- Microsoft SCIM Validator with no unresolved failure;
- non-gallery enterprise application;
- initial sync and on-demand provisioning;
- create, lookup, profile update, deactivate, reactivate, and delete semantics;
- case-insensitive PATCH operations;
- primary-email value-path filter;
- Group create, rename, membership add/remove/replace, and
  `excludedAttributes=members`;
- throttling/retry and disabled credential behavior.

### Okta

- SCIM 2.0 specification and CRUD lifecycle suites;
- `userName eq` and `displayName eq` lookup;
- PUT and PATCH update/deactivation profiles;
- stable pagination;
- Group membership path removal and full replacement;
- replay after timeout and credential rotation.

Synthetic or redacted provider fixtures become deterministic regressions. Raw
live request bodies, secrets, tenant names, emails, workforce identifiers, and
bearer tokens remain outside the repository.

## Evidence Manifest

Every live, load, restore, rollback, and security gate produces one
machine-readable release-evidence manifest containing:

- commit SHA, build/artifact ID, and schema version;
- environment/topology ID without secret values;
- provider/product and exact version/profile;
- UTC timestamp and operator timezone;
- synthetic/live dataset identity, size, and sanitized hash;
- acceptance threshold and observed value;
- test/report/log artifact paths plus content hashes;
- redaction declaration;
- operator and independent approver;
- expiry/revalidation date for drift-prone provider or infrastructure evidence.

A checkbox without this manifest and referenced artifact is not accepted
evidence. Manifests are retained as immutable CI/release artifacts under a
stable key such as `scim-evidence/<build-id>/<gate>.json` and linked from the
PR/release; they are not mutable ad hoc status documents.

## Published Operating Envelope

GA publishes measured, configurable bounds rather than implying unlimited
scale:

- maximum resources and memberships per connection;
- page count and maximum count;
- request bytes;
- filter length, depth, nodes, and execution timeout;
- PATCH operation count;
- members per mutation and per group;
- requests per second/burst;
- credential overlap and expiry;
- audit/event retention;
- convergence and deprovision service levels.

The load program measures one representative tenant, the published boundary,
and at least one controlled overload point. The selected limits become typed
configuration, contract documentation, alerts, and tests.

## Observability

Metrics contain connection-public IDs or bounded provider profiles, never user
attributes:

- request count, latency, status, operation, and resource type;
- authentication failure, rate limit, request bound, and invalid filter count;
- create/update/deactivate/reactivate/delete rates;
- conflict and correlation failure counts;
- credential expiry and last-use age;
- active/tombstoned resource and membership counts;
- when the mapped-Groups branch ships: outbox pending count, age, retry, and
  reconciliation drift;
- current-actor denial caused by directory, local, or readiness state.

Alerts cover credential attacks, mass deactivation/reactivation, abnormal
deletion, repeated correlation conflicts, deprovision service-level breach,
and expiring credentials. The mapped-Groups branch additionally alerts on
outbox lag and authorization drift. Logs and traces use request/resource server
IDs only where necessary and never capture SCIM bodies.

## Backup, Restore, And Rebuild

The provisioning ledger, tombstones, credential verifiers, and audit events are
always PostgreSQL backup scope. Mapping ownership, access blockers, and outbox
join that scope only for the mapped-Groups branch. Verifier keys/peppers remain
in the managed secret system and have a documented restore procedure; their
values are never copied into the runbook.

Restore rehearsal uses an isolated PostgreSQL candidate and, when mappings are
enabled, an isolated OpenFGA store:

1. restore the database;
2. restore or rotate machine credentials through the managed secret procedure;
3. keep connections suspended;
4. verify resource IDs, tombstones, activation axes, and audit counts;
5. for mapped Groups, rebuild/reconcile only managed OpenFGA assignments from
   the ledger;
6. for mapped Groups, compare expected and actual tuples/model IDs;
7. run two-tenant and deactivated-session negative tests;
8. enable one canary connection.

When present, OpenFGA managed assignments are rebuildable projections. Source
ACL generations are independent and are never synthesized from Directory
Groups during restore.

## Rollout State Machine

```text
PRIVATE_BETA
  -> PILOT_ONE_ORGANIZATION
  -> LIMITED_AVAILABILITY
  -> GENERAL_AVAILABILITY
```

This is release maturity, separate from each connection's configuration and
operational state. Advancement requires an accepted evidence manifest and
soak/incident thresholds, not a manual status label alone. Connections remain
individually controllable at every stage. An operational global freeze makes
SCIM read-only while preserving authenticated diagnostics. A separate security
cutoff denies all SCIM credentials; browser administrators recover through the
ordinary `/api/admin/**` boundary.

The first production smoke uses a bounded fixture:

1. create;
2. wait/read;
3. update;
4. first login and exact identity binding;
5. optional inert Group membership;
6. on the twenty-PR mapped branch only, approved mapping and permission
   explanation;
7. deactivate and prove immediate denial;
8. tombstone/cleanup;
9. credential rotation;
10. rollback and recovery.

## GA Exit And Documentation

Only the final PR may consolidate shipped behavior into `ARCHITECTURE.md`,
domain specs, test coverage, operations, and security documentation. Active
increment files continue to describe intent until code and live evidence exist.

General availability means the certified provider profiles are supportable and
available for new organizations; it does not auto-enable their connections.
Every new connection remains disabled until its own validation, recovery, and
operator gates pass.

Deferred features remain explicit:

- SCIM Bulk and sort;
- protocol ETag/If-Match;
- nested groups;
- arbitrary custom extensions;
- OAuth client credentials or workload identity federation;
- one Keycloak subject in multiple OrgMemory organizations;
- Keycloak preview SCIM as a production dependency.

They require evidence and a new increment, not silent expansion during
certification.
