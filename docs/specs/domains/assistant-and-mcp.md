# Assistant And MCP Spec

Source: `core/src/main/java/com/orgmemory/core/assistant`,
`apps/api/src/main/java/com/orgmemory/api/assistant`,
`apps/mcp/src/main/java/com/orgmemory/mcp`, and
`web/src/features/assistant`.

Reconciled: `2026-07-29-repository-operating-model-refresh (7cf1c8a)`.

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

Assistant conversations have two deliberately separate stores. The
tenant-and-actor-owned transcript keeps the complete user/assistant history for
list, replay, rename, and delete. Spring AI `MessageWindowChatMemory` keeps only
the recent context sent back to the model. Current permission-verified grounding
and bounded server user context are placed in the current system message; the
memory advisor persists the raw user question and assistant answer, not copied
evidence. Every new turn performs a fresh authorized retrieval. Historical
answers remain a snapshot of what the user received at that time, while opening
a citation still rechecks current access. A future purge-on-revocation rule is a
separate retention policy, not a prerequisite for ordinary multi-turn chat.

The in-app Asset Assistant boundary is a separate, closed action allowlist. It
can recommend authorized exact releases, search canonical Knowledge, prepare,
render, and run Prompt Templates, guide Work Instructions, start/read/update a
Pack, fork a release, and submit feedback. Assistant-proposed external calls
and mutations require one explicit confirmation; a direct user click is already
the confirmation and does not add another modal. Tool descriptions are
descriptive metadata only; service authorization and confirmation checks remain
authoritative.

Every Asset action appends an actor-scoped trace with exact release references,
citation identifiers, model route where applicable, authorization context, and
sanitized request/outcome metadata. Raw Prompt variables, provider output,
tokens, and credentials are not persisted in the trace. There is no Assistant
action for approval, publication, withdrawal, role/permission changes, or
arbitrary tool/code execution.

`apps/mcp` runs a stateless Spring AI MCP server with one read-only,
closed-world `search_knowledge` tool. It validates the caller's bearer token and
forwards that same token to `/api/knowledge/search`, preserving one retrieval,
OpenFGA, ACL-recheck, and audit path across the Assistant, REST, and MCP
surfaces. MCP owns no schema migration or privileged service identity.

General chat-turn idempotency remains unimplemented. The public MCP surface
still exposes only Knowledge search; Asset resources, prompts, and read-only
tools are deferred to the authenticated public MCP increment.

## Source Modules

- `apps/api.assistant`
- `core.assistant.AssistantAssetToolService`
- `core.assistant.AssistantConversationService`
- `core.knowledge`
- `web.features.assets`
- `apps/mcp`
- Spring AI MCP server in `apps/mcp`

## Related Decisions

- [0006](../../decisions/0006-ai-tasks-route-through-provider-adapters.md)
- [0008](../../decisions/0008-worker-owns-ingestion-and-derived-indexes.md)
