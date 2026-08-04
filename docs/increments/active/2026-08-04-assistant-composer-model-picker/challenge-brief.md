# Assistant Composer And Conversation Model Picker Challenge Brief

## Adversarial Review Mandate

Attack this proposal rather than validating it. Verify every material claim in
the repository itself. Look specifically for a cheaper boundary that preserves
the product promise, a privilege escalation through model selection, stale
route behavior after an administrator changes the control plane, provider or
model capability mismatches, cross-tenant persistence mistakes, and a way in
which conversation memory could be sent through a route the administrator did
not authorize. Do not edit any file.

## Product Promise At Stake

OrgMemory is a governed organizational-memory layer for enterprise AI work.
Its Assistant performs fresh permission-aware retrieval for every turn and
routes generation through an administrator-owned, organization-scoped AI
control plane. A more capable composer must not turn model selection into a
provider bypass, leak credentials or inaccessible resource identity, or weaken
the fail-closed behavior of the existing Assistant.

## Exact Rule Under Review

> The Assistant model picker may select only chat models explicitly enabled by
> an organization administrator on the gateway currently effective for
> `ASSISTANT_CHAT`. The selected gateway key and model id are stored on the
> actor-owned conversation. Every selection write and every turn re-resolves
> the effective organization route and validates that the stored/requested
> model remains in the enabled catalog for that gateway. If the administrator
> changes the effective gateway, the old conversation selection is not reused;
> the new route default becomes effective until the actor selects an allowed
> model on the new gateway. The exact validated route, including conversation
> memory, is passed to `ChatModelPort`; the browser never sends a credential,
> base URL, or arbitrary provider route.

Current enforcement paths to inspect:

- `core/src/main/java/com/orgmemory/core/ai/AiGatewayAdministrationService.java`
- `core/src/main/java/com/orgmemory/core/ai/AiRouteResolver.java`
- `integrations/ai-model-gateways/src/main/java/com/orgmemory/integrations/ai/gateway/AiGatewayRegistry.java`
- `integrations/ai-model-gateways/src/main/java/com/orgmemory/integrations/ai/gateway/SpringAiChatModelAdapter.java`
- `core/src/main/java/com/orgmemory/core/assistant/AssistantConversationService.java`
- `apps/api/src/main/java/com/orgmemory/api/assistant/AssistantController.java`
- `apps/web/src/features/assistant/api/chat-transport.ts`
- `docs/specs/domains/assistant-and-mcp.md`
- `docs/guidelines/agent-safety.md`

The proposed persistence is one administrator-managed catalog table keyed by
organization, gateway profile, and model id, plus nullable selected gateway/model
columns on `assistant_conversations`. Database foreign keys repeat tenant and
actor ownership for catalog and conversation data. The current effective route
model is always returned as the default even when no additional catalog entry
exists, so existing deployments do not lose chat.

## Comparable-System Evidence

| System | Observed behavior | Mechanism and source |
| --- | --- | --- |
| Onyx at `618b5031bf21463f44e3bed9eb9d5073b806fec0` | The composer exposes the effective model, searchable provider-grouped choices, selected state, capability descriptions, and disables changes while busy. Model visibility is also subject to LLM access controls. | `D:/OrgMemory/tmp/onyx/web/src/sections/model-selector/ModelSelector.tsx`, `D:/OrgMemory/tmp/onyx/web/src/sections/model-selector/ModelSelectorContent.tsx`, `D:/OrgMemory/tmp/onyx/web/src/sections/input/BaseInputBar.tsx`, `D:/OrgMemory/tmp/onyx/web/tests/e2e/utils/chatActions.ts` |
| Northstar at `101e374db5aaab726534b9510f38e84fef790ae2` | The model picker lives in `PromptInputTools`, uses a searchable AI Elements dialog, persists gateway/model per conversation, and has the server validate the pair against the AI router. Gateway settings own the manual/discovered model catalog. | `D:/northstar/web/src/components/assistant-workspace.tsx`, `D:/northstar/web/src/components/ai-elements/model-selector.tsx`, `D:/northstar/apps/api/src/main/java/com/northstar/api/assistant/AssistantConversationRouteService.java`, `D:/northstar/apps/api/src/main/java/com/northstar/api/ai/AiSettingsController.java`, `D:/northstar/web/src/pages/settings.tsx` |
| Vercel AI Elements | The local-source component offers a searchable, keyboard-navigable, provider-grouped model selector, but deliberately owns presentation rather than authorization or persistence. | `C:/Users/admin/.agents/skills/ai-elements/references/model-selector.md`, `C:/Users/admin/.agents/skills/ai-elements/references/prompt-input.md` |

## Operational Motivation And Cost

The current empty state dedicates the left side of the composer footer to a
static `Permission-aware` label while providing no meaningful control. The
organization already operates a multi-provider control plane, yet ordinary
Assistant users cannot see which model will answer or keep a deliberate model
choice with a conversation. A frontend-only dropdown would be cheap but false:
the current request carries only message, retrieval limit, and conversation id,
and `AssistantService` always resolves the single organization route inside the
chat adapter. Conversely, exposing provider discovery directly to users would
make every provider model callable without an administrator-owned chat-model
allowlist, including non-chat or unexpectedly expensive models.

## Required Verdict

Return plain Markdown with:

1. `VERDICT`: ACCEPT, ACCEPT WITH MUST-FIXES, or REJECT.
2. The strongest counterargument to the proposal.
3. A numbered must-fix list, with repository evidence for every item.
4. A cheaper rejected/accepted alternative and why.
5. Explicit scope limits: what this verdict does not authorize.

Before answering, read `CLAUDE.md`, `docs/conventions.md`,
`docs/specs/domains/assistant-and-mcp.md`, `docs/guidelines/agent-safety.md`, and
scan `docs/decisions` filenames for binding decisions.
