# Source Authorization Core V2 Plan

One vertical pull request, based on the latest `origin/main`.

## Contract And Persistence

- [x] Rename the canonical source identity vocabulary from external key to stable
  native principal ID without using email as identity.
- [x] Split membership capture from identity observation and version it
  independently.
- [x] Add immutable membership snapshots, members, seals, and an atomic
  per-group head through Flyway and matching JPA mappings.
- [x] Make only sealed `COMPLETE` evidence activatable; preserve an
  `INCOMPLETE` reason without widening access.
- [x] Remove the per-document `source_acl_group_members` authority.

## Ingestion And Connectors

- [x] Reconcile membership once per group/crawl before resource ACL objects.
- [x] Make unchanged resource ACLs no-op even when membership changes.
- [x] Emit Slack identities and membership using Slack-native IDs.
- [x] Emit Drive identities using provider-native grantee IDs; keep email as an
  alias and mark unenumerated Google groups incomplete.
- [x] Reject unsupported nested membership and invalid/cross-connection evidence
  fail closed.

## Retrieval, Administration, And Audit

- [x] Resolve source group grants through the active sealed membership head
  before ranking.
- [x] Preserve `DENY` precedence and the AppUser/OpenFGA/classification/tenant
  intersection.
- [x] Replace the admin generation guess with the canonical membership head and
  expose its snapshot ID/generation in permission administration.
- [x] Preserve the existing retrieval audit's decisive resource ACL snapshot.
  A combined ACL-plus-membership decision explanation is explicitly follow-up
  work, not a hidden exit criterion for this increment.

## Verification

- [x] Add deterministic domain and PostgreSQL/Testcontainers tests for snapshot
  activation, incomplete evidence, hash/seal integrity, revocation without ACL
  rotation, unmapped/cross-tenant denial, and connector fixtures.
- [x] Run backend Java inspection when available, Gradle compilation, focused
  core/API/worker/connector tests, and `.\gradlew.bat --no-daemon clean test`.
- [x] Update architecture/specs/tests only after the behavior exists; update the
  roadmap when the increment is moved to `completed`.
- [x] Move this increment to `completed` and prepare one ready end-to-end PR.
- [ ] Resolve actionable CodeRabbit feedback, require green CI, merge to `main`,
  and verify the merged SHA.

## Verification Notes

- JetBrains inspection was attempted for the edited backend Java but the
  connected IDE indexed a different repository, so its result is unavailable
  and untrusted. The documented Gradle fallback gates are required.
- The project owner waived the usual Claude review because its account quota was
  exhausted. This does not waive tests, GitHub CI, or actionable CodeRabbit
  review.
- `compileJava`, full `:core:test`, the connector/API/worker/GraphRAG focused
  suites, and the terminating multi-module `clean test` passed against
  PostgreSQL/pgvector Testcontainers.
- Web Oxlint, TypeScript typecheck, production build, generated-API drift, JSON
  contract parsing, Flyway naming, package declarations, zero-byte checks, and
  `git diff --check` passed.
