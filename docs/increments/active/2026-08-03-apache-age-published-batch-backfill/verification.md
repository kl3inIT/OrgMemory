# Apache AGE Published-Batch Backfill Verification

## Local gates

- `./gradlew.bat :integrations:graph-rag-postgres:test` passed all 27 module
  tests, including the PostgreSQL 18 + AGE 1.8.0 cutover and replay proof.
- `./gradlew.bat --no-daemon compileJava` passed for the whole multi-project
  build.
- `./gradlew.bat :apps:api:test --rerun-tasks` passed the complete API suite,
  including the terminating PostgreSQL context test.
- `./gradlew.bat --no-daemon test` passed all 81 Gradle test tasks.
- Mechanical package, zero-byte source/config, migration-name, `git diff
  --check`, shell syntax, and production Compose `config --quiet` checks passed.
- The Linux deployment harness could not execute under Git Bash on Windows
  because GNU `install -m` cannot apply the requested mode to its NTFS temp
  directory. Both changed shell files pass `bash -n`; CI Linux remains the
  executable deployment-contract gate.
- JetBrains MCP inspection was unavailable for the clean worktree, so the
  required Gradle and mechanical fallback gates were used.

## Production sizing evidence

Read-only SQL over the 2026-08-03 production database measured 49 retained
published graph batches, 3,450 total graph entities, and 5,084 total relation
contributions. The largest batch contains 507 entities and 786 relation
contributions. No unresolved relation identity or endpoint was observed, and
the AGE catalog still contained zero organization graphs before deployment.

The production defaults of 1,000 batches, 1,000,000 entities, and 1,000,000
relation contributions therefore leave ample bounded headroom without making
the operation unbounded.

## Pending delivery proof

- GitHub CI and the Linux deployment harness.
- Immutable image deployment and one-shot exit result.
- AGE ready-marker count plus authenticated Graph explorer and Assistant
  citation browser flows.
