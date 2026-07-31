# Shared ZM Team Development Design

## Intent

Five developers can run OrgMemory applications from local checkouts while
reusing the current non-production ZM PostgreSQL data, Keycloak realm, OpenFGA
store, MinIO bucket, and AI gateway. The workflow stays deliberately small:
there is one shared dataset, no per-developer infrastructure provisioning, and
the repository tooling detects changes that are unsafe against shared state.

Production remains a future isolated migration target. This design must not be
reused for customer or production data.

## Boundary

| Service | Shared team boundary | Local access |
| --- | --- | --- |
| PostgreSQL | canonical `orgmemory` database and existing rows | loopback SSH tunnel |
| Keycloak | canonical `orgmemory` realm and users | public TLS issuer plus exact loopback callbacks on the shared web client |
| OpenFGA | canonical store and pinned model | loopback SSH tunnel |
| MinIO | canonical evidence bucket | loopback SSH tunnel |
| AI gateway | existing ZM development route | loopback SSH tunnel |

No OpenSearch container is deployed on ZM. PostgreSQL, pgvector, and AGE remain
the active search and graph implementation.

## Operating Modes

The server-backed coordination registry stays intentionally small:

- ordinary local web/API/MCP sessions do not need a lease and may coexist;
- `worker` is exclusive only against another worker, pauses the ZM worker, and
  permits exactly one local worker session;
- `maintenance` serializes only Flyway/OpenFGA writers. It does not kick out or
  block ordinary local sessions on this non-production environment.

The two exceptional leases carry owner, host, session ID, commit, heartbeat,
and expiry. Acquisition and renewal are serialized with `flock`. Stale leases
are reaped before each operation; stale worker ownership triggers recovery of
the canonical ZM worker. The launcher acquires and releases them automatically,
so they do not add manual approval steps.

## Schema And Authorization Change Detection

Repository tooling compares the current branch with `origin/main` and treats
either path as a protected state change:

- `core/src/main/resources/db/migration/**`
- `integrations/authorization-openfga/src/main/openfga/model.fga`

A feature branch containing either change receives a prominent warning but is
not blocked from shared-data mode. By default Flyway remains disabled and the
OpenFGA model remains pinned, so ordinary startup cannot mutate either one.
Feature checkouts never apply these changes to shared ZM. The author validates
them in disposable PostgreSQL/OpenFGA tests, resolves any conflict with current
`main`, and lets the normal post-merge deployment own the short maintenance
lease and mutation. Applied Flyway migrations remain immutable.

CI rejects duplicate or misnamed migration versions, changes to migrations
already present on `main`, invalid OpenFGA models, and branches whose protected
files no longer apply cleanly after `main` advances. PostgreSQL integration
tests prove clean-schema and upgrade behavior.

After merge, deployment compares the candidate with the currently deployed
commit. If migrations or the model changed, deployment automatically acquires
the same short maintenance lease before its existing backup, model write, and
API-owned Flyway startup. It releases the lease after smoke checks or rollback.
A release without those changes does not acquire the lease.

## Developer Lifecycle

1. Update the branch from `origin/main`.
2. Start the launcher. It verifies Node 24, reports the Git state and protected
   path changes, and checks server reachability.
3. The launcher retrieves only the required values over authenticated SSH,
   opens loopback-only tunnels, and starts the selected local applications.
4. Normal mode starts API/web and optionally MCP. The launcher passes explicit
   environment overrides that disable Flyway and graph index provisioning.
   Feature checkouts never mutate the shared schema or OpenFGA model. No
   ZM-specific Spring profile is added.
5. Worker mode obtains exclusive ownership before stopping the ZM worker and
   starting the local worker.
6. Exceptional modes heartbeat until stopped, then release automatically. A
   trap is a convenience; TTL recovery is the correctness mechanism.

## Migration Collaboration Rule

The author of a PR owns its migration conflicts:

1. merge current `origin/main` into the feature branch;
2. when another PR claimed the same version, rename the unapplied migration to
   the next available version and update dependent tests/documentation;
3. never edit or `repair` a migration already present on `main`;
4. rerun clean-schema and populated-upgrade tests plus OpenFGA model tests;
5. keep changes backward compatible using expand, bounded backfill, validate,
   and a later contract migration where required.

Branch protection and CI enforce the mechanical floor without requiring an
extra human approval workflow. Review remains responsible for semantic
conflicts that version uniqueness cannot detect.

## Secret And Data Boundary

- Nothing is made public; every private service uses an SSH loopback tunnel.
- Secrets are streamed into child-process environment variables and are never
  printed, committed, or written into a repository file.
- The shared web client adds exact `127.0.0.1` callback, origin, and logout
  URIs. It retains authorization code with PKCE, no direct grants, and no
  service account; no wildcard is introduced.
- Local branch code can read and mutate the shared non-production dataset and
  receives credentials capable of doing so. This is an explicit owner-accepted
  risk, not a production security pattern.
- Seed/reset/full-delete commands are forbidden against shared ZM state.

## Strongest Counterargument

Arbitrary local branch code that receives shared database, object-store,
authorization, encryption, and model credentials can corrupt or disclose the
entire development dataset. A shared API can also enqueue work that a different
worker version consumes. Leases reduce concurrency but cannot create tenant or
credential isolation, and rollback cannot atomically undo changes across
PostgreSQL, OpenFGA, and MinIO.

## Selected Choice

Use one shared non-production dataset because the five-person team prioritizes
low setup cost and shared realistic data. Permit ordinary local API/web/MCP
concurrency, use warnings rather than hard branch blocks, serialize worker and
schema/model writers automatically, and keep direct SSH repair as an explicit
development-environment recovery path.

## Rejected Alternatives

- Per-developer databases, stores, buckets, and clients: safer but rejected by
  the project owner as unnecessary operational complexity for the current
  non-production server.
- Feature-branch migrations against ZM: rejected. Disposable tests validate
  them before merge; the normal deployment applies them once from `main`.
- Frontend-only local development: rejected because the team also develops API,
  worker, and MCP.
- One complete local or server stack per developer: retained as the disposable
  migration-test fallback, not the default daily path.

## Scope

In scope: protected-path detection, migration collaboration checks, server lease
registry, loopback launcher, exact Keycloak callbacks, deployment
maintenance gating, deterministic tests, and a team runbook.

Out of scope: production/customer data, public infrastructure ports, automatic
destructive reset, per-developer isolation, and OpenSearch.
