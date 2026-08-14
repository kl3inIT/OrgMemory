# Assistant Private Files Verification

## Candidate

- Branch: `feat/assistant-private-files`
- Worktree: `D:\OrgMemory-worktrees\assistant-private-files`
- Implementation commit: `92448337`
- Independent challenge: one Fable 5 response, verdict `REVISE`; accepted
  conditions are recorded in `challenge-verdict.md` and decision 0039.
- Malware/DLP: explicitly waived for this increment after the pinned Onyx
  upload path was verified not to contain either gate.

## Passed gates

- Focused core, worker, API, migration, scheduler, citation, and conversation
  tests passed while iterating.
- `./gradlew.bat --no-daemon test` — passed, 86 actionable tasks, 10m32s.
- OpenAPI contract generation and committed-contract comparison — passed.
- `corepack pnpm check:web` on Node 24.15.0 — passed: 33 files and 113 unit
  tests, typecheck, Oxlint, generated-client drift check, and production build.
- `corepack pnpm exec playwright test test/e2e/assistant-pipeline.spec.ts
  --workers=1` — passed, 27/27 browser tests, including private upload, recent
  file reuse, and the private-versus-governed lane boundary.
- `corepack pnpm release:check` — passed before final browser-test additions;
  the Tegami entry and release policies have not changed since that pass.
- `git diff --check` — passed before documentation consolidation and must be
  repeated on the committed candidate.
- Local dev dependencies, API migration, and `/api/health` — passed on the
  isolated worktree stack.

## Delivery proof

- PR #353 merged as `af7ed3acfa27477c851681e62042938cd01de1c6`
  after latest-head CI run `31561250272` passed; CodeRabbit was rate-limited,
  reported no findings, and the documented green-CI fallback was used.
- Green-main CI run `31561630011`, production image run `31561998985`, and
  production deployment run `31562289377` passed for exact source commit
  `af7ed3acfa27477c851681e62042938cd01de1c6`.
- Tegami Version Packages PR #354 merged as
  `e7febec673c0769f973018c78cb9e5517cd49d0d`. Release run `31562799544`
  published `v0.4.0` at that exact tag target with the immutable
  `artifacts.json`; idempotent rerun `31562927295` passed.
- Docs image run `31562799536` and docs deployment run `31562905206` passed for
  the release commit. Live checks returned HTTP 200 from the product and docs
  health endpoints, and the deployed changelog exposed v0.4.0.
- The isolated feature worktree and local feature branch were removed after
  exact-SHA deployment and live runtime proof.

## Residual constraints

- The owner approved the production-build attachment candidate on 2026-08-12.
  The sanitized screenshot used the real built UI with authenticated API
  fixtures and showed a selected private file, READY recent-file reuse, the
  actual model name, and disabled `Publish to Knowledge` while the private lane
  was selected. Authentication UI was unchanged by this increment.
- The repository demo directory fixture is stale against the current fresh-DB
  schema (`organizations` is absent and `app_users.role` no longer exists).
  Local runtime verification used one schema-correct synthetic identity only;
  fixing the unrelated fixture is outside this increment.
- IDE inspection remained unavailable; the accepted Gradle, frontend static,
  browser, latest-head CI, deployment, and live runtime evidence supersedes that
  tooling gap for this delivered increment.
