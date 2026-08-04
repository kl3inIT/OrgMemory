# Independent Architecture Challenge Verdict

## Verdict

`ACCEPT WITH MUST-FIXES`

The separation of immutable transcript, mutable feedback, and browser-only
draft state is sound, but identity propagation, database ownership integrity,
and browser lifecycle rules must be explicit before implementation.

## Strongest Counterargument

The proposed canonical ID can identify a streamed answer that never becomes a
persisted row, while feedback adds durable actor-linked behavioral data.
Without commit ordering and database-enforced ownership, a cosmetic control
creates phantom targets, an existence oracle, and mismatched tenant metadata.

## Must Fix Before Completion

1. Wire the server-allocated UUID through `completeTurn` into the persisted
   entity instead of allocating another UUID inside the entity.
2. Emit successful stream completion only after transcript persistence returns,
   and keep feedback disabled during a submitted or streaming turn.
3. Resolve feedback through `(messageId, organizationId, actorUserId)`, require
   the `ASSISTANT` role, and use the same opaque not-found response for missing,
   cross-tenant, cross-actor, and wrong-role targets.
4. Enforce the denormalized ownership tuple in PostgreSQL with a unique message
   tuple, composite feedback foreign key, constrained sentiment, and
   `ON DELETE CASCADE`.
5. Cap stored drafts at 4,000 characters and clear them on submit, logout, actor
   change, and conversation deletion. Never send draft text to telemetry.

## Explicit Answers

- A server-generated message UUID may be allocated before persistence. Unused
  UUIDs are harmless, but allocation does not establish existence.
- Feedback needs its own table with organization and actor columns, a composite
  ownership-preserving foreign key, and cascade deletion.
- `sessionStorage` is acceptable only with the lifecycle cleanup above.

## Rejected Alternative

Do not add mutable feedback columns to `assistant_conversation_messages`.
Evaluation state has separate mutation and deletion semantics from an immutable
transcript and should remain in its own constrained table.

## Evidence Reviewed

- `core/src/main/java/com/orgmemory/core/assistant/AssistantConversationService.java`
- `core/src/main/java/com/orgmemory/core/assistant/AssistantConversationMessage.java`
- `core/src/main/java/com/orgmemory/core/assistant/AssistantConversationMessageRepository.java`
- `core/src/main/resources/db/migration/V6__assistant_conversation_history.sql`
- `docs/guidelines/agent-safety.md`
