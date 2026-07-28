# Multi-provider Model Control Plane Verification

## Scope

Verified from `origin/main` commit `d7ca979` on
`feat/multi-provider-model-settings`.

The increment adds organization-scoped chat-provider profiles, encrypted
credentials, explicit Assistant and prompt-execution routes, native
OpenAI-compatible and Anthropic dispatch, an administrator control plane, and
a read-only embedding settings surface. Embedding geometry remains
deployment-managed.

## Backend

- `.\gradlew.bat --no-daemon build -x :integrations:graph-rag-postgres:test -x :integrations:graph-rag-opensearch:test -x :integrations:graph-rag-neo4j:test`
  passed.
- `:apps:worker:cleanTest :apps:worker:test` passed after an earlier timed-out
  shell left a stale Gradle in-progress result file.
- Focused core administration and AI adapter/probe/endpoint-policy tests
  passed.
- `PermissionsAdminIntegrationTests#aiControlPlaneActorReferencesCannotCrossTenantBoundaries`
  passed against PostgreSQL and proves profile, credential, and route actor
  references cannot cross tenants.
- Both `OpenApiContractTests` passed after regenerating
  `contracts/openapi.json`; the generated TypeScript client has no drift.

## Authorization

- `fga model validate` reported `is_valid=true`.
- `fga model test --tests store.fga.yaml` passed:
  - tests: 9/9;
  - checks: 69/69;
  - ListObjects: 29/29.

Only the organization administrator receives `can_manage_ai`.

## Frontend

- `pnpm --filter @orgmemory/web lint` passed.
- `pnpm --filter @orgmemory/web typecheck` passed.
- `pnpm --filter @orgmemory/web check:api` passed.
- `pnpm --filter @orgmemory/web build` passed.
- Vitest passed 20/20 tests.
- Playwright passed 12/12 tests, including the committed Language Models test
  for provider grouping, redacted credential entry, and restoring a deployment
  route.

The local machine uses Node 23.11.1 and therefore prints the repository's
`node >=24` warning. GitHub CI is the authoritative Node 24 environment.

## Documentation and Static Gates

- `python scripts/check_docs.py` passed for 281 Markdown files and eight
  mirrored domain pairs.
- Java package declarations, zero-byte Java/YAML/SQL files, Flyway migration
  naming/duplication, and `git diff --check` passed.
- JetBrains MCP was unavailable in this session, so the repository static
  analysis skill used its documented mechanical and compiler/test fallback.
- Spring Boot 4.1 and Spring AI 2.0.0 symbols were verified against official
  documentation and exact local dependency JARs. Context7 was attempted first,
  but its monthly quota was exhausted.

## Security and Failure Proof

- Credentials remain write-only ciphertext and sensitive request `toString()`
  output is redacted.
- Direct-provider endpoints are fixed; custom origins require exact
  deployment allowlisting.
- Model discovery uses bounded timeouts, no redirects, address filtering,
  bounded response reads, bounded model counts, and stable error codes.
- Explicit organization routes fail closed. A colliding organization gateway
  key cannot replace a deployment default.
- Metadata plus optional credential rotation is one service transaction.
- Cache keys include organization, workload, route, protocol, and runtime
  revision; superseded organization entries are evicted.

## Operator Evidence Still Required

No real OpenAI, Anthropic, 9Router, or other provider credential was available
to this deterministic verification run. An operator must still allow the
intended custom origin where applicable and run the built-in connection test
with a managed credential in the target environment.

## Remote Delivery

- Pull request [#113](https://github.com/kl3inIT/OrgMemory/pull/113) was
  rebased onto `origin/main` at `eb870a9`.
- GitHub CI passed its aggregate gate plus Backend Java 25, Web Node 24,
  Public docs Node 24, PostgreSQL GraphRAG, OpenFGA, CLI, and documentation
  jobs.
- CodeRabbit reported success but explicitly skipped review because its review
  limit was reached; it produced no review thread or actionable comment. The
  project owner had already authorized merge on green CI in this condition.
