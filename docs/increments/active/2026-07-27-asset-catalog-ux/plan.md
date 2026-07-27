# Plan

- [x] Add a paged released-Asset catalog query and HTTP contract.
- [x] Promote Administration pagination to a shared collection component.
- [x] Add URL-backed catalog search, type, sort, and page controls.
- [x] Refine catalog cards and Asset detail consumption hierarchy.
- [x] Add focused repository/service and frontend component tests.
- [x] Regenerate the OpenAPI contract and client, then run backend, frontend,
  and browser gates.
- [x] Consolidate current specs and test evidence.
- [ ] Merge the reviewed pull request and move this increment to completed.

## Verification

- `AssetRegistryIntegrationTests#catalogPagesAndSortsTheLatestAuthorizedReleasesOnTheServer`
- `.\gradlew.bat --no-daemon :core:test`
- `.\gradlew.bat --no-daemon clean test`
- `corepack pnpm -C web typecheck`
- `corepack pnpm --dir web run test:unit`
- `corepack pnpm -C web build`
- `PLAYWRIGHT_PORT=4187` focused Asset golden-flow Playwright test
- missing-package, zero-byte source/config/migration, migration-name, and
  `git diff --check` mechanical gates
