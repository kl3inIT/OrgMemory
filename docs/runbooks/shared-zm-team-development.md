# Shared ZM Team Development

This runbook is for the current five-person team while ZM remains a disposable,
non-production environment. Local applications reuse the same OrgMemory data,
Keycloak realm, OpenFGA store/model, MinIO bucket, and AI gateway as the server.
Do not reuse this workflow for customer or production data.

## What Runs Where

| Part | Daily development behavior |
| --- | --- |
| API, web, MCP | local; multiple developers may run them concurrently |
| PostgreSQL, OpenFGA, MinIO | shared on ZM through loopback-only SSH tunnels |
| Keycloak | shared public TLS issuer with exact `127.0.0.1` web callbacks |
| Worker | either ZM or one developer locally, never both |
| Flyway and OpenFGA model writes | post-merge deployment only |

There is no `shared-zm` or `test` Spring profile. Local applications use their
default configuration plus launcher-owned environment overrides. The deployed
ZM applications continue to use `prod`. Tests use Testcontainers,
`@ServiceConnection`, and test properties rather than a persistent test
profile.

## Prerequisites

- Node 24 selected with `nvm use 24` when running the web application;
- Java version required by the repository toolchain;
- authenticated SSH access to ZM using the deployment account or another
  account permitted to read `/apps/orgmemory/.env.production` and inspect the
  OrgMemory containers;
- no local process already using ports `15432`, `18081`, or `19000`.

The launcher never writes the server configuration to disk. The SSH account is
high trust because local feature code receives credentials for the shared
non-production data.

## Start A Normal Session

```powershell
nvm use 24
.\scripts\shared-zm-dev.ps1 `
  -Server <zm-host> `
  -SshUser <ssh-user>
```

The default starts API and web. Open `http://127.0.0.1:5173`. Select services
when a narrower session is useful:

```powershell
.\scripts\shared-zm-dev.ps1 `
  -Server <zm-host> `
  -SshUser <ssh-user> `
  -Services api,mcp
```

The launcher fetches `origin/main`, warns about Flyway/OpenFGA changes, opens
three SSH tunnels bound to `127.0.0.1`, disables local Flyway and PostgreSQL
index provisioning, and starts the selected processes. Press `Ctrl+C` to close
the applications and tunnels.

## Run A Local Worker

```powershell
.\scripts\shared-zm-dev.ps1 `
  -Server <zm-host> `
  -SshUser <ssh-user> `
  -Services api,web,worker
```

This acquires one five-minute renewable lease and stops the ZM worker before
the local worker starts. A second worker is rejected. Normal API/web/MCP
sessions are not blocked. Clean shutdown releases the lease immediately; a
server watchdog restores the ZM worker after an abandoned lease expires.

Inspect or recover coordination state on ZM:

```bash
cd /apps/orgmemory
./infrastructure/deployment/scripts/team-dev-coordination.sh status
./infrastructure/deployment/scripts/team-dev-coordination.sh reap
```

## Database And OpenFGA Changes

The developer who opens the PR owns the conflict resolution:

1. Update from `origin/main` before requesting merge.
2. Run `python scripts/check_shared_schema_changes.py --base-ref origin/main`.
3. If another PR claimed the same unapplied Flyway version, rename yours to the
   next available version and update dependent tests/docs.
4. Never edit or repair a migration already on `main`.
5. Validate migrations with the disposable Testcontainers suite and validate
   the OpenFGA model with its model tests.
6. Merge only after CI is green. The deployment from `main` acquires the short
   maintenance lease, backs up shared state, applies the change once, runs
   smoke checks, and releases the lease after success or rollback.

The environment is repairable over SSH, so the workflow intentionally has no
extra approval ceremony. Direct repair remains an exception: record what was
changed and convert any durable schema/model correction into repository code.

## Keep Documentation Current

Every PR runs:

```powershell
python scripts/check_public_docs_impact.py --base-ref origin/main
```

When users, administrators, deployers, or integrators observe changed behavior,
update its English and Vietnamese pages under `apps/docs/content/docs` in the
same PR. Private ZM topology, credentials, leases, and repair procedures stay
in repository runbooks such as this one. Internal refactors may explicitly
state that no reader-visible docs changed.

## Never Do This

- expose PostgreSQL, OpenFGA, or MinIO on a public host port;
- save the exported configuration in `.env`, logs, chat, or a ticket;
- run seed/reset/full-delete commands against shared ZM;
- run a local worker without the launcher lease;
- apply feature-branch Flyway or OpenFGA changes directly to shared ZM.
