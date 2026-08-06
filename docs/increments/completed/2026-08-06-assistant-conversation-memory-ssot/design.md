# Assistant Conversation Memory SSOT

## Intent

Make one table the single persisted home of an Assistant conversation, and make
a failed turn say why it failed to the person who hit it. Today the same
conversation lives in two tables written by two different writers, and a turn
that fails tells the user only "The assistant stream failed."

## Observed problem

### One conversation, two stores

`assistant_conversation_messages` (`V6__assistant_conversation_history.sql:43`)
holds the product transcript with `organization_id`, `actor_user_id`, foreign
keys to `app_users` and `assistant_conversations`, and a generated
`sequence_id`. It serves `GET /api/assistant/conversations/{id}/messages`.

`spring_ai_chat_memory` (same migration, line 3) holds the model window. It has
`conversation_id varchar(36)`, `content`, `type`, `timestamp`, `sequence_id` —
**no tenant column, no actor column, no foreign key** — while holding raw
message content. It is written by Spring AI's `MessageChatMemoryAdvisor` through
`MessageWindowChatMemory.builder().maxMessages(20)`
(`AssistantConfiguration.java:55`).

Consistency between them is maintained by `AssistantController.java:379-380`
calling `conversations.delete(...)` and then `memory.clear(...)` — two stores,
two calls, outside one transaction, orchestrated in the delivery layer. Any
future deletion path that forgets the second call leaves message content in a
table that cannot be filtered by organization to find it again.

### A failed turn is mute

`UiMessageStream.Encoder.error()` emits the fixed string
`"The assistant stream failed."` for every failure. A rate-limited gateway, an
expired credential, a model that no longer exists on the gateway, and a broken
deployment are indistinguishable to the person who has to decide what to do
next.

Northstar solved exactly this in `AssistantStreamFailures.java`, which reads only
the HTTP status off the failure and returns a fixed sentence per status. Its
javadoc names the same before-state OrgMemory is still in: *"a model that cannot
serve this chat was indistinguishable from a broken deployment."*

## Evidence that shaped the decision

### The two stores are not drifting

Measured against the deployment on 2026-08-06, 546 conversations:

| Shape | Conversations |
| --- | --- |
| `memory = LEAST(transcript, 20)` — exactly the window rule | 514 |
| Explained by a failed turn or a model-free no-evidence turn | 31 |
| Unexplained | 1 |
| `memory > transcript` | 0 |

The original framing of "20 orphaned conversations" as drift was wrong. Those
are turns where the model was never invoked: the transcript correctly holds the
question (and, for a no-evidence turn, the static answer), and the model window
correctly holds nothing. Both stores agree.

This measurement did not weaken the case for collapsing — it changed the reason.
The second store is not diverging; it is a **pure function** of the first plus
"was the model invoked". That is precisely the condition under which a second
physical store earns nothing.

### Nothing model-only exists to protect

The `CHECK (type IN ('USER','ASSISTANT','SYSTEM','TOOL'))` on
`spring_ai_chat_memory` is partly dead schema:

- `JdbcChatMemoryRepository.saveAll` (spring-ai 2.0.0, lines 112-116) filters out
  `ToolResponseMessage` and tool-calling `AssistantMessage`, and logs a warning
  when it does.
- `MessageChatMemoryAdvisor` sits at `HIGHEST_PRECEDENCE + 200`
  (`Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER`) while `ToolCallingAdvisor` is
  at `HIGHEST_PRECEDENCE + 300`, so the tool loop runs *inside* the memory
  advisor and tool messages never reach `ChatMemory` at all.

Production confirms it: `USER` 591, `ASSISTANT` 494, `SYSTEM` 0, `TOOL` 0.
Permission-scoped grounding is rebuilt into the request-local system message on
every turn by design, so it must not be persisted either.

## Decision

Collapse to one store. `assistant_conversation_messages` becomes the sole
persisted home of both the product transcript and the model context;
`spring_ai_chat_memory`, `MessageWindowChatMemory` and `ChatMemoryRepository`
are dropped.

This was decided by an independent two-architect debate rather than by the
proposer. The full record is in `challenge-brief.md` and `challenge-verdict.md`.
The deciding argument was the defending side's own round-2 concession: *"No
meaningful value remains in Spring AI's stock JDBC repository or dialect… that
part of A's attack succeeds"*, leaving a permanently project-owned persistence
stack holding a derivable projection.

### Rejected alternative

Keep two stores and bring `spring_ai_chat_memory` into the tenancy model: change
`conversation_id` to UUID, add `organization_id` and `actor_user_id`, add a
composite foreign key with cascade, and serialize the read-trim-replace cycle
under a parent lock.

Its strongest form is that the model-invocation boundary is a real semantic
distinction, observable in the data, that lets model memory carry its own
retention and reset lifecycle independent of the product transcript. It would
have won on evidence of **model-only state that cannot be derived from the
transcript**. Both sides agreed no such state exists today: the vendor filters
tool messages out, and system grounding is deliberately request-local.

It would also have required replacing the stock repository and dialect anyway —
`PostgresChatMemoryRepositoryDialect` binds only the five upstream columns and
`ChatMemory.add(String, List<Message>)` receives no tenant — so the upstream
compatibility that justified the split does not survive its own fix.

## Binding constraints from the debate

The winning position won **conditionally**. These are design requirements, not
notes:

1. **No no-op `add()`.** The originally proposed shape is withdrawn. Spring AI
   writes the assistant message from the aggregation callback
   (`MessageChatMemoryAdvisor:136-162`) while OrgMemory writes the transcript
   answer later in the controller's `doOnComplete`
   (`AssistantController.java:419-436`). A guarded `add()` would therefore either
   reject every healthy completion or write ahead of the canonical writer.
   Replace `MessageChatMemoryAdvisor` with a project-owned **read-only** context
   advisor that never calls `ChatMemory.add`.
2. **Explicit turn identity is required.** `assistant_conversation_messages` has
   `role` and a global `sequence_id` but no `turn_id`, and `beginTurn` /
   `completeTurn` are separate transactions. Concurrent turns can persist as
   `U1,U2,A2,A1`, which no ordering heuristic pairs correctly. Add `turn_id`.
3. **The current in-flight USER must be excluded by construction.** `beginTurn`
   commits the USER row before the model call and the adapter also passes the
   question as `.user(...)` (`SpringAiChatModelAdapter.java:202-213`), so a naive
   read would send it twice. Select completed turns only.
4. **`clear()` is not delegated to the domain delete.** `ChatMemory.clear(String)`
   carries no actor while the domain delete requires `CurrentActor`. Deletion
   stays at the owned controller boundary and relies on the existing cascade;
   the second `memory.clear` call is removed.
5. **Snap-forward is not a gain of this change.** `MessageWindowChatMemory:113-119`
   already snaps the retained window forward to a USER message. Collapsing the
   stores means OrgMemory must now implement that behavior itself rather than
   inherit it — it is a cost, not a benefit.
6. **The drift claim is withdrawn** and must not appear in the spec.

## Scope

In scope:

- One persisted conversation store with explicit turn identity.
- A project-owned read-only transcript context advisor replacing
  `MessageChatMemoryAdvisor`.
- Status-mapped failure sentences for a failed turn, ported from Northstar's
  `AssistantStreamFailures` shape.

Out of scope, deliberately:

- Assistant composer file attachment, Knowledge Base format coverage, and wiring
  the built-but-unwired LightRAG multimodal pipeline. Recorded in the roadmap
  Engineering Backlog; attachment in particular is a permission-boundary question
  needing its own challenge, not a UI change.

## Open questions the record did not settle

- The one genuinely unexplained conversation (`transcript=6, assistant=3,
  memory=4`) has a proposed mechanism — two concurrent `add` calls overwriting
  one another through the read-then-replace cycle — but no proven diagnosis.
  Collapsing removes the class, so this is not a blocker; it is not an
  explanation either.
- Whether legacy rows without `turn_id` stay eligible for model context. The
  safe default is transcript-visible but context-ineligible.
