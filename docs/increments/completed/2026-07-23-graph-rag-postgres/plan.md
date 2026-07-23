# Plan

1. [x] Add the pinned PostgreSQL 18 + pgvector + Apache AGE image, shared-instance
   OpenFGA database bootstrap, and extension checks.
2. [x] Add Flyway schema for published graph heads, canonical identities,
   evidence-level contributions and entity/relation vector stores.
3. [x] Implement atomic revision replacement/removal with monotonic generation checks,
   canonical identity conflict detection, bounded batching and advisory locks.
4. [x] Implement permission-scoped relational reads, vector seeds, replaceable
   vector index strategies, and AGE topology projection/traversal; retain FTS
   and bounded recursive traversal as fallback channels.
5. [x] Prove tenant isolation, pre-rank ACL filtering, relation endpoint visibility,
   atomic replacement, vector dimension/profile safety, AGE projection rebuild and
   generation rollback denial with PostgreSQL Testcontainers.
6. [x] Run backend static-analysis fallback and the full repository verification gates.

## Verification

- Full Gradle build: 166 tests, 0 failures, 0 errors.
- PostgreSQL integration tests use the combined PostgreSQL 18, pgvector 0.8.2,
  and Apache AGE 1.7.0 image.
- Fresh-volume and existing-volume OpenFGA database bootstrap paths were verified.
- `docker compose config --quiet` and `git diff --check` pass.
- JetBrains inspection was attempted but unavailable because the local IDE MCP
  transport was not reachable; Gradle compile/test and repository fallback checks
  were used instead.
