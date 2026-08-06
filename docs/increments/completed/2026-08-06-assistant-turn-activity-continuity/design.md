# Assistant Turn Activity Continuity

## Intent

Keep the Assistant's pre-answer activity continuously visible from submit to
the first renderable answer content, without moving the activity block when the
AI SDK creates the Assistant message. Refine the Skill receipt with the useful
parts of Onyx Craft's tool-card lifecycle while preserving OrgMemory's closed,
sanitized activity contract.

## Observed problem

The current browser equates any non-blank raw text part with visible output.
An opening Markdown fragment such as `**` therefore removes the waiting row
before Streamdown can paint meaningful text. The waiting row and Skill receipt
also have separate render sites: the receipt moves from the message-list tail
to immediately before the Assistant message once that placeholder appears,
while the waiting row remains at the tail. The resulting remount, list gap, and
smooth bottom-following can briefly move the active state outside the viewport.

The completed `2026-08-06-assistant-skill-activity-receipt` increment intended
to close this handoff, but its raw-text predicate and split DOM ownership do not
fully satisfy that intent. This increment corrects the implemented browser
lifecycle; it does not change the server event or authorization contract.

## Reference behavior

Pinned Onyx commit `618b5031bf21463f44e3bed9eb9d5073b806fec0`
keeps activity in one stable tool/timeline surface:

| Behavior | Onyx source |
| --- | --- |
| A Skill invocation renders as `Using <name> skill`, with a spinner while active and a sparkle when complete | `tmp/onyx/web/src/app/craft/components/tool-cards/CraftToolCard.tsx` |
| A tool row is expandable only when it has useful body content or a failure | `tmp/onyx/web/src/app/craft/components/tool-cards/CraftToolCard.tsx` |
| Active work stays open and the same group auto-collapses after answer content follows | `tmp/onyx/web/src/app/craft/components/tool-cards/CraftToolGroup.tsx` |
| The timeline preserves one expansion state across streaming and completed output | `tmp/onyx/web/src/app/app/message/messageComponents/timeline/hooks/useTimelineExpansion.ts` |

OrgMemory will port the lifecycle rather than the OpenCode packet model, Opal
components, raw command bodies, or animated comet treatment.

## Chosen behavior

1. One current-turn activity surface is anchored immediately after the latest
   user message. The same keyed surface remains before the Assistant message
   when that message is appended.
2. The surface owns both the waiting label and every current-turn Skill
   receipt. Retrieval, generation, and Skill phase changes update content
   without changing its transcript position.
3. Raw Markdown framing alone is not renderable output. The waiting state ends
   only after the Assistant text contains a visible glyph after bounded removal
   of whitespace, zero-width characters, and Markdown framing punctuation.
4. When renderable answer content arrives, the waiting row leaves and active
   Skill receipts collapse in the same stable surface. A receipt remains as
   current-turn evidence.
5. A completed activation with no resource-read detail is a non-interactive
   receipt: no chevron and no redundant `Skill instructions loaded` body. A
   resource read or failure supplies useful detail and remains expandable.
6. Activity stays plain text and closed-world. No Skill instructions, resource
   content, tool input/output, identifiers, or raw errors enter the DOM.

## Rejected alternatives

- **Add a fixed delay before hiding thinking.** This masks provider timing,
  makes fast answers feel slower, and still allows the activity block to move.
- **Adopt AI Elements `Tool` directly.** The component expects a `ToolUIPart`
  with input/output states. OrgMemory has a sanitized activity receipt rather
  than a client-visible tool call, so adapting it would weaken semantics or
  invite payload disclosure.
- **Strip all Markdown before rendering the answer.** Markdown is a supported
  answer format; only the visibility predicate needs a bounded framing check.
- **Persist the activity timeline.** Replay, retention, and revocation remain
  outside the current-turn receipt contract.

## Scope and safety

- Frontend composition, current-turn view state, and focused tests only.
- No API, SSE schema, persistence, model, retrieval, Skill authorization, or
  production configuration changes.
- The change is not a material domain, authorization, persistence, publication,
  or deployment decision, so it does not require a new architecture challenge.

## Exit criteria

- A Markdown-only opening delta cannot remove the waiting indicator.
- Thinking and Skill activity stay in one transcript position before the
  Assistant answer through placeholder creation and first visible text.
- A completed activation-only receipt has no empty disclosure affordance.
- Resource progress remains visible, expandable, and auto-collapses with the
  first renderable answer.
- No-Skill, abort, error, finish-without-output, actor switch, and conversation
  switch behavior remain covered.
