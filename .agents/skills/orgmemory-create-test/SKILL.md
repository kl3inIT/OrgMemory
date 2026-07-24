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
corepack pnpm -C web typecheck
corepack pnpm -C web build
```

The terminating `clean test` command is the Spring context gate. Never use
`bootRun` as verification because it does not terminate on success.

## Frontend

For now, prefer type-safe component logic and manual Playwright verification over
heavy test scaffolding. Add frontend tests once there are stable flows worth
protecting.

## Test Quality

- Do not assert implementation details when domain behavior is enough.
- Use seed-like examples from OrgMemory: employee onboarding, leave policy,
  expense claims, product releases, and restricted financial forecasts.
- Keep tests deterministic; no live LLM calls in tests.
