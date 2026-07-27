# Source Authorization Sync Correctness Plan

One vertical pull request based on the merge of Source Authorization Core V2.

## Contract And Persistence

- [x] Add generic `CONTENT | PERMISSION | MEMBERSHIP` component state to the
  connector contract.
- [x] Evolve the single checkpoint table to one row per connection/component
  with last-observed and last-successful evidence.
- [x] Keep `COMPLETE | INCOMPLETE` capture semantics separate from technical
  attempt outcomes.

## Reconciliation

- [x] Process only component cursors that have not already been observed.
- [x] Attribute per-item failures to blocked components and add `PARTIAL`.
- [x] Advance only components that completed; keep failed components pending.
- [x] Refuse ACL rotation/content materialization from incomplete permission
  capture while allowing diagnostic incomplete membership evidence.

## Retrieval And Operations

- [x] Remove the GraphRAG-only ACL-expiry denial and pin ADR 0015 parity.
- [x] Expose component checkpoint status, reason, observation time, and last
  successful time through connection activity.
- [x] Consolidate architecture/spec/test documents only after implementation.

## Verification

- [x] Add domain and PostgreSQL/Testcontainers coverage for independent
  cursors, incomplete evidence, partial retry, and stale ACL parity.
- [x] Run backend inspection when available, compilation, focused suites,
  `clean test`, generated API drift, and web lint/typecheck/build.
- [x] Move this increment to `completed` and prepare one ready PR after all
  local gates. Review, CI, merge, and `origin/main` verification are tracked on
  the GitHub pull request rather than predicted in this source document.

Claude review remains waived by the project owner because its account quota is
exhausted; this does not waive tests, CodeRabbit when available, or CI.

## Delivered

- One generic checkpoint table now stores observed and last-successful state per
  content, permission, and membership component.
- Slack and Google Drive emit independent component cursors; Google Directory
  membership limitation is durable `INCOMPLETE` evidence.
- Item failure produces `PARTIAL`, advances only successful components, and
  remains pending; incomplete permission evidence rotates no ACL and does not
  hot-loop inside one poll.
- Canonical retrieval and PostgreSQL GraphRAG both apply ADR 0015.
- Connection activity API and UI show component capture status, reason, and
  last-successful time.

## Verification

- `.\gradlew.bat --no-daemon clean test` — green, 93 actionable tasks.
- `corepack pnpm -C web check:api` — generated API client current.
- `corepack pnpm -C web build` — lint, typecheck, and production build green.
- Contract, migration-name, zero-byte, and `git diff --check` mechanical gates
  — green.
- JetBrains inspection was unavailable as a trustworthy gate because the
  connected IDE indexed a different repository; clean compilation and the full
  terminating suite remain authoritative here.
