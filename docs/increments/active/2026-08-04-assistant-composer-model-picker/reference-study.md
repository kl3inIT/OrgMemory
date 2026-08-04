# Assistant Composer And Model Picker Reference Study

## Pins

- Onyx: `D:/OrgMemory/tmp/onyx` at
  `618b5031bf21463f44e3bed9eb9d5073b806fec0` (clean when inspected).
- Northstar: `D:/northstar` at
  `101e374db5aaab726534b9510f38e84fef790ae2` (clean when inspected).
- AI Elements: official component documentation resolved through Context7 as
  `/vercel/ai-elements`, plus the installed skill references.

No Onyx or Northstar source is copied. Their source is behavioral evidence.

## Behavior Mapping

| Evidence | Useful behavior | OrgMemory judgment |
| --- | --- | --- |
| Onyx `web/src/sections/model-selector/ModelSelector.tsx` and `ModelSelectorContent.tsx` | Current-model trigger, search, provider groups, selected check, capability context | Retain compact searchable selection; omit temperature/reasoning controls from this increment |
| Onyx `web/src/sections/input/BaseInputBar.tsx` and `AppInputBar.tsx` | The model choice lives in the composer and disables during incompatible busy states | Retain placement and busy semantics; do not port queue, voice, upload, or agent state |
| Northstar `web/src/components/assistant-workspace.tsx` | Small provider/model trigger inside AI Elements `PromptInputTools`; conversation-scoped selection | Retain interaction and scope, adapted to OrgMemory tenancy and admin route lifecycle |
| Northstar `apps/api/src/main/java/com/northstar/api/assistant/AssistantConversationRouteService.java` | Server validates gateway/model and persists a conversation route | Retain server authority; reject its unscoped single-user table shape |
| Northstar `web/src/pages/settings.tsx` and `apps/api/src/main/java/com/northstar/api/ai/AiSettingsController.java` | Gateway settings own manual and discovered model catalogs | Retain an administrator-owned chat-model catalog rather than trusting arbitrary browser model ids |
| AI Elements `model-selector` and `prompt-input` references | Searchable, keyboard-navigable, provider-grouped dialog composed into a prompt footer | Use as local presentation source; do not adopt the example's trust of an arbitrary request model string |

## Community Example Review

Official Onyx chat documentation and current shadcn community examples converge
on a compact bottom-row selector, rounded send action, searchable command menu,
and quiet suggestion chips. The useful lesson is hierarchy: the textarea owns
the visual weight, secondary controls are ghost-sized, and model details move
into the dialog. Model grids, token meters, and configuration-heavy cards are
rejected for the empty Assistant because they compete with the first question.

## AI Elements Inventory Judgment

- Use now: `model-selector`, existing `prompt-input`, `suggestion`,
  `conversation`, and `message`.
- Good next when the backend emits the corresponding typed state: `tool` and
  `confirmation` for agent actions; `reasoning` for safe provider reasoning
  summaries; `attachments` for governed uploads; `context` for a real token
  budget.
- Defer: `agent` until custom agents are a defined product object.
- Not relevant to chat: `toolbar`, which is a React Flow node toolbar rather
  than a message/composer action bar.
