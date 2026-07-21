# Testing Harness

Use a layered gate:

1. Static: IDE inspection when available, Gradle compile, web typecheck, migration
   naming, and zero-byte/package checks.
2. Domain: unit tests and Spring Modulith boundary verification.
3. Integration: PostgreSQL/Testcontainers for schema, ACL, outbox, idempotency,
   retrieval order, and fail-closed behavior.
4. Contract: versioned Edge/upload/connector payload fixtures and provider adapter
   tests.
5. Conformance: deterministic Java graph fixtures compared with pinned LightRAG
   semantics in allow-all mode plus OrgMemory security-specific tests.
6. Runtime: terminating Spring context test, then health and real browser flows.
7. Security: two-user negative tests proving denied content, metadata, graph, MCP,
   citation, and export paths do not leak.

OpenFGA model changes additionally require `model validate` and `model test` with
the repo-local CLI from the directory containing the test file. Java adapter
tests do not replace model tests.

A successful model answer is not evidence of correct authorization. Verify the
selected evidence set and audit decision independently.

GitHub CI mirrors the repository gates with three independent jobs: Gradle
`clean build`, pnpm typecheck/build, and the pinned OpenFGA model test. A stable
`CI Gate` aggregates those jobs for branch protection. Workflow permissions
remain read-only, action dependencies are pinned to full commit SHAs, and
superseded runs on the same pull request are cancelled.
