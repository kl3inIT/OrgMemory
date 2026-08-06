# Debate Brief — Chat Transcript SSOT

You are one of two architects debating a material persistence-boundary decision
in the OrgMemory repository. Read this file in full, inspect the repository
evidence yourself, then write your response.

## Hard constraints

- **Read-only.** Do not edit, create, or delete any repository file. Do not run
  migrations, mutate any database, or change git state.
- Inspect the repo freely to check the claims below. Do not trust this brief;
  verify it. If a claim here is wrong, say so with the file and line.
- Your response goes into the debate record file you are told to append to.
  Plain Markdown. No tools-only output, no truncation.

## The question

OrgMemory stores the same assistant conversation in two Postgres tables. Should
it collapse to one store, or keep two and fix the weaker one?

## Current state (verify these)

**Table A — `assistant_conversation_messages`** (`core/src/main/resources/db/migration/V6__assistant_conversation_history.sql`)
- Columns include `organization_id`, `actor_user_id`, `role`, `content`,
  `sequence_id bigint GENERATED ALWAYS AS IDENTITY`, `version`.
- `CHECK (role IN ('USER','ASSISTANT'))`, `CHECK (length(content) BETWEEN 1 AND 200000)`.
- Foreign keys to `app_users` and to `assistant_conversations`, `ON DELETE CASCADE`.
- Written by `AssistantConversationService` (`core/src/main/java/com/orgmemory/core/assistant/AssistantConversationService.java`):
  the USER row when a turn starts, the ASSISTANT row in `completeTurn(...)`,
  which returns early when the answer is blank.
- Read by `AssistantConversationService.history(...)`, which serves
  `GET /api/assistant/conversations/{conversationId}/messages`.
- Also carries answer citations.

**Table B — `spring_ai_chat_memory`** (same migration file, top)
- Columns: `conversation_id varchar(36)`, `content text`, `type varchar(10)`,
  `timestamp`, `sequence_id bigint`. **No `organization_id`, no `actor_user_id`,
  no foreign key.**
- `CHECK (type IN ('USER','ASSISTANT','SYSTEM','TOOL'))`.
- Written by Spring AI's `MessageChatMemoryAdvisor` through the `ChatMemory`
  bean built in `apps/api/src/main/java/com/orgmemory/api/assistant/AssistantConfiguration.java`
  as `MessageWindowChatMemory.builder().maxMessages(20).build()`, wrapped in
  `ObservedChatMemory`.
- `MessageWindowChatMemory` trims to its window on write and `saveAll` replaces
  the conversation's row set, so rows beyond the window are physically deleted.
- Consumed only via the `ChatMemory` interface at
  `integrations/ai-model-gateways/src/main/java/com/orgmemory/integrations/ai/gateway/SpringAiChatModelAdapter.java`
  (`MessageChatMemoryAdvisor.builder(chatMemory).build()`, ~line 277 and again
  in `assistantMemoryClient`).

**Consistency today.** `apps/api/src/main/java/com/orgmemory/api/assistant/AssistantController.java`
(~line 379) deletes a conversation by calling `conversations.delete(actor, conversationId)`
and then `memory.clear(conversationId.toString())` — two stores, two calls, not
one transaction, orchestrated in the delivery layer.

## Measured evidence from the production deployment (2026-08-06)

Production data is test-only; there are no real customers and no backfill
obligation.

- `assistant_conversation_messages`: 1148 rows / 539 conversations. Longest
  conversation 56 messages.
- `spring_ai_chat_memory`: 1085 rows / 519 conversations. Longest 20 — exactly
  the window cap, confirming trimming is live.
- **20 conversations have zero rows in `spring_ai_chat_memory`** while their
  transcript exists in table A.
- Message type distribution in `spring_ai_chat_memory`: `USER` 591,
  `ASSISTANT` 494. **No `SYSTEM` and no `TOOL` rows exist**, despite the schema
  allowing them.

## Position A — collapse to one store

Implement `ChatMemory` over `assistant_conversation_messages`. Drop
`MessageWindowChatMemory`, `ChatMemoryRepository`, and the
`spring_ai_chat_memory` table. Keep the `ChatMemory` *interface*, because
`SpringAiChatModelAdapter` depends on it.

Proposed shape:
- `add()` → no-op, documented: `AssistantConversationService` is the sole writer
  and is the only caller holding `organizationId`, `actorUserId` and citations,
  which `ChatMemory.add(String, List<Message>)` does not receive.
- `get()` → windowed read ordered by `sequence_id`, snapped forward to the
  nearest `USER` message so the window never begins on an assistant reply whose
  prompt was cut off.
- `clear()` → delegate to the conversation service delete.

Claimed benefits: drift becomes structurally impossible; model memory enters the
tenancy model for the first time; deletion becomes one domain transaction; the
destructive `saveAll` disappears.

## Position B — keep two stores, fix the weaker one

Keep the separation the migration comment declares intentional
(`-- Spring AI ChatMemory is the bounded context sent back to the model. The
complete product transcript is stored separately below.`). Bring
`spring_ai_chat_memory` into the tenancy model instead: add `organization_id`,
foreign keys and cascade, and move deletion out of the controller into a single
domain transaction.

Claimed benefits: the model window and the product transcript are genuinely
different concerns with different lifecycles and retention; keeping Spring AI's
own repository preserves upstream compatibility and avoids an interface
implementation whose `add()` violates its contract by doing nothing.

## Points each side must engage with

1. Is a no-op `add()` an acceptable implementation of `ChatMemory`, or a
   contract violation that will mislead the next maintainer? What happens if a
   future Spring AI upgrade, or another advisor, calls `add()` and expects
   persistence?
2. Does the absence of `SYSTEM`/`TOOL` rows today prove table A can hold
   everything the model window needs, or is it an artifact of the current
   feature set that tool-calling will invalidate? Note the Skill tool loop in
   `apps/api/.../AssistantConfiguration.java` and `AssistantSkillToolCallbacks`.
3. `completeTurn(...)` returns early on a blank answer, so a failed turn leaves
   a USER row with no ASSISTANT row. Under each position, what does the model
   window see on the next turn, and is the snap-forward read sufficient?
4. What explains the 20 conversations with zero rows in
   `spring_ai_chat_memory`, and does either position prevent that class of
   divergence or merely relabel it?
5. Retention and privacy: `spring_ai_chat_memory` holds message content with no
   tenant column. Under Position B, is adding `organization_id` sufficient, or
   does the second writer remain the actual risk?
6. Cost and reversibility: which position is cheaper to undo if wrong?

## Required output shape

1. **Position** — which architecture you defend, in one sentence.
2. **Evidence** — concrete file paths and line references supporting it.
3. **Attacks** — specific, evidence-backed attacks on the opposing position.
   Attack the position, not a strawman of it.
4. **Concessions** — what the other side is genuinely right about.
5. **Falsifier** — what fact, if true, would change your mind.
