---
name: orgmemory-create-test
description: Create or update focused tests for OrgMemory backend services, REST controllers, Flyway/JPA behavior, Spring Modulith boundaries, or frontend flows. Use when adding business behavior, changing ingestion/publication/versioning rules, or fixing a regression.
---

# OrgMemory Create Test

Add the smallest test that protects the behavior being changed.

## Backend

Prefer service tests for domain behavior and controller tests for HTTP contract
behavior.

Test these knowledge lifecycle rules when touched:

- a stable Knowledge Asset owns immutable monotonically numbered versions,
- at most one version is active and current for an asset,
- publication commits pending PostgreSQL evidence before calling OpenFGA,
- authorization failure leaves retryable outbox evidence and no visible chunks,
- changed source content creates revision N+1 while identical content is idempotent,
- retrieval pins current source revision, current version, ACL/model/profile,
- JPA mappings match forward-only Flyway migrations.

Select the relevant layered gates from `docs/guidelines/testing-harness.md`.
For changes spanning ingestion, publication, persistence, worker wiring, or the
frontend contract, run at least:

```powershell
.\gradlew.bat --no-daemon :core:test :apps:api:test :apps:worker:test
.\gradlew.bat --no-daemon clean test
corepack pnpm --filter @orgmemory/web typecheck
corepack pnpm --filter @orgmemory/web test:unit
corepack pnpm --filter @orgmemory/web build
```

The terminating `clean test` command is the Spring context gate. Never use
`bootRun` as verification because it does not terminate on success.

## API Contract

When a REST endpoint or DTO changes, the committed contracts under
`contracts/` must follow (mechanism documented on
`apps/api/.../OpenApiContractTests.java`):

1. Refresh: set `ORGMEMORY_OPENAPI_WRITE=true` and run
   `.\gradlew.bat :apps:api:test --tests "*OpenApiContractTests*"`.
2. Regenerate the browser client:
   `corepack pnpm --filter @orgmemory/web gen:api` — the hey-api output is
   gitignored, so it is regenerated, never committed.
3. Verify: the same contract test without the write flag must pass, then run
   the web typecheck.

Skipping this after an endpoint change either breaks web typecheck or ships a
stale committed contract.

## Frontend

Frontend tests are established; extend them instead of deferring.

- Unit: Vitest `*.test.ts(x)` files colocated with the component or feature
  logic under `apps/web/src/**`. Cover pattern components, feature state, and
  view-model mapping. Run `corepack pnpm --filter @orgmemory/web test:unit`.
- E2E: Playwright specs in `apps/web/test/e2e/`. Cover the product flow the
  change touches; run one spec while iterating
  (`corepack pnpm --filter @orgmemory/web exec playwright test test/e2e/<name>.spec.ts --project=chromium`)
  and the full `test:e2e` before handoff when flows changed.
- A real browser pass per `docs/conventions.md` still applies when a flow
  matters and no spec covers it yet.

## Test Quality

- Do not assert implementation details when domain behavior is enough.
- Use seed-like examples from OrgMemory: employee onboarding, leave policy,
  expense claims, product releases, and restricted financial forecasts.
- Keep tests deterministic; no live LLM calls in tests.
