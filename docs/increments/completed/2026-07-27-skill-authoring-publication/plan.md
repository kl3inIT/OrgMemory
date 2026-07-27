# Plan

- [x] Add deterministic local Skill folder inspection and `skill validate`.
- [x] Add the OAuth `assets:write` scope and a bounded publication companion
  endpoint without adding MCP mutation tools.
- [x] Add `skill publish` with dry-run and machine-readable output.
- [x] Reuse canonical Skill Draft creation and governance without a parallel
  lifecycle.
- [x] Add focused CLI, gateway, security, and deployment-contract tests.
- [x] Run backend, CLI, generated-contract, deployment, and static-analysis
  gates.
- [x] Open the pull request and resolve CI/review findings.

## Verification

- `./gradlew --no-daemon clean build`
- `web`: generated API drift, Vitest, lint, typecheck, and production build
- `apps/cli`: Vitest, typecheck, build, and an offline dry-run against an
  unreachable server
- Keycloak onboarding, nginx forwarded-port, shellcheck, production Compose,
  JSON, Bash syntax, and whitespace contracts

## Delivery evidence

- PR #90 implements folder-first Skill validation and governed Draft
  publication.
- GitHub CI passed Backend, Web, CLI, Deployment contracts, and the aggregate
  CI gate.
- CodeRabbit reported no findings because the review allowance was rate
  limited; the project owner explicitly waived waiting for that review.
