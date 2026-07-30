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
- [x] Build only dependency-affected production images, carry unchanged OCI
  digests forward, and emit one complete release manifest.
- [x] Add a protected, single-flight production deployment workflow with exact
  commit selection, health checks, and rollback.
- [x] Chain a green immutable image release into automatic deployment of the
  current `main` SHA, retain manual redeploy/rollback, and reject stale
  out-of-order automatic runs before server access.
- [x] Update delivery/testing documentation to match implemented behavior.
- [ ] Let an infrastructure-only commit reach production. Found on 2026-07-29
      while deploying `ea21ceb`, which changed only
      `infrastructure/deployment/**`. No path filter in `build-images.yml` names
      that directory, so nothing was built; "Require a green production image
      set" then found no release, and the deploy skipped every remaining step.
      Deployment runs `git checkout --detach <sha>` on the server and executes
      that commit's `compose.production.yaml` and `deploy.sh`, so a commit
      without an image set can never be deployed and its configuration change
      cannot take effect on its own. Today such a change reaches the server only
      by riding along with the next application-code commit.
- [ ] Stop reporting a skipped deployment as a successful one. The run above
      concluded `success` with `Require a green production image set`,
      `Configure SSH` and `Deploy and verify` all skipped. The reason is in the
      log — "Skipping deployment: build run … produced no image set" — but the
      status shown against the commit says the deployment succeeded. A pipeline
      that reports green for doing nothing is the same failure class as an
      exporter that reports healthy while pushing to nowhere.

## Server Preparation

- [x] Create a neutral `shared-infra` Docker network and attach the retained
  PostgreSQL container with aliases `postgres` and `shared-postgres`.
- [x] Keep Nginx Proxy Manager on `proxy-network`.
- [x] Disable the obsolete Zero Mail GitHub runner with administrator access.
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
- [x] Web login redirects through `auth.kl3in.tech` and returns to
  `om.kl3in.tech`.
- [x] Exact SPA routes that collide with Vite output directories remain
      reachable. The web Nginx config serves `/assets` and `/assets/` from the
      SPA shell while `/assets/*` remains immutable static content, and the
      Docker-backed proxy regression covers all three paths.
- [ ] One upload reaches `READY`, GraphRAG indexing completes, Assistant
  retrieval returns a citation, and denied evidence remains absent.
- [ ] VPS memory, PostgreSQL connections, JVM heap, restart behavior, and image
  retention are observed for one full demo workflow.
- [ ] CI and deploy workflows are green.
- [x] Actionable CodeRabbit threads are resolved.
- [x] Merge through reviewed PR #44.
