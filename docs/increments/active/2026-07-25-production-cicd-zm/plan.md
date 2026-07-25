# Production CI/CD And ZM Runtime Plan

## Repository

- [x] Confirm ZM capacity, retained services, current PostgreSQL image,
  extensions, databases, and Docker networks.
- [x] Clone `origin/main` to `/apps/orgmemory` without starting a runtime.
- [x] Pin the deployment hostnames to `om.kl3in.tech` and
  `auth.kl3in.tech`.
- [x] Accept Keycloak `26.7.0` from the dependency update as the production
  baseline.
- [x] Add optimized, non-root application and web images.
- [x] Add an optimized Keycloak production image and non-demo realm.
- [x] Upgrade the PostgreSQL GraphRAG image to PG18 + pgvector `0.8.4` +
  pinned Apache AGE `PG18/v1.8.0-rc0` while preserving
  `pg_stat_statements`.
- [x] Add the production Compose profile, required-variable contract, health
  checks, bounded resources, and container hardening.
- [x] Add an idempotent shared-PostgreSQL bootstrap and backup/restore/cutover
  runbook.

## Continuous Integration

- [x] Add change detection without making branch protection path-dependent.
- [x] Split the expensive PostgreSQL GraphRAG image/integration gate from
  ordinary backend tests where the Gradle test topology permits it.
- [x] Preserve the stable `CI Gate`.
- [x] Cache Gradle, pnpm, BuildKit, and evaluation dependencies.
- [x] Add image builds with immutable SHA tags, registry cache, SBOM,
  provenance, and vulnerability scanning.
- [x] Add a protected, single-flight production deployment workflow with exact
  commit selection, health checks, and rollback.
- [x] Update delivery/testing documentation to match implemented behavior.

## Server Preparation

- [x] Create a neutral `shared-infra` Docker network and attach the retained
  PostgreSQL container with aliases `postgres` and `shared-postgres`.
- [x] Keep Nginx Proxy Manager on `proxy-network`.
- [ ] Disable the obsolete Zero Mail GitHub runner with administrator access.
- [x] Create DNS A records for both production hostnames.
- [ ] Create Nginx Proxy Manager hosts and certificates only after the runtime
  services are healthy on the private proxy network.
- [x] Store production secrets in `/apps/orgmemory/.env.production` with mode
  `0600`.

## Shared PostgreSQL Cutover

- [x] Capture current image digest, server settings, roles, databases,
  extensions, and volume.
- [ ] Produce full and per-database logical backups under the protected backup
  directory.
- [ ] Restore into an isolated candidate PG18 container.
- [ ] Verify `pg_stat_statements`, pgvector `0.8.4`, the pinned Apache AGE,
  database ownership, and existing Northstar behavior.
- [ ] Create separate OrgMemory, OpenFGA, and Keycloak database/logins through
  the idempotent bootstrap.
- [ ] Schedule a bounded writer-stop maintenance window.
- [ ] Recreate the retained PostgreSQL container with the pinned shared image.
- [ ] Run extension, Northstar, Keycloak, OpenFGA, API, worker, upload,
  retrieval, citation, and OIDC smokes.
- [ ] Roll back immediately on any failed gate.

## Completion Gates

- [x] Compose config renders with no secret values committed.
- [x] Production profiles fail fast when required settings are absent.
- [x] API is the only Flyway owner.
- [ ] Keycloak reports ready on its management endpoint and emits the expected
  public issuer.
- [ ] Web login redirects through `auth.kl3in.tech` and returns to
  `om.kl3in.tech`.
- [ ] One upload reaches `READY`, GraphRAG indexing completes, Assistant
  retrieval returns a citation, and denied evidence remains absent.
- [ ] VPS memory, PostgreSQL connections, JVM heap, restart behavior, and image
  retention are observed for one full demo workflow.
- [ ] CI and deploy workflows are green.
- [ ] Actionable CodeRabbit threads are resolved.
- [ ] Merge through a reviewed pull request.
