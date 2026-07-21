# Assistant And MCP Spec

## Current Behavior

The API exposes prototype Spring AI normalization/chat backed directly by the
OpenAI starter when enabled, with demo-safe local fallback. The registry-based
Ask flow does not use the permission-aware Knowledge Asset retrieval service.
Conversation memory, provider-neutral routing, durable turn idempotency, shared
tool publication, and grounded knowledge citations are not implemented.

The MCP application starts from the shared core/schema but remains a scaffold;
it does not yet publish production Knowledge or Capability tools.

## Source Modules

- `apps/api.ai`
- `apps/mcp`
- Spring AI dependency in `core`

## Related Decisions

- [0006](../../decisions/0006-ai-tasks-route-through-provider-adapters.md)
- [0008](../../decisions/0008-worker-owns-ingestion-and-derived-indexes.md)
