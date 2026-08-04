# Assistant Interaction Foundation

## Intent

Make the existing permission-aware Assistant easier to start, recover, and
evaluate before adding Packs, tool execution, custom agents, uploads, or deep
research. This increment ports the useful interaction behavior observed in the
pinned Onyx reference without importing Onyx's agent, project, or packet model.

## Current Gap

The browser already streams grounded answers, rechecks protected citations,
persists actor-owned conversations, retries failed turns, and supports stop.
The remaining first-use friction is narrower:

- starter prompts are hard-coded in the browser;
- a composer draft is lost when the user switches conversations or reloads;
- only failed turns expose retry, while completed answers expose only copy;
- users cannot mark an answer helpful or unhelpful;
- the streamed AI SDK message ID is unrelated to the persisted Assistant
  message ID, so a durable answer-level action has no canonical target;
- focus and scroll behavior have only incidental coverage.

## Reference Evidence

The pinned Onyx checkout at `618b5031bf21463f44e3bed9eb9d5073b806fec0`
keeps starter messages on the active agent, persists composer drafts, exposes
copy/feedback/regeneration per completed answer, and stores chat feedback in a
separate message-linked table. Its implementation is evidence for the
interaction, not an architecture to mirror:

- `D:/OrgMemory/tmp/onyx/web/src/sections/Suggestions.tsx`
- `D:/OrgMemory/tmp/onyx/web/src/hooks/useDraft.ts`
- `D:/OrgMemory/tmp/onyx/web/src/app/app/message/messageComponents/MessageToolbar.tsx`
- `D:/OrgMemory/tmp/onyx/backend/onyx/db/models.py`
- `D:/OrgMemory/tmp/onyx/backend/onyx/db/feedback.py`

AI Elements already supplies the local presentation primitives for
conversation scrolling, prompt input, suggestions, message actions, and
sources. OrgMemory retains product-specific state, authorization, persistence,
and streaming contracts.

## Selected Design

### One canonical Assistant message identity

The API allocates the assistant message UUID before streaming. The same UUID is
used as the AI SDK UI message ID and, only after a non-empty stream completes,
as the persisted transcript message ID. Failed or aborted streams may consume
an UUID without creating a row; UUID continuity is not a product contract.

This keeps feedback targeted to the exact answer without adding a client-to-
server identity translation endpoint. It does not change retrieval, model
memory, citation numbering, or transcript ordering.

The ID is server-owned and allocation does not imply existence. The stream's
successful finish is emitted only after transcript persistence returns, and
answer feedback remains unavailable while that turn is still submitted or
streaming. A failed, aborted, or empty turn therefore has no actionable
feedback target.

### Separate actor-owned feedback

One mutable feedback row is keyed by the assistant message UUID. The row stores
only organization ID, actor user ID, `HELPFUL|NOT_HELPFUL`, and update time. It
does not copy the question, answer, prompt, evidence, citation, model route, or
provider output.

Create/update and delete first resolve the message through its denormalized
organization and actor ownership and require the `ASSISTANT` role. Cross-actor,
cross-tenant, missing, and user-message targets remain one opaque not-found
surface. Deleting a conversation cascades its feedback.

The database repeats that ownership boundary: feedback has one primary key on
`message_id`, a constrained sentiment, and a composite foreign key from
`(message_id, organization_id, actor_user_id)` to an equally unique message
tuple with `ON DELETE CASCADE`. Service checks are not the only tenant
integrity mechanism.

The transcript entity remains immutable. Feedback is mutable evaluation state,
so adding feedback columns to `assistant_conversation_messages` is rejected.

### Session-scoped drafts

The browser stores one draft per actor and conversation in `sessionStorage`,
including a distinct key for a new conversation. A successful submit clears
that key immediately. The draft is not sent to the server until submit and is
not retained across browser sessions.

Draft storage is capped at the server's 4,000-character message limit and is
cleared on logout, actor change, and deletion of its conversation. Draft text
is never copied into analytics or telemetry.

Durable `localStorage` and server-side draft persistence are rejected for this
increment because they create a new prompt-retention surface without a user or
administrator retention control.

### Retry is a fresh turn

Retry on a completed answer resubmits the user message immediately preceding
that answer into the same conversation. It always performs fresh authorized
retrieval and creates a new linear turn. This increment does not introduce a
message tree, overwrite history, or reuse prior evidence.

### Server-owned starters

The API publishes a small ordered, closed list of supported starter prompts.
The list contains ordinary prompt text and intent labels only; it is not
personalized from inaccessible resources and does not invoke the model.

## Strongest Counterargument

Adding durable feedback and canonical streamed message IDs widens a previously
read-only transcript surface for a cosmetic toolbar. Session storage still
retains user-entered enterprise text in a script-readable browser store, and a
separate feedback table adds migration, ownership, deletion, and retention
obligations before feedback has proven product value. A frontend-only,
ephemeral thumbs control plus static starters would ship faster and keep the
backend contract unchanged.

## Decision And Rejected Alternative

The independent challenge returned `ACCEPT WITH MUST-FIXES`. Proceed with the
selected design after adding commit ordering, composite database ownership,
opaque target resolution, and draft lifecycle cleanup above.

Reject mutable feedback columns on `assistant_conversation_messages` because
they couple evaluation state to the immutable transcript and obscure their
different update and deletion semantics. Also reject a frontend-only rating:
it cannot survive history replay or produce governed product-quality signal.

## Scope

Included:

- server-owned starter prompts;
- session-scoped composer drafts;
- completed-answer retry;
- helpful/not-helpful create, replace, remove, replay, and authorization;
- focus and scroll behavior tests;
- current specs, coverage matrix, OpenAPI, and migration consolidation.

Excluded:

- suggested follow-ups generated by a model;
- message branching or edit history;
- queued turns;
- uploads, custom agents, Projects, voice, or deep research;
- tool execution, action approval, or MCP mutation;
- raw prompt/answer telemetry.
