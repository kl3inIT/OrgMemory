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
the store/model IDs to `.env.production`. It refuses to create a second store
when identifiers already exist.

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
3. `Build production images` publishes or carries forward the complete
   immutable image set;
4. `Deploy production` deploys that SHA automatically.

The deploy workflow ignores a completed image build when its SHA is no longer
the current `main`, preventing a slower old build from rolling production back
after a newer merge. Deployments remain single-flight.

The workflow checks out the exact commit on the server and runs:

```bash
./infrastructure/deployment/scripts/deploy.sh <full-commit-sha>
```

Use the manual `workflow_dispatch` input only for an intentional redeploy or
rollback. Manual commits must still be ancestors of `main` with a successful
`Build production images` run.

The deployment:

1. acquires a host lock;
2. replaces only immutable image references in `.env.production`;
3. validates Compose without printing resolved secrets;
4. pulls the complete image set before mutation;
5. idempotently checks database roles/databases;
6. backs up OrgMemory, OpenFGA, and Keycloak;
7. runs OpenFGA migration and API-owned Flyway migration;
8. starts the private runtime;
9. checks web, API, MCP, Keycloak, and optionally the public endpoints;
10. restores the previous image references when a gate fails.

`ORGMEMORY_BACKUP_UID` and `ORGMEMORY_BACKUP_GID` must match the owner of
`ORGMEMORY_BACKUP_DIRECTORY`. The one-shot backup container drops all Linux
capabilities and writes as that host identity instead of relying on root to
bypass directory permissions.

Database migrations must remain backward compatible with the immediately
previous application image. The rollback does not reverse a committed database
migration.

Logical backup rotation is an operator responsibility, not part of the
transactional deployment script. A scheduled retention job may prune old
timestamped directories only after a newer `SHA256SUMS` set has passed a restore
verification. Keep at least the last two verified deployment backups and one
off-host copy; do not delete the only known-good pre-cutover backup.

## POC Performance Budget

The current profile prioritizes interactive traffic:

- API: 2 CPU limit, 2 GiB memory, 12 database connections, 200 accepted live
  connections, virtual threads.
- Worker: 1.5 CPU limit, 3 GiB memory, 8 database connections, two concurrent
  graph-extraction jobs, and a 60% maximum JVM heap.
- Keycloak: 1 CPU, 768 MiB, 10 database connections.
- OpenFGA: 0.5 CPU, 384 MiB, 12 maximum database connections.
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
   `pg_stat_statements`, the OrgMemory role's AGE session preload and
   `ag_catalog.ag_graph` read privilege, and Northstar;
10. restore the prior image immediately if PostgreSQL does not become healthy.

Never remove or rename `zero-mail_postgres_data` during the upgrade.
Apache AGE for PostgreSQL 18 is currently pinned to the upstream
`PG18/v1.8.0-rc0` commit. Treat replacing it with a GA release as a post-POC
upgrade, and never roll the shared image back after AGE-backed data exists
without first confirming the old image still contains a compatible `age.so`.
