# Assistant And MCP Spec

Source: `core/src/main/java/com/orgmemory/core/assistant`,
`apps/api/src/main/java/com/orgmemory/api/assistant`,
`apps/mcp/src/main/java/com/orgmemory/mcp`, and
`apps/web/src/features/assistant`.

Reconciled: `2026-07-29-mcp-scoped-completion (b79d6ac)`.

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

Assistant chat and governed Prompt execution now resolve their model route with
the current `organizationId`. An organization override selects one encrypted
gateway profile and model id; absence means the read-only deployment default.
An explicit override is fail-closed, so provider failure does not silently send
organization prompts to a different provider. Keyword planning, graph
extraction, and embedding remain deployment-managed in this increment.
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

`apps/mcp` runs a stateless Spring AI MCP server. Its published surface is nine
read-only, closed-world tools, two Asset resource templates, and one released
Prompt adapter. It validates the caller's bearer token and exchanges it for a
short-lived API-audience token instead of forwarding the inbound bearer,
preserving one retrieval, OpenFGA, ACL-recheck, and audit path across the
Assistant, REST, and MCP surfaces. MCP owns no schema migration or privileged
service identity.

Completion is permission-scoped. Every suggestion for a Prompt argument or an
Asset resource-template variable comes from one authorized Asset delivery call
for the current identity, an already-resolved argument narrows the next one, and
a delivery failure yields no suggestion rather than an error, so completion is
never a second existence channel. Suggested values are exact identifiers because
MCP completion returns the literal argument value.

A downstream gateway failure crosses the MCP boundary as a cause-free failure.
The annotation runtime appends the root cause message to the tool error it
returns, so the cause is logged in the server and only the already-sanitized
gateway message reaches the caller.

The stateless protocol carries no server-initiated request, so progress
notifications, logging notifications, elicitation, and sampling are unavailable,
and tool, prompt, and resource listings are registered once at startup rather
than filtered per actor. General chat-turn idempotency remains unimplemented.

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
