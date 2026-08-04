# Adversarial Architecture Challenge: Assistant Interaction Foundation

## Reviewer Mandate

Attack this proposal. Do not validate it by default. Read the enforcing code,
find the strongest failure modes, and return an explicit verdict with must-fix
items and a repository citation for every claim. Work read-only. Read
`CLAUDE.md`, `docs/conventions.md`, `docs/guidelines/agent-safety.md`,
`docs/specs/domains/assistant-and-mcp.md`, and the filenames under
`docs/decisions/` before judging.

## Product Promise At Stake

OrgMemory is a governed organizational-memory layer. Its Assistant answers
only from permission-verified evidence, rechecks citations at open time, keeps
transcripts tenant-and-actor owned, and avoids persisting unnecessary prompts,
answers, secrets, or tool arguments outside the deliberate transcript. A small
interaction improvement must not create a second existence channel, weaken
deletion, or widen prompt retention.

## Exact Proposal Under Review

Review `design.md` beside this file. The proposed rule is:

> Allocate one assistant message UUID before streaming; use it for the AI SDK
> message and the completed transcript row. Store at most one separate mutable
> actor-owned `HELPFUL|NOT_HELPFUL` feedback row keyed to that assistant
> message, containing no prompt or answer payload. Store unsent composer drafts
> only in actor-and-conversation-scoped browser `sessionStorage`.

Current enforcing paths:

- `apps/api/src/main/java/com/orgmemory/api/assistant/AssistantController.java`
- `apps/api/src/main/java/com/orgmemory/api/assistant/UiMessageStream.java`
- `core/src/main/java/com/orgmemory/core/assistant/AssistantConversationMessage.java`
- `core/src/main/java/com/orgmemory/core/assistant/AssistantConversationService.java`
- `apps/web/src/features/assistant/components/assistant-page.tsx`
- `apps/web/src/features/assistant/api/chat-transport.ts`

## Comparable-System Evidence

| System | Observed behavior | Source |
| --- | --- | --- |
| Onyx pin `618b503` | Feedback is a separate row linked to an assistant chat message; creation first resolves the message through the current user and rejects non-assistant targets; removal deletes feedback for that exact message. | `D:/OrgMemory/tmp/onyx/backend/onyx/db/models.py:3386`, `D:/OrgMemory/tmp/onyx/backend/onyx/db/feedback.py:216`, `D:/OrgMemory/tmp/onyx/backend/onyx/server/query_and_chat/chat_backend.py:759` |
| Onyx pin `618b503` | Composer drafts and message feedback are separate UI behaviors; completed answers expose feedback and regeneration, while the input owns draft state. | `D:/OrgMemory/tmp/onyx/web/src/hooks/useDraft.ts:54`, `D:/OrgMemory/tmp/onyx/web/src/app/app/message/messageComponents/MessageToolbar.tsx:163` |
| AI SDK current contract | A `UIMessage` owns one ID and typed parts; a custom transport may connect a non-JavaScript backend to that protocol. | OrgMemory's current adapter: `apps/web/src/features/assistant/api/chat-transport.ts`; server encoder: `apps/api/src/main/java/com/orgmemory/api/assistant/UiMessageStream.java` |
| OrgMemory existing Asset feedback | Mutable feedback is already separate from immutable release/trace state and records sanitized metadata rather than raw provider output. | `core/src/main/java/com/orgmemory/core/assistant/AssistantAssetFeedback.java`, `core/src/main/java/com/orgmemory/core/assistant/AssistantAssetTraceRecorder.java` |

## Motivating Cost

The current browser can render feedback buttons but cannot safely persist a
selection for a newly streamed answer: `UiMessageStream` creates a random UI
message UUID and `AssistantConversationService.completeTurn` later creates a
different entity UUID. Using either value would make replay inconsistent or
require a fragile title/sequence lookup. The current composer also loses an
unsent draft on conversation navigation, and retry exists only after failure.

## Required Verdict

Return:

1. `VERDICT: ACCEPT`, `ACCEPT WITH MUST-FIXES`, or `REJECT`.
2. The strongest counterargument.
3. Concrete must-fix items with repository evidence.
4. Whether the message ID may be allocated before successful persistence.
5. Whether feedback needs its own table, ownership columns, foreign key, and
   deletion rule.
6. Whether `sessionStorage` is an acceptable draft boundary under the current
   safety and retention rules.
7. The rejected alternative you recommend recording.

