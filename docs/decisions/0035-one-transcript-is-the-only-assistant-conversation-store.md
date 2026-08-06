# 0035 — One transcript is the only Assistant conversation store

Status: accepted
Date: 2026-08-06

## Context

`V6` created two tables for one conversation.
`assistant_conversation_messages` held the product transcript with an
organization, an actor, foreign keys to `app_users` and
`assistant_conversations`, and a generated sequence. `spring_ai_chat_memory`
held the twenty-message model window that Spring AI's
`MessageWindowChatMemory` and `MessageChatMemoryAdvisor` wrote — with no tenant
column, no actor column and no foreign key, while holding raw message content.
Consistency between them was one hand-written `memory.clear` call in the
delivery layer, outside the transaction that deleted the conversation.

Measured against the deployment on 2026-08-06 across 546 conversations, the two
stores were **not** drifting: 514 matched `memory = LEAST(transcript, 20)`
exactly, 31 were explained by a failed turn or a model-free no-evidence turn,
one was unexplained, and none had more model memory than transcript. The
earlier framing of "20 orphaned conversations" as drift was wrong and is
withdrawn. The second store was a pure function of the first plus "was the model
invoked" — which is the condition under which a second physical store earns
nothing rather than the condition under which it is safe.

Nothing model-only existed to protect. Spring AI's `JdbcChatMemoryRepository`
filters `ToolResponseMessage` and tool-calling `AssistantMessage` out before
they are stored, `MessageChatMemoryAdvisor` sits at
`DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER` while `ToolCallingAdvisor` sits below it
so tool messages never reach memory at all, and permission-scoped grounding is
rebuilt into a request-local system message on every turn by design. Production
held `USER` 591 and `ASSISTANT` 494 rows, and `SYSTEM` and `TOOL` zero.

## Decision

`assistant_conversation_messages` is the only persisted home of an Assistant
conversation. A project-owned read-only advisor reads prior turns from it before
each model call. `spring_ai_chat_memory`, `MessageWindowChatMemory`,
`ChatMemoryRepository`, the `ChatMemory` bean, the `ObservedChatMemory`
decorator and the JDBC chat-memory starter are removed.

The advisor's unit is a completed turn, identified by the `turn_id` added in
`V26`, not a message count. That single choice discharges three separate
requirements: the question of the turn in flight is excluded without having to
be recognized, a turn that failed before answering is excluded for the same
reason, and a window counted in whole turns can never open on an answer, so the
forward-snapping that `MessageWindowChatMemory` performed does not have to be
reimplemented.

The read is scoped by organization as well as conversation. A message belongs to
its conversation through a composite foreign key and a conversation belongs to
exactly one actor, so tenant scoping on the read is sufficient and actor scoping
is implied. A call that has not resolved an organization gets no prior turns at
all rather than an unscoped read.

Deletion relies on the existing cascade. `ChatMemory.clear(String)` carried no
actor while the domain delete requires one, so the second call is removed rather
than delegated.

## Independent challenge

Decided by a two-architect debate with a no-tools judge rather than by the
proposer; the record is in the increment's `challenge-brief.md` and
`challenge-verdict.md`. The deciding argument was the defending side's own
round-two concession: *"No meaningful value remains in Spring AI's stock JDBC
repository or dialect… that part of A's attack succeeds"*, which left a
permanently project-owned persistence stack holding a derivable projection.

The winning position won conditionally. The proposer's original shape — keeping
`ChatMemory` with a guarded no-op `add()` — was rejected by both sides and is
not what shipped: Spring AI writes the assistant message from the response
aggregation callback while OrgMemory writes it later from the controller's
stream completion, so a guarded `add()` would either reject every healthy
completion or write ahead of the canonical writer. The advisor never writes.

## Rejected alternative

Keep two stores and bring `spring_ai_chat_memory` into the tenancy model: change
`conversation_id` to UUID, add `organization_id` and `actor_user_id`, add a
composite foreign key with cascade, and serialize the read-trim-replace cycle
under a parent lock.

Its strongest form is that the model-invocation boundary is a real semantic
distinction, observable in the data, letting model memory carry its own
retention and reset lifecycle independent of the product transcript. It would
have won on evidence of model-only state that cannot be derived from the
transcript. Both sides agreed no such state exists today.

It also would not have preserved what justified the split. Replacing the schema
means replacing `PostgresChatMemoryRepositoryDialect`, which binds only the five
upstream columns, and `ChatMemory.add(String, List<Message>)` receives no
tenant, so the upstream compatibility that motivated a separate store does not
survive its own fix.

## Consequences

- One store, one delete path, one row per message. Message content can no longer
  exist in a table that cannot be filtered by organization to find it again.
- A no-evidence turn's static answer now enters model context, where previously
  the model window held nothing for it because the model was never invoked. This
  is the transcript the user actually saw, and a follow-up question about it is
  now answerable.
- The context read is a query against a growing table rather than a bounded
  per-conversation row set. It is bounded by ten turns and served by the
  conversation-and-sequence index, and it publishes the same
  `CONVERSATION_HISTORY_LOAD` stage the removed decorator did, so a regression
  is visible rather than silent.
- Upgrading Spring AI can no longer change how an OrgMemory conversation is
  stored or trimmed.
