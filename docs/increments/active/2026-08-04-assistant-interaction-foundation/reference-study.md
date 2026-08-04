# Assistant Interaction Reference Study

## Pin And License

- Reference: `D:/OrgMemory/tmp/onyx`
- Commit: `618b5031bf21463f44e3bed9eb9d5073b806fec0`
- Worktree state when checked: clean
- Relevant files are outside Onyx `ee` directories and covered by the
  repository's MIT Expat grant.

No Onyx source is copied into OrgMemory. The implementation ports observable
interaction behavior onto OrgMemory's existing AI SDK, AI Elements, generated
REST, authorization, and persistence boundaries.

## Behavior Mapping

| Onyx evidence | Behavior retained | OrgMemory implementation |
| --- | --- | --- |
| `web/src/hooks/useDraft.ts` | Preserve an unsent composer draft | Actor-and-conversation-scoped `sessionStorage`, bounded to 4,000 characters and cleared on submit/logout/actor change/deletion |
| `web/src/app/app/message/messageComponents/MessageToolbar.tsx` | Completed answers expose copy, feedback, and regeneration actions | Existing local AI Elements `MessageActions`; retry is a fresh linear authorized turn |
| `backend/onyx/db/models.py` and `backend/onyx/db/feedback.py` | Feedback is mutable state linked to an assistant answer | Separate constrained feedback table with exact tenant/actor ownership and cascade deletion |
| `web/src/sections/Suggestions.tsx` | The active assistant publishes useful starting points | Closed server-owned starter endpoint; no model call or inaccessible-resource personalization |

## Deliberate Non-Ports

- Onyx agent/persona and project selection
- message branching and edit trees
- queued turns and deep research
- Onyx component source, state containers, API clients, database models, and
  backend route code

These would widen the increment beyond improving the existing Assistant and
would import assumptions that do not match OrgMemory's governed retrieval and
actor-owned transcript model.
