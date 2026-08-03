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

## Delivery proof

- PR [#284](https://github.com/kl3inIT/OrgMemory/pull/284) merged as
  `641b7adfd09967452fcb3eb302237720673a5630` after the main CI run
  `30784149363` and immutable production image run `30784371771` completed
  successfully, including the Linux deployment-contract harness.
- Automatic deploy run `30784671864` timed out opening SSH from its hosted
  runner before any remote mutation. The same repository deploy script was
  then run over the established operator SSH path with the exact merged image
  set; backup, one-shot, stack health, OpenFGA/Keycloak configuration, and
  production smoke all passed.
- The one-shot enumerated 49 candidates, repaired all 49 missing ready-marker
  batches, skipped none, exited successfully, and left one organization graph
  in the AGE catalog where the pre-deployment count was zero.
- `/apps/orgmemory-runtime/current-commit`, the server checkout, API, worker,
  web, and MCP images all resolve to the merge SHA. API, web, and MCP were
  healthy; the worker was running; post-cutover API/worker logs contained no
  runtime error, OOM, or killed-process event.
- An authenticated production browser opened Company Knowledge in the Graph
  explorer with 92 entities and 129 relations. A fresh Assistant request
  answered the P0 acknowledgement question as 15 minutes, cited
  `support.sla-and-escalation@1`, opened the cited-source panel, and previewed
  the underlying document content.

The explicit one-shot emitted a non-fatal Spring Modulith
`eventPublicationRegistry` destroy warning while its deliberately short-lived
application context closed. Reconciliation had already completed, the process
exited zero, and the normal API context starts and remains healthy without that
warning.
