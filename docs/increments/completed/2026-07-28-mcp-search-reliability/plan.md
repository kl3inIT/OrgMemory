# MCP search reliability plan

## Status

Completed in pull requests
[#98](https://github.com/kl3inIT/OrgMemory/pull/98) and
[#99](https://github.com/kl3inIT/OrgMemory/pull/99). Production verification
is recorded in [verification.md](verification.md).

## 1. Diagnose the live path — complete

- Verify MCP discovery, OAuth metadata, health, deployment SHA, and client
  initialization.
- Correlate Claude attempts with MCP and API logs.
- Confirm the effective production timeout and downstream GraphRAG budget.

## 2. Repair the timeout chain — complete

- [x] Split connect and response timeouts.
- [x] Raise the bounded MCP request budget to 75 seconds.
- [x] Pass both settings through production Compose.
- [x] Add regression tests for safe defaults, zero, and negative values.

## 3. Verify and release — complete

- [x] Run focused tests, the repository static floor, and terminating suite.
- [x] Merge PR #98 and its reviewed reliability follow-ups to `main`.
- [x] Deploy the immutable image and verify the production MCP call.
