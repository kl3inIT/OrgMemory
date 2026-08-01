# MCP search reliability

Date: 2026-07-28

## Outcome

Restore Claude MCP `search_knowledge` calls that fail while the canonical
permission-aware GraphRAG API is still computing a valid response.

## Production evidence

The public MCP discovery and OAuth resource metadata endpoints were healthy,
and Claude successfully initialized the `Anthropic/ClaudeAI` MCP client. During
two failed searches the API logged a committed-response `Broken pipe`; the
first occurred about 22 seconds after initialization. Production did not set
`ORGMEMORY_MCP_REQUEST_TIMEOUT`, so both the MCP server and its API client used
the 20-second default.

The GraphRAG request may perform bounded keyword-planning and embedding provider
calls. Its AI gateway has a 60-second timeout. A 20-second gateway timeout
therefore violates the timeout chain by cancelling a healthy downstream
operation before its own bounded deadline.

## Decision

- Keep connection establishment fail-fast at 5 seconds.
- Give the complete MCP request and its API response 75 seconds: the downstream
  60-second AI provider budget plus bounded authorization, database, rendering,
  and transport overhead.
- Expose both values in the production Compose environment so operators can tune
  them without rebuilding an image.
- Do not bypass GraphRAG, weaken permission rechecks, or replace semantic query
  planning with connector- or language-specific heuristics.

## Exit proof

- Focused property and MCP client tests pass.
- The terminating repository test suite passes.
- Production runs the merged image with a 5-second connect timeout and
  75-second request timeout.
- A Claude-compatible authenticated `search_knowledge` call completes without
  a downstream broken pipe and returns permission-filtered evidence.
