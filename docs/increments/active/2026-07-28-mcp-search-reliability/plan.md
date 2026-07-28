# MCP search reliability plan

## 1. Diagnose the live path — complete

- Verify MCP discovery, OAuth metadata, health, deployment SHA, and client
  initialization.
- Correlate Claude attempts with MCP and API logs.
- Confirm the effective production timeout and downstream GraphRAG budget.

## 2. Repair the timeout chain — in progress

- Split connect and response timeouts.
- Raise the bounded MCP request budget to 75 seconds.
- Pass both settings through production Compose.
- Add regression tests for safe defaults and invalid values.

## 3. Verify and release — pending

- Run focused tests, edited-Java static analysis, and the terminating full suite.
- Open the PR and process actionable CodeRabbit/CI feedback.
- Merge, deploy the immutable image, and verify the production MCP call.
