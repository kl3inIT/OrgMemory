# Assistant And MCP Spec

## Current Behavior

The in-app Assistant routes chat through the provider-neutral AI gateway and
grounds every answer in `PermissionAwareKnowledgeSearch`. GraphRAG is the
default retrieval engine; the canonical hybrid engine is an explicit
configuration choice rather than an implicit fallback. Answers stream with
permission-verified citations. GraphRAG supplies one structured, token-bounded
grounding set containing entity, relation, and chunk contributions. The
application rechecks its complete evidence closure through OpenFGA and the
canonical ledger before the pure-Java renderer creates the final model prompt.
`AssistantService` sends that already-verified prompt through `ChatModelPort`;
it does not construct a second chunk-only prompt or invoke a Spring AI retrieval
advisor. The server assigns each citation number while rendering the same
verified closure and streams that number as provider metadata. The browser makes only those declared
markers interactive; an undeclared `[n]` remains literal text. Citation content
is read through an authenticated backend endpoint instead of exposing
object-storage URLs. Every open performs one fresh canonical authorization and
integrity check; missing, changed, and denied citations are wire-equivalent
opaque `404` responses.
The citation response derives its media type from a closed extension allowlist,
never from upload metadata. Text, PDF, and known raster images may render
inline; Office and unknown formats are forced to download as binary content.

The source panel treats citation content as server state. It deduplicates an
in-flight open, does not retry an authorization failure, does not show stale
content during a recheck, and discards the cached blob when the panel releases
the source. Text, image, and PDF previews use browser-local object URLs; the
external object-store address never reaches the browser.

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
