# Assistant And MCP Spec

Source: `core/src/main/java/com/orgmemory/core/assistant`,
`apps/api/src/main/java/com/orgmemory/api/assistant`,
`apps/mcp/src/main/java/com/orgmemory/mcp`, and
`apps/web/src/features/assistant`.

Reconciled: `2026-08-04-assistant-interaction-foundation (cb05dfc5)`.

## Current Behavior

The in-app Assistant routes chat through the provider-neutral AI gateway and
grounds every answer in the parent `knowledge::search` interface without
importing its Retrieval implementation. GraphRAG is the default retrieval
engine; the canonical hybrid engine is an explicit configuration choice rather
than an implicit fallback. Answers stream with permission-verified citations.
GraphRAG supplies one structured, token-bounded
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

Every turn is observed on its own surface, `orgmemory.assistant.turn`, rather
than through `GraphRagEventSink`: generation runs above an engine-neutral
retrieval interface, so a GraphRAG stage would mislabel a canonical-engine turn.
The surface carries the same payload boundary by construction —
`AssistantTurnEvent` accepts only counts, durations, bounded enumerations and an
organization identifier, and the observation convention reads that record rather
than the mutable context, so it cannot publish a prompt or a completion. There is
no request or conversation identifier on it; correlation to retrieval and to the
model call is by trace context.

Time to first token is recorded as its own distribution,
`orgmemory.assistant.time_to_first_token`, tagged only by engine and measured
from the arriving question rather than from the model call, because
permission-scoped retrieval runs while the user waits. Spring AI's
`gen_ai.client.operation` separately reports generation duration, per-call token
usage and finish reason; it observes inside `ChatModel`, below `ChatModelPort`,
and starts after retrieval, so the two measurements answer different questions.
No meter carries an organization, request or conversation identifier.

Those two meters are load-bearing because the trace does not cover the turn. The
streamed generation runs on a thread the request's trace context does not reach,
so its span becomes the root of a separate trace instead of a child of the HTTP
request — the parent-child limitation Spring AI documents for streaming calls.
A measured turn on 2026-07-31 spent 3.2 of 13.0 seconds in retrieval, fully
broken down by `orgmemory.graph_rag.*`, and the remaining 9.8 seconds carries no
span at all: 75% of the turn. The ChatClient and advisor layer between
`AssistantService` and `ChatModel` is unobserved for a second reason —
`ChatClient.builder(ChatModel)` supplies `ObservationRegistry.NOOP` — but that
layer is a chat-memory read, not the missing time. Turn duration and time to
first token are what answer "how long, and how long until the user saw
something" while the trace cannot.

Assistant chat, direct LightRAG answer generation, Keyword Planning, and
governed Prompt execution resolve their model route with the current
`organizationId` at request time. An organization override selects one
encrypted gateway profile, model id, and optional OpenAI reasoning effort;
absence means the read-only deployment default. An explicit override is
fail-closed, so provider failure does not silently send organization prompts to
a different provider. Graph extraction and embedding remain deployment-managed.
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

The API allocates one server-owned assistant message UUID before streaming and
uses it both in the AI SDK start frame and in the completed transcript row.
Successful stream completion follows transcript persistence; failed, aborted,
and empty turns may consume an UUID but create no feedback target. Completed
answers accept one mutable `HELPFUL` or `NOT_HELPFUL` rating in a separate
message-linked table. Service lookup requires the exact current organization,
actor, and assistant role, while a composite database foreign key repeats the
tenant/actor boundary and conversation deletion cascades the rating. Missing,
cross-actor, cross-tenant, and user-message targets share one opaque not-found
surface. Conversation replay includes the current rating without copying the
question, answer, evidence, or provider output into feedback storage. Feedback
mutations take a pessimistic lock on that owned assistant-message row, which
serializes concurrent set/set and set/delete requests for one answer.

The empty Assistant publishes a small ordered starter list from the API; these
prompts are closed application data and do not infer inaccessible resources or
call a model. The composer keeps at most 4,000 characters per actor and
conversation in browser `sessionStorage`, including a separate new-conversation
draft. Submit, actor change, conversation deletion, and logout clear the
applicable draft state. Drafts are neither server state nor telemetry. Retrying
a completed answer resubmits its immediately preceding user message as a new
linear turn in the same conversation, so retrieval and authorization always run
fresh; it does not overwrite or branch history. If the effective actor changes
without a page reload, the browser hides and clears the previous actor's
messages, feedback selection, and open source panel before loading new history.

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
