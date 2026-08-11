# ZM Development Deployment

This runbook deploys the production-shaped POC to the ZM development server.
Short maintenance windows are acceptable. Data loss is not: capture a logical
backup before changing the shared PostgreSQL image or running application
migrations.

## Public Endpoints

- Web: `https://om.kl3in.tech`
- Authenticated MCP: `https://om.kl3in.tech/mcp`
- Keycloak: `https://auth.kl3in.tech`
- OIDC issuer: `https://auth.kl3in.tech/realms/orgmemory`

Only Nginx Proxy Manager exposes these endpoints. PostgreSQL, MinIO, OpenFGA,
API, worker, actuator, and the standalone MCP container remain private
Docker-network services. The web proxy exposes only the MCP container's exact
`/mcp` transport path.

## One-Time GitHub Configuration

Create a protected GitHub environment named `production`. Add:

- `PRODUCTION_SSH_HOST`
- `PRODUCTION_SSH_PORT`
- `PRODUCTION_SSH_USER`
- `PRODUCTION_SSH_PRIVATE_KEY`
- `PRODUCTION_SSH_KNOWN_HOSTS`

`Deploy production` runs automatically after `Build production images`
successfully publishes a complete image set for the current `main` SHA. The
same workflow retains a manual input for an intentional redeploy or rollback to
an older green `main` SHA.

If a required-reviewer protection rule is later added to the `production`
environment, automatic runs will wait for that approval. The current POC
environment has no approval rule.

## One-Time Host Preparation

The retained PostgreSQL container is still named `zeromail-postgres`, but
`/apps/postgres` owns its lifecycle. Do not rename the live container in the
same change as the OrgMemory deployment.

```bash
cd /apps/orgmemory
./infrastructure/deployment/scripts/prepare-host.sh
cp infrastructure/deployment/production.env.example .env.production
chmod 0600 .env.production
```

Fill `.env.production` on the server. Never copy the file back to a workstation
or commit it. Generate independent values for database, Keycloak, object
storage, OIDC, and application encryption secrets.

The initial start keeps:

```dotenv
ORGMEMORY_REQUIRE_PUBLIC_SMOKE=false
```

Build or pull the exact production images, then initialize OpenFGA:

```bash
./infrastructure/deployment/scripts/bootstrap-openfga.sh
```

The command creates one store from the repository authorization model and saves
the store ID, immutable model ID, and model SHA-256 to `.env.production`. It
refuses to create a second store when identifiers already exist.

## Nginx Proxy Manager

Both OrgMemory containers and Nginx Proxy Manager must share
`proxy-network`. Do not use the host IP as the forward target.

### `om.kl3in.tech`

Create a Proxy Host:

- Domain: `om.kl3in.tech`
- Scheme: `http`
- Forward host: `orgmemory-web`
- Forward port: `8080`
- Websocket support: enabled
- Block common exploits: enabled

Request a Let's Encrypt certificate, then enable Force SSL and HTTP/2. Enable
HSTS only after login and logout have completed successfully.

### `auth.kl3in.tech`

Create a second Proxy Host:

- Domain: `auth.kl3in.tech`
- Scheme: `http`
- Forward host: `orgmemory-keycloak`
- Forward port: `8080`
- Websocket support: enabled
- Block common exploits: enabled

Request a Let's Encrypt certificate and enable Force SSL and HTTP/2. Never
proxy Keycloak management port `9000`.

In the Advanced field, preserve the public request metadata:

```nginx
proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
proxy_set_header X-Forwarded-Proto $scheme;
proxy_set_header X-Forwarded-Host $host;
proxy_set_header X-Forwarded-Port 443;
```

After both hosts work, set:

```dotenv
ORGMEMORY_REQUIRE_PUBLIC_SMOKE=true
```

Verify the public issuer:

```bash
curl --fail --silent \
  https://auth.kl3in.tech/realms/orgmemory/.well-known/openid-configuration \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["issuer"])'
```

The result must be exactly:

```text
https://auth.kl3in.tech/realms/orgmemory
```

API and MCP validate the public issuer but fetch signing keys through the
private Keycloak service URL. They therefore do not depend on DNS hairpinning
through Nginx Proxy Manager for JWT verification.

## Normal Deployment

The normal path is fully chained:

1. a pull request merges to `main`;
2. `CI` verifies that exact merge commit;
3. `Build production images` publishes or carries forward the complete image
   set and records each registry digest in a release manifest;
4. `Deploy production` downloads that manifest and deploys the digest-pinned
   image set for the SHA automatically.

The deploy workflow ignores a completed image build when its SHA is no longer
the current `main`, preventing a slower old build from rolling production back
after a newer merge. Deployments remain single-flight.

The workflow pins the deployment-controller SHA from its trusted `main` checkout,
creates a clean linked worktree at the exact target commit as release data, rejects
any tracked or untracked worktree drift, and runs deployment, Keycloak, smoke, and
coordination executables from that immutable controller SHA with the validated
digest manifest. It never detaches or mutates the operator checkout. Terminal and
reconciled cleanup remove both the linked-worktree registration and its private
`/tmp/orgmemory-deploy.*` root before deleting state. This keeps intentional
rollback compatible with newer realm-migration safety logic without allowing
`main` to change the controller mid-run. Production operators must use the
workflow; calling `deploy.sh` without all six manifest-derived digest variables
fails closed.

The workflow executes that script from an ephemeral clean linked worktree and
points it at `/apps/orgmemory/.env.production`. It does not checkout, reset, or
clean the operator worktree at `/apps/orgmemory`, so unrelated staged or local
work there cannot replace files from the released commit.

Use the manual `workflow_dispatch` input only for an intentional redeploy or
rollback. Manual commits must still be ancestors of `main` with a successful
`Build production images` run whose digest manifest artifact is available. The
workflow retains those artifacts for 30 days; an older target must first have
its image build safely rerun to recreate a manifest.

The production route defaults approved by the 2026-08-02 bounded evaluation
are:

| Workload | Model | Reasoning | Lifecycle |
| --- | --- | --- | --- |
| Assistant Answer | `gpt-5.6-sol` | provider default | later requests |
| Keyword Planning | `gpt-5.6-luna` | `none` | later requests |
| Graph Extraction | `gpt-5.4-mini` | provider default | newly enqueued jobs |

Changing the Graph route never starts a reindex and never changes the immutable
profile pinned by an already queued or completed job. A future reindex is a
separate explicit operation; the current Documents surface deliberately offers
View and Delete only.

The deployment:

1. acquires a host lock;
2. inspects each existing Compose service container, proves its local image has
   exactly one matching registry digest, and snapshots those six deployed
   digests; an existing digest-pinned environment must match the containers.
   If the completed `postgres-bootstrap` one-shot container was pruned, its
   exact environment digest must instead match the locally retained image used
   by no-pull rollback; mutable tags and missing local images fail closed;
3. validates Compose without printing resolved secrets;
4. pulls the complete image set before mutation;
5. idempotently checks database roles/databases;
6. backs up OrgMemory, OpenFGA, and Keycloak;
7. runs OpenFGA migration and API-owned Flyway migration;
8. when the repository authorization-model digest changed or the legacy digest
   is absent, writes a new immutable model into the existing store and
   atomically pins its ID before application recreation;
9. pulls the candidate Keycloak image and proves it can render the previous
   login theme before any theme change, then reconciles a rollback target's stock
   theme before replacing the image;
10. starts the private runtime, verifies all six service containers use the
    manifest digests, and reconciles the target realm configuration;
11. checks web, API, MCP, Keycloak, the expected authorization theme bootstrap
    and its JavaScript/CSS assets, and optionally the other public endpoints;
12. restores and verifies the previous realm theme before restoring the previous
    digest-pinned image references and model pin when a gate fails, then verifies
    all six restored container digests. If realm
    restoration fails, rollback stops before starting a potentially incompatible
    previous Keycloak image and leaves the candidate environment for recovery;
13. while the deployment transaction and rollback trap remain active, executes the
    public login renderer in a read-only, capability-dropped Playwright container
    that receives no SSH or registry credentials. It rejects stock-theme fallback,
    incomplete or unsafe forms, failed or unapplied assets, unloaded fonts,
    console errors, external requests (blocked before dispatch), and horizontal
    overflow. An atomic first-writer decision symlink prevents approval/rejection
    races. The controller runs detached on the production host with remote-only
    logs and an 1800-second watchdog, plus a 900-second hard kill grace so a
    signal-ignoring child cannot retain registry credentials or suppress terminal
    status forever. An atomic first-writer ownership symlink lets either cleanup
    or a PID/start-time-qualified controller claim state, so no check/delete race
    can erase a live transaction. Cleanup atomically renames claimed state to a
    private tombstone before recursive deletion; the ownership object therefore
    never disappears while its original directory remains claimable. The
    finalizer derives ownership from that durable claim rather than an in-memory
    flag; HUP/INT/TERM are trapped before the claim. A reserved high-numbered
    `flock` lease is acquired by the launcher only after both detached reconcilers
    publish unique child-owned PID/start-time readiness markers. The launcher
    validates each live identity within a bounded interval before linked-worktree
    creation or registry login; a detached-fork exec failure therefore remains a
    pre-reconciler cleanup failure. Remote preparation runs in strict mode and
    exclusively creates a private, deployment-user-owned state directory. If
    deployment terminates before the active-controller handshake completes, the
    launcher accepts its atomic regular-file terminal status only after validating
    state/marker ownership and modes, rejecting symlinks and any status bytes beyond
    the canonical decimal line, checking the status range and qualified owner identity,
    and proving registry credential removal with no residual credential symlink. A
    status appearing while the ACTIVE lease is still held is fully validated rather
    than bypassed; if ACTIVE identity or lease validation observes a concurrent
    controller exit, the verifier also revalidates the terminal state published
    before lease release. The
    reconcilers close any inherited FD 198
    defensively; the controller and deploy
    subprocess tree inherit the launcher lease, which is never rebound by the
    deployment lock FD. A reconciler can therefore prove the whole controller tree
    is gone rather than trusting PID liveness or `controller-started` alone. Any
    bounded pre-ACTIVE signal is sent through a Linux pidfd after validating the
    process start time, preventing PID-reuse kills. Registry login has its own
    TERM/KILL timeout, so an ownerless credential process cannot hold the lease
    indefinitely. Two redundant reconcilers remain active through terminal cleanup,
    recover each other's interrupted tombstones, and process an external
    `cleanup-requested` marker outside the deletion namespace. After the workflow
    reads terminal status it requests lease-qualified cleanup and waits for linked
    worktree, state, and gate removal. If the runner is hard-killed before that
    request, the reconcilers perform the same cleanup after a bounded terminal
    grace period. ACTIVE
    requests first reject the gate, then consume terminal state only after the
    inherited lease is released and registry credentials are absent. Cleanup
    command failures never publish terminal success and remain recoverable by the
    peer reconciler. The controller finalizer likewise withholds terminal status
    and records intervention if credential deletion or atomic status publication
    fails. If the lease disappears after ACTIVE without terminal status,
    they remove credentials, persist operator intervention, and publish status
    `137` for the waiting workflow. Before ACTIVE, they only reclaim a dead owner
    (or terminate a controller that exceeds the bounded launch window). Launch
    acknowledgement
    is published only after the finalized controller owns state; an independent
    remote reconciler claims cleanup for an orphaned launch even if the runner
    disappears. Only explicit approval records the new current commit;
    rejection, durable pre-gate cancellation, HUP/INT/TERM (including repeated
    signals), gate timeout, or the controller's soft timeout enters rollback. If
    rollback itself exceeds the hard-kill grace, the controller publishes terminal
    failure, removes registry credentials, writes the persistent
    `/apps/orgmemory-runtime/deployment-intervention-required` latch, and blocks
    every later deployment until an operator verifies or completes rollback and
    deliberately clears the latch; it never reports the release committed.
    Successful rollback waits for Compose health, reruns production smoke with the
    previous login theme, rechecks all six immutable artifacts (including successful
    completion of the PostgreSQL one-shot), and only then restores `current-commit`.

`ORGMEMORY_BACKUP_UID` and `ORGMEMORY_BACKUP_GID` must match the owner of
`ORGMEMORY_BACKUP_DIRECTORY`. The one-shot backup container drops all Linux
capabilities and writes as that host identity instead of relying on root to
bypass directory permissions.

Database migrations must remain backward compatible with the immediately
previous application image. The rollback does not reverse a committed database
migration.

OpenFGA models are also immutable and remain in the store after a failed
canary. Rollback makes that unused version inert by restoring the previous
model ID. Model changes that need tuple migration must stage that migration
explicitly; the release script orders and pins models but does not synthesize
tuples.

Logical backup rotation is an operator responsibility, not part of the
transactional deployment script. A scheduled retention job may prune old
timestamped directories only after a newer `SHA256SUMS` set has passed a restore
verification. Keep at least the last two verified deployment backups and one
off-host copy; do not delete the only known-good pre-cutover backup.

## POC Performance Budget

The current profile prioritizes interactive traffic:

- API: 2 CPU limit, no Docker memory cap, 12 database connections, 200
  accepted live connections, virtual threads.
- Worker: 1.5 CPU limit, no Docker memory cap, 8 database connections, two
  concurrent graph-extraction jobs, and a 60% maximum JVM heap.
- Keycloak: 1 CPU, no Docker memory cap, 10 database connections.
- OpenFGA: 0.5 CPU, no Docker memory cap, 12 maximum database connections.
- MCP, web, and the observability services also run without Docker memory
  caps. Host-level monitoring remains mandatory because the ZM POC host has no
  swap.
- Nginx streaming proxy buffering is disabled for Assistant responses.

This is intended for approximately 20-30 concurrent POC users, not a load-test
claim. On 2026-07-25 the target host had 4 CPUs, 15 GiB RAM, and approximately
12 GiB available before OrgMemory started. During a demo, watch:

```bash
docker stats --no-stream
docker exec zeromail-postgres psql -U zeromail -d postgres -c \
  "select datname, count(*) from pg_stat_activity group by datname order by datname;"
```

Application package logs stay at `DEBUG` during the POC. SQL, Spring Security,
prompts, completions, credentials, evidence content, and tokens do not. Return
`ORGMEMORY_APP_LOG_LEVEL` to `INFO` after diagnosis.

## Short Maintenance Upgrade For Shared PostgreSQL

The ZM host is a development server, so use a short writer-stop window instead
of building an HA cutover:

1. record the current image digest and `/apps/postgres/docker-compose.yml`;
2. confirm the current and replacement images use the same base distribution;
   the observed ZM image and the pinned replacement are both Debian bookworm;
3. check for collation drift before and after the image replacement:

   ```sql
   SELECT datname,
          datcollversion,
          pg_database_collation_actual_version(oid) AS actual_version
   FROM pg_database
   WHERE datallowconn;
   ```

   If a database reports different stored and actual versions, reindex affected
   collation-dependent indexes before running
   `ALTER DATABASE <name> REFRESH COLLATION VERSION`;
4. run a full logical backup plus per-database dumps;
5. stop Northstar writers and OrgMemory;
6. change only `POSTGRES_IMAGE` to the pinned OrgMemory PostgreSQL image;
7. ensure the shared command still preloads `pg_stat_statements,age`;
8. recreate only `zeromail-postgres` against
   `zero-mail_postgres_data`;
9. verify every database, database owner, role connection limit, pgvector, AGE,
   `pg_stat_statements`, the OrgMemory role's AGE session preload through
   `orgmemory_runtime.age_session_preloaded()`, its `ag_catalog.ag_graph` read
   privilege, and Northstar; do not grant `pg_read_all_settings` to the
   application role;
10. restore the prior image immediately if PostgreSQL does not become healthy.

Never remove or rename `zero-mail_postgres_data` during the upgrade.
Apache AGE for PostgreSQL 18 is currently pinned to the upstream
`PG18/v1.8.0-rc0` commit. Treat replacing it with a GA release as a post-POC
upgrade, and never roll the shared image back after AGE-backed data exists
without first confirming the old image still contains a compatible `age.so`.
