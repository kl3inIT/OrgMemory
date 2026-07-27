# Plan

- [x] Add actor-specific Asset Governance action discovery in Core and REST.
- [x] Refresh the committed OpenAPI contract and generated browser client.
- [x] Return and print the exact Governance URL from Skill publication.
- [x] Add capability-aware Draft submission and Skill package summary to the
  Governance workspace.
- [x] Hide review, publish, deprecate, and withdraw actions when unavailable.
- [x] Add focused backend, CLI, Vitest, and Playwright coverage.
- [x] Run backend, frontend, generated-contract, and static-analysis gates.
- [ ] Open the pull request and resolve CI/review findings.

## Verification

- `.\gradlew.bat :core:test :apps:api:test`
- `.\gradlew.bat --no-daemon clean test`
- `corepack pnpm -C apps/cli test`
- `corepack pnpm -C apps/cli typecheck`
- `corepack pnpm -C apps/cli build`
- `corepack pnpm -C web check:api`
- `corepack pnpm -C web lint`
- `corepack pnpm -C web typecheck`
- `corepack pnpm --dir web run test:unit`
- `corepack pnpm -C web build`
- focused Playwright Governance handoff flow
