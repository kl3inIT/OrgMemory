# Assistant And MCP Spec

## Current Behavior

The in-app Assistant routes chat through the provider-neutral AI gateway and
grounds every answer in `PermissionAwareKnowledgeSearch`. GraphRAG is the
default retrieval engine; the canonical hybrid engine is an explicit
configuration choice rather than an implicit fallback. Answers stream with
permission-verified citations, and citation content is read through an
authenticated backend endpoint instead of exposing object-storage URLs.

`apps/mcp` runs a stateless Spring AI MCP server with one read-only,
closed-world `search_knowledge` tool. It validates the caller's bearer token and
forwards that same token to `/api/knowledge/search`, preserving one retrieval,
OpenFGA, ACL-recheck, and audit path across the Assistant, REST, and MCP
surfaces. MCP owns no schema migration or privileged service identity.

Durable conversation memory, turn idempotency, mutation tools, and agent tool
traces remain unimplemented.

## Source Modules

- `apps/api.assistant`
- `core.knowledge`
- `apps/mcp`
- Spring AI MCP server in `apps/mcp`

## Related Decisions

- [0006](../../decisions/0006-ai-tasks-route-through-provider-adapters.md)
- [0008](../../decisions/0008-worker-owns-ingestion-and-derived-indexes.md)
