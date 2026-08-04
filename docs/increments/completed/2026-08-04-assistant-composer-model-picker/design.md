# Assistant Composer And Conversation Model Picker

Status: completed 2026-08-04.

## Intent

Make the Assistant composer communicate a useful choice instead of a static
security slogan, and give users a polished, server-governed model picker before
the product expands into Packs, custom agents, uploads, or deep research.

## Current Gap

The empty state is visually balanced but functionally sparse. Its large
composer uses the footer for `Permission-aware`, which describes an invariant
rather than helping the user act. OrgMemory already has an organization AI
control plane, but the Assistant neither shows the effective model nor lets an
actor keep an allowed model choice with a conversation.

## Selected Design

The organization administrator continues to own the effective
`ASSISTANT_CHAT` gateway and an explicit catalog of chat-model activations
allowed on that gateway. The actor may select only from that catalog. The
current route model is always available as a synthetic default, including
deployment-default routes.

The browser submits only an opaque catalog activation UUID, or `null` for the
current default. The actor-owned conversation stores that activation UUID plus
the effective organization route override identity and version. Each selection
mutation and each chat turn re-resolves the effective organization route. A
stored selection is used only when its activation remains enabled, its profile
still owns the active route, and the route identity and version still match.
Route changes therefore apply to subsequent requests and an A -> B -> A change
cannot revive old authority.

Catalog rows are soft-disabled, not deleted. Re-enabling the same gateway/model
creates a new activation UUID. Existing conversations may retain a stale
foreign key for audit and display history, but resolution treats it as invalid
and returns the current default; no disabled activation can become live again.
Composite database keys repeat organization ownership.

The deployment default has no organization profile. It is exposed as a
read-only synthetic default and is never persisted as a selectable catalog
activation. An organization using it receives no alternate choices until an
administrator creates an organization profile and explicit Assistant route.

Core creates a sealed Assistant route authority containing the exact validated
route, organization, profile, route generation, and optional catalog activation.
Only this authority can enter the new exact-route-plus-conversation-memory port
operation. The integration adapter revalidates it immediately inside the cold
stream before model-client construction. Generic route operations and the
registry's existing exact-route equality check remain unchanged, so Prompt,
Keyword, Graph, and embedding callers cannot use the Assistant exception.

Each catalog activation is an administrator assertion that the identifier is a
chat model. To avoid silently applying an incompatible model-specific option,
alternate selection is disabled whenever the effective default route carries
an explicit reasoning effort. The exact default remains usable with its
administrator-owned option. Provider rejection is surfaced as an unavailable
turn and never triggers fallback.

The browser receives safe option UUID, provider label, model id, display name,
and default/selected state only. It never receives a base URL, credential,
profile identity, route generation, or arbitrary gateway route. Catalog
changes and effective route identity are audited without prompt, completion,
credential, or endpoint data.

The browser uses the local AI Elements `ModelSelector` composition inside
`PromptInputTools`: a compact trigger, searchable dialog, provider grouping,
selected check, keyboard navigation, and disabled busy state. The empty state
becomes a left-aligned, outcome-oriented welcome block with quieter starter
chips. The `Permission-aware` footer label is removed; authorization remains a
server invariant rather than decorative copy.

## Reference Judgment

Onyx and Northstar both put the picker in the composer and keep advanced
configuration out of the main chat surface. OrgMemory should retain that
interaction. It must not copy Onyx's model access data structures or
Northstar's single-user route table because OrgMemory's tenant, administrator,
and current-route lifecycle are stricter.

The AI Elements component is adopted as local presentation source. Its example
of sending an arbitrary model string directly to the chat route is explicitly
rejected; OrgMemory validates a closed server-owned catalog.

## Scope

Included:

- compact AI Elements model selector in both empty and active composers;
- administrator-owned allowed Assistant model catalog on the active gateway;
- actor-owned conversation selection and route-change reset semantics;
- exact-route conversation-memory generation;
- empty-state hierarchy, spacing, copy, and starter-chip polish;
- focused authorization, persistence, API, component, and browser coverage;
- generated REST contracts and documentation reconciliation.

Excluded:

- cross-gateway user selection;
- reasoning-effort, temperature, or token-budget controls;
- model recommendations, cost ranking, or automatic provider fallback;
- attachments, voice, web search, custom agents, Packs, tools, or approvals;
- importing Onyx or Northstar component, state, API, or persistence code.

## Strongest Counterargument

This is a large control-plane and persistence change to replace one line of
composer text. A model picker can create cost and support complexity, and an
organization route already gives administrators a predictable model. A small
UI-only PR could remove the label, show the current model read-only, and defer
selection until model access policy is fully designed.

## Decision And Rejected Alternative

The independent challenge returned `ACCEPT WITH MUST-FIXES`. Proceed with the
full governed picker after incorporating exact authority plus memory,
route-generation binding, soft-disabled activation identity, deployment-default
semantics, reasoning compatibility, concurrency linearization, safe DTOs, and
payload-free audit above.

Reject a read-only effective-model pill because it does not deliver the
project-owner-requested conversation choice. Reject a browser-supplied
gateway/model pair and live provider discovery because neither is an
administrator-owned allowlist.
