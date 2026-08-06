# Assistant And MCP Spec

Source: `core/src/main/java/com/orgmemory/core/assistant`,
`core/src/main/java/com/orgmemory/core/ai`,
`core/src/main/java/com/orgmemory/core/assetregistry/skill`,
`integrations/ai-model-gateways`,
`apps/api/src/main/java/com/orgmemory/api/assistant`,
`apps/mcp/src/main/java/com/orgmemory/mcp`, and
`apps/web/src/features/assistant`, and
`apps/web/src/components/ai-elements/model-selector.tsx`.

Reconciled: `2026-08-06-assistant-skill-activity-receipt (64221f86)`.

## Current Behavior

The in-app Assistant routes chat through the provider-neutral AI gateway and
grounds every answer in the parent `knowledge::search` interface without
importing its Retrieval implementation. GraphRAG is the default retrieval
engine; the canonical hybrid engine is an explicit configuration choice rather
than an implicit fallback. Answers stream with permission-verified citations.
Both model-backed answer paths receive one shared behavior policy: answer in
the user's language and voice, do not speculate about restricted-resource
existence or expose pipeline internals, label neighboring authorized material
instead of presenting it as the requested answer, and cite all and only the
evidence used. Empty authorized evidence remains model-free and citation-free;
the service returns a bounded Vietnamese or English not-found sentence plus one
escalation sentence. The browser places a static evidence-scope disclosure
under Assistant messages only.
GraphRAG supplies one structured, token-bounded
grounding set containing entity, relation, and chunk contributions. The
application rechecks its complete evidence closure through OpenFGA and the
canonical ledger before the pure-Java renderer creates the final model prompt.
`AssistantService` sends that already-verified prompt through the selected
model port; it does not construct a second chunk-only prompt or invoke a Spring
AI retrieval advisor. An exact administrator-authorized Assistant route enters
the request-local agent model port. Before the first model call, that port
loads at most ten live-`CAN_USE` Skill summaries for the current actor and
discloses their names, descriptions, versions, and exact release identifiers
in the `activate_skill` tool description. The model semantically matches the
user's task against this tier-one catalog, activates an exact release before
applying its instructions, and uses actor-scoped `search_skills` only when the
disclosed catalog has no match. One bounded exact-release UTF-8 resource-read
tool completes progressive disclosure. When the actor has no usable Skills,
the port omits both the Skill tools and Skill policy. Spring AI's streaming
`ToolCallingAdvisor` performs a bounded recursive loop. A per-turn tool-call
budget supplies a second bound; a model response without tool calls retains
the ordinary text path. Skill instructions and resources are
untrusted model context; they cannot grant tools or permissions and package
scripts are never executed. Empty authorized Knowledge retrieval still stops
before model generation, so the beta augments grounded turns rather than
creating an uncited Skill-only answer path. The server assigns each citation
number while rendering the same
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

An unavailable turn is tagged with `failure_code`, a bounded machine code naming
why it was unavailable — `assistant_retrieval_rejected` for scheduler
saturation, `assistant_turn_failed` before the stream begins,
`assistant_stream_failed` and `assistant_stream_cancelled` after it does — and
`none` when the turn answered. The exception class alone does not distinguish
them, because saturation and downstream failure are both
`AssistantUnavailableException`. The code travels on that exception and is
validated against the same bounded pattern the turn event enforces, so it stays
safe as a meter tag. An unavailable turn is also logged at `WARN` with its
failure code and cause: it is a `BusinessException`, which the API layer answers
without logging, so nothing else records it.

A failed turn ends on a sentence naming what the caller can do about it, not a
single opaque frame. Retrieval-scheduler saturation is read from the bounded
failure code carried on the exception, because that failure is raised before any
gateway is contacted and never had a status; every other case is keyed on the
leading HTTP status the client puts on the message, so the delivery layer never
depends on provider exception types. Every returned sentence is a fixed string
and nothing is interpolated from the failure, so a chatty or misconfigured
gateway cannot echo a key, a prompt fragment, or provider internals into the
browser. An unrecognized failure still ends on the opaque generic sentence.

Blocking permission-scoped retrieval runs on an Assistant-owned fixed scheduler
with configured concurrency, a finite queue, sanitized overload rejection, and
bounded shutdown. The server begins the UI message stream before scheduling
retrieval and emits only transient closed activity values for retrieval active,
retrieval complete with an already-authorized evidence count, generation
active, and Skill discovery, activation, or resource-read active/complete/failed
states. Skill discovery may include only an authorized result count. These
events contain no question, source identity, reasoning, tool payload, or raw
error and are not persisted. Only a successful actor-authorized exact-release
activation may add a server-sanitized plain-text Skill title, capped at 80
characters, plus a positive turn-local ordinal. Discovery and failure remain
unnamed. Resource activity carries an ordinal only for an exact release already
activated successfully in the same turn. The browser never infers completion
from missing activity phases.

Browser-owned copy replaces the activity on phase changes. A turn-local latch
keeps the waiting row mounted until Assistant text is actually visible; a
source frame alone is not visible citation output. This remains true even when
the transport has already finished; abort, error, stop,
actor/conversation change, and finish-without-output are terminal. Successful
activation creates a plain-text current-turn receipt before the answer with
fixed UI-owned step labels. It auto-collapses at visible output and is cleared
on the next submit or terminal context change; transcript replay does not
reconstruct it. The waiting UI has no leading product icon.

Time to first token is recorded as its own distribution,
`orgmemory.assistant.time_to_first_token`, tagged only by engine and measured
from the arriving question rather than from the model call, because
permission-scoped retrieval runs while the user waits. Spring AI's
`gen_ai.client.operation` separately reports generation duration, per-call token
usage and finish reason; it observes inside `ChatModel`, below `ChatModelPort`,
and starts after retrieval, so the two measurements answer different questions.
No meter carries an organization, request or conversation identifier. The
separate `orgmemory.assistant.retrieval.duration` distribution measures the
permission-scoped retrieval interval, including bounded scheduler queue wait,
while TTFT still ends only on the first model text token rather than an activity
event.

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
The citation response derives its presentation from a closed filename allowlist,
never from upload metadata, blob MIME, or display title. Text, Markdown, PDF,
and PNG/JPEG/GIF/WebP may render inline; Office and unknown formats are forced
to download. The citation-specific Markdown composition strips raw HTML, has no
Mermaid or other active renderer, never loads remote resources automatically,
blocks dangerous URLs, confirms outbound navigation, retains a raw view, and
falls back to raw text if rendering fails.

The Assistant composer exposes the current Answer model and any additional
models an administrator activated on that same organization gateway. The
ordinary-user response contains only a safe activation UUID, gateway/provider
label, model identifier, display name, and default marker; it omits profile,
endpoint, credential, and route-generation data. The browser submits the opaque
activation UUID or `null` for the current default. Core converts it to a sealed
Assistant-only route authority, and the gateway adapter revalidates the active
profile, activation, route identity, and route version inside the cold stream
before model-client construction. Generic workload routing cannot consume this
authority. Alternate models are unavailable while the default route pins an
explicit reasoning effort, and no provider failure falls back to another model.
The effective gateway/model identity is audited without prompt or completion
content.

The source panel treats citation content as server state. Selecting a citation
opens its source directly rather than a metadata-only hover carousel. It first
loads a separately audited, currently authorized excerpt capped at 4,000 Unicode
code points, then loads the original only for a server-declared preview kind.
The excerpt remains readable if original preview loading fails, and preview and
download are separate actions. The panel deduplicates an
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
Each completed Assistant message also owns only its ordered citation number to
canonical chunk-ID mappings; it stores no excerpt, source bytes, title, URI, or
object key. Answer and mapping rows commit atomically under a composite
message/organization/actor foreign key, and conversation deletion cascades the
mappings. Replay always returns the actor-owned transcript independently of
live authorization. Near-viewport Assistant messages hydrate at most 100
stored mappings through a separate no-store request, deduplicate chunks, and
recheck current visibility in fixed batches of at most 20. Determinate denied,
missing, stale, or revised sources simply leave their historical marker inert;
an indeterminate authorization result fails citation hydration without hiding
the saved answer.
Each conversation also stores its optional model activation together with the
organization route override identity and version observed at selection. Picker
changes and turn creation lock the owned conversation row. Disabled catalog
activations remain as immutable historical identities; re-enabling the same
model creates a new UUID, and stale selections collapse to the current default.
This prevents an Answer route change followed by a return to the old text route
from reviving earlier authority.

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

The empty Assistant uses an outcome-oriented, left-aligned welcome and publishes
a small ordered starter list from the API; these prompts are closed application
data and do not infer inaccessible resources or call a model. Its AI Elements
composer places a compact searchable, keyboard-navigable model selector in the
footer and keeps provider grouping and selection detail in the dialog. The
decorative `Permission-aware` label is absent; authorization remains a server
invariant. The composer keeps at most 4,000 characters per actor and
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
- [0032](../../decisions/0032-conversation-model-selection-is-bound-to-admin-route-authority.md)
