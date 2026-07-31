# Shared ZM Team Development Plan

- [x] Verify the current ZM runtime and absence of OpenSearch.
- [x] Record the project owner's choice to share the current non-production
      dataset and keep production as a future isolated migration target.
- [x] Classify this reversible non-production workflow as below the independent
      architecture-challenge threshold after explicit owner direction.
- [x] Add warning-only migration/OpenFGA change detection and PR conflict checks.
- [x] Add the TTL/heartbeat worker and maintenance lease registry.
- [x] Add the loopback-only local launcher and exact Keycloak development
      callbacks on the shared web client.
- [x] Gate post-merge schema/model deployment with the maintenance lease.
- [x] Add deterministic lease, detection, deployment, and configuration tests.
- [x] Publish the five-person migration and shared-development runbook.
- [ ] Verify one shared local API/web session and exclusive worker recovery on
      ZM without exposing infrastructure ports.
- [ ] Run the PR/CI/CodeRabbit/merge/deploy loop.
- [ ] Consolidate current behavior and archive the increment.

## Completion Gates

- Local API cannot run Flyway or provision PostgreSQL indexes in shared mode.
- A feature branch with a migration or OpenFGA model change receives an
  actionable warning and cannot apply it through the local launcher.
- CI rejects duplicate/misnamed migrations and edits to migrations already on
  `main`; OpenFGA validation/model tests remain mandatory.
- Multiple ordinary sessions may coexist; only worker-to-worker and
  writer-to-writer concurrency is excluded, and stale worker leases restore the
  ZM worker.
- A deployment with schema/model changes serializes against another schema/model
  writer and holds maintenance through smoke or rollback.
- A deployment without schema/model changes does not interrupt local sessions.
- Keycloak callbacks use exact `127.0.0.1` origins with no wildcards.
- No infrastructure port is public and no secret is written to the repository.
- The canonical ZM product returns healthy after live verification.
