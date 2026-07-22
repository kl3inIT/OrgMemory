# Assistant And MCP Spec

## Current Behavior

The API exposes prototype Spring AI normalization/chat backed directly by the
OpenAI starter when enabled, with demo-safe local fallback. The registry-based
Ask flow does not use the permission-aware Knowledge Asset retrieval service.
Conversation memory, provider-neutral routing, durable turn idempotency, shared
tool publication, and grounded knowledge citations are not implemented.

The `apps/mcp` module is reserved, but has no runtime implementation. Its legacy
scaffold was removed so the future server can publish only production Knowledge
and Capability tools backed by the shared permission-aware use cases.

## Source Modules

- `apps/api.ai`
- `apps/mcp`
- Spring AI dependency in `core`

## Related Decisions

- [0006](../../decisions/0006-ai-tasks-route-through-provider-adapters.md)
- [0008](../../decisions/0008-worker-owns-ingestion-and-derived-indexes.md)
