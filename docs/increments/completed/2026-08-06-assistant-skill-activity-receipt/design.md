# Assistant Skill Activity Receipt

## Intent

Remove the blank handoff between the last pre-token activity and the first
rendered answer, and make a successfully activated Skill visible in the current
Assistant turn. The experience should follow Onyx Craft's truthful tool-card
timeline without importing its OpenCode/ACP runtime or disclosing tool payloads.

## Observed problem

The browser currently derives the waiting row from AI SDK `submitted` or
`streaming` status and clears activity in `onFinish`. A stream can therefore
settle before React has painted visible Assistant output, leaving a short blank
interval. Skill phases replace one transient label and disappear at completion,
so the completed answer gives no visible evidence that a Skill was used.

## Reference behavior

Pinned Onyx commit `618b5031bf21463f44e3bed9eb9d5073b806fec0`
renders a real `skill` tool call as `Using <name> skill`, gives active/completed/
failed state a distinct icon, groups consecutive calls under `Working`, and
auto-collapses the group after answer text follows while leaving the receipt in
the transcript:

| Behavior | Onyx source |
| --- | --- |
| Named Skill invocation row and completed sparkle | `tmp/onyx/web/src/app/craft/components/tool-cards/CraftToolCard.tsx` |
| Active grouped work, count, failure state, and auto-collapse | `tmp/onyx/web/src/app/craft/components/tool-cards/CraftToolGroup.tsx` |
| Tool/text/thinking ordering | `tmp/onyx/web/src/app/craft/components/BuildMessageList.tsx` |
| Explicit `skillName` on safe display state | `tmp/onyx/web/src/app/craft/types/displayTypes.ts` |

OrgMemory cannot port those components directly: Onyx consumes OpenCode ACP
tool packets and Opal components, while OrgMemory owns an AI SDK message stream,
AI Elements primitives, actor-scoped Skill authorization, and a stricter
payload boundary.

## Proposed behavior

1. A client-owned `awaitingVisibleAnswer` latch begins on submit and ends only
   when an Assistant message has visible text. A source frame does not count
   because citations are not visible until referenced by answer text. AI SDK
   transport completion alone does not end the waiting row.
2. When the last server phase is complete but no answer is visible, the waiting
   copy becomes `Preparing the grounded answer…`; it continues using the
   reduced-motion-safe shimmer.
3. Discovery remains generic. Only a successful actor-authorized Skill
   activation may add the bounded Skill title to the transient activity event.
   The activity record itself trims the title, strips control characters, and
   caps it at 80 characters before delivery. Failed and denied activation
   remains unnamed and opaque.
4. Each activation attempt receives one positive turn-local ordinal. Resource
   activity carries the ordinal only when the exact release was successfully
   activated, so multiple Skills cannot collapse into one untruthful timeline.
5. The browser builds one sanitized current-turn Skill receipt per successful
   activation from the closed activation/resource phases. It shows
   `Using <title> skill`, live state, and fixed step labels. It never renders
   IDs, paths, instructions, resource content, tool arguments, or raw errors.
6. The receipt appears before the current Assistant answer, stays expanded
   while work is active, and collapses when visible answer output arrives. It
   remains only in current browser state and is cleared on the next submit,
   actor/conversation change, abort, or navigation. Transcript persistence is
   explicitly out of scope.

## Safety boundary

- A Skill title is emitted only after `SkillRuntimeOperations.activate`
  succeeds for the current actor and exact release. Search results and denied
  identities never reach the UI receipt.
- Activity remains transient and closed-world. The protocol adds one bounded
  display string to successful activation states and a positive turn-local
  ordinal to attributable activation/resource states; it does not add
  arbitrary server prose.
- Skill instructions and resource contents remain model-only untrusted context.
- The receipt is evidence of tool use, not evidence that the answer is correct
  or that a Skill granted additional authority.

## Strongest counterargument

The existing no-identity activity contract deliberately prevents activity from
becoming a second disclosure channel. Adding a Skill title, even after
activation, can drift toward leaking catalog metadata and creates a partial
history that vanishes on reload. A generic receipt would preserve the security
boundary and require no backend contract change.

## Independent challenge and final choice

Fable 5 returned `REVISE`: successful actor-authorized title disclosure is safe
on the authorization axis only when the direct-published title is structurally
bounded and rendered as plain text; current-turn-only continuity is truthful
when replay renders no receipt and lossy activity never infers completion. The
implementation therefore adopts the bounded title, per-activation ordinal,
turn-token terminal state machine, and no-reconstruction requirements. Durable
receipt persistence remains rejected because it would require a retention,
revocation, replay, and schema decision.

## Exit criteria

- No blank state exists between submit and first visible output.
- A successfully activated Skill produces one named, sanitized current-turn
  receipt with truthful steps and terminal state.
- A no-Skill answer produces no Skill receipt.
- Abort, error, actor change, and conversation change clear the latch and
  receipt without resurrection by late events.
- Backend, frontend, and browser tests cover the state transitions.
