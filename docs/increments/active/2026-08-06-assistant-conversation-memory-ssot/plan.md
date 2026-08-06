# Assistant Conversation Memory SSOT Plan

- [x] Measure the real relationship between the two stores against the
  deployment; retire the incorrect drift claim.
- [x] Complete the independent architecture challenge as a two-architect debate
  with a no-tools judge; record brief and verdict.
- [x] Port status-mapped failure sentences so a failed turn names its cause.
  Independent of the store change and shippable on its own.
- [x] Add `turn_id` to `assistant_conversation_messages` with partial uniqueness
  for one USER and one ASSISTANT per turn; leave legacy rows nullable,
  transcript-visible and context-ineligible.
- [x] Carry the turn id through `beginTurn` and `completeTurn` so the pair is
  written against one identity rather than inferred from sequence order.
- [ ] Add a project-owned read-only transcript context advisor that selects the
  last completed turns, excludes the in-flight USER by construction, and snaps
  the window forward to a USER boundary.
- [ ] Replace `MessageChatMemoryAdvisor` with that advisor in both memory client
  paths; remove the `ChatMemory` bean, `ObservedChatMemory`, the JDBC memory
  starter and its schema-initialization setting.
- [ ] Remove the second `memory.clear` call from conversation deletion and let
  the existing cascade do the work.
- [ ] Drop `spring_ai_chat_memory` in a migration separate from the one that
  adds `turn_id`, so a rolling deployment never runs the new reader against a
  dropped table or the old writer against a missing one.
- [ ] Cover: turn pairing under `U1,U2,A2,A1` concurrency, exclusion of the
  in-flight USER, failed and cancelled turns, model-free no-evidence turns,
  legacy null-`turn_id` rows, window bound of 20, deletion cascade, and one
  status-mapped sentence per failure class.
- [ ] Reconcile the Assistant spec and test matrix, record the persistence
  decision with its rejected alternative, and refresh `Source:`/`Reconciled:`.
- [ ] Run `:core:test`, `:apps:api:test`, lint, typecheck, production build, and
  browser verification of a failed turn's message.

## Sequencing note

The failure-sentence port is deliberately first and separate: it is the only
item that changes what a user sees today, it touches no schema, and it completes
the diagnosability work already shipped in `f38a5357` (which made failures
attributable to operators but left them mute to users).

The store collapse is gated behind turn identity. Without `turn_id` the reader
cannot pair turns correctly under concurrency, and shipping the reader first
would replace a working window with a subtly wrong one.
