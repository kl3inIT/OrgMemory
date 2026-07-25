# Asset Registry POC UI Reference Audit

Date: 2026-07-26

This audit reads the source of current open-source products and component
libraries. It is a pattern study, not permission to copy another product's
visual identity. OrgMemory keeps its existing Hanken Grotesk typography,
surface/status tokens, shell, generated API clients, and authorization model.

## Source Repositories And Components

### Backstage — catalog identity and generic detail

Repository snapshot:
[`backstage/backstage@4956d7f`](https://github.com/backstage/backstage/tree/4956d7ffc5b091fc14b0fd29d417b2100fb4f132)
(Apache-2.0).

Source read:

- `plugins/catalog-react/src/components/EntityDataTable/EntityDataTable.tsx`
- `plugins/catalog-react/src/components/EntityInfoCard/EntityInfoCard.tsx`
- `plugins/catalog-react/src/components/EntityPeekAheadPopover/EntityPeekAheadPopover.tsx`
- `packages/core-components/src/components/StructuredMetadataTable/StructuredMetadataTable.tsx`
- `packages/core-components/src/components/SimpleStepper/*`

Learn:

- keep one stable entity reference separate from the human display title;
- make generic identity, metadata, loading, error, and empty states reusable;
- use configurable type panels inside one detail shell;
- do not fetch or reveal detail until the user can access that entity;
- keep actions in explicit header/footer regions instead of making an entire
  information card an ambiguous button.

Apply to OrgMemory:

- **For your role** shows namespace/slug plus exact release version;
- **Asset detail / use** owns the shared identity, owner reference,
  provenance, release selector, and profile panel boundary;
- denied candidates never appear in catalog, hover, count, or empty-state
  copy.

### Langfuse — Prompt version, evaluation, and comparison

Repository snapshot:
[`langfuse/langfuse@190c4ca`](https://github.com/langfuse/langfuse/tree/190c4cac843c58de45b8ad46a85b1dae85572fc3)
(MIT outside its enterprise directories; only non-enterprise files below were
read).

Source read:

- `web/src/features/prompts/components/prompt-detail.tsx`
- `web/src/features/prompts/components/prompt-history.tsx`
- `web/src/features/prompts/components/PromptVersionDiffDialog.tsx`
- `web/src/features/prompts/components/PromptVariableListPreview.tsx`
- `web/src/features/experiments/components/MultiStepExperimentForm.tsx`
- `web/src/features/experiments/components/steps/ReviewStep.tsx`

Learn:

- the selected version belongs in URL state and remains visible next to the
  primary action;
- version history, content/config, variables, linked evidence, and execution
  are distinct information layers;
- comparison identifies both sides and normalizes structured content before
  diffing;
- evaluation should end with a review summary before execution;
- compact status/version badges support the content instead of becoming the
  main hierarchy.

Apply to OrgMemory:

- Prompt use keeps the exact release and digest visible while variables,
  grounding, evaluation status, and output remain separate;
- governance comparison names both immutable revisions and uses a real
  sequence-aware diff instead of comparing lines only by array index;
- evaluation results belong with the candidate release and approval evidence,
  not in an unrelated dashboard metric.

### assistant-ui — tool state and explicit approval

Repository snapshot:
[`assistant-ui/assistant-ui@3f90440`](https://github.com/assistant-ui/assistant-ui/tree/3f90440a45d8b7bc11745a1d3cf242d4f40934ed)
(MIT).

Source read:

- `packages/ui/src/components/assistant-ui/tool-fallback.tsx`
- `examples/with-opencode/components/tools/opencode-permission-card.tsx`

Learn:

- tool state is a small state machine: running, complete, incomplete, or
  requires action;
- arguments/results are inspectable but collapsed by default;
- an approval surface states the action and consequences, always preserves a
  refusal path, prevents double submission, and resolves to a durable outcome;
- “allow once” is a safer default than a sticky global permission.

Apply to OrgMemory:

- replace the persistent external-provider switch with **Review and run** then
  **Run once**;
- Pack start/progress, fork, and feedback confirmations describe the exact
  release and mutation;
- Assistant traces appear as compact action receipts, not raw secret-bearing
  JSON.

### Open edX Learning — ordered Pack journey

Repository snapshot:
[`openedx/frontend-app-learning@c694ac6`](https://github.com/openedx/frontend-app-learning/tree/c694ac61161d069db99f0f07b9e33b76384ef9ea)
(AGPL-3.0; studied only as a UX/state reference, with no source copied).

Source read:

- `src/courseware/course/sidebar/sidebars/course-outline/CourseOutline.tsx`
- `.../components/SidebarSequence.jsx`
- `.../components/SidebarUnit.tsx`
- `.../components/CompletionIcon.tsx`
- `.../components/UnitLinkWrapper.tsx`

Learn:

- ordered content needs one clearly active/next item;
- complete, partial, inactive, active, and locked states require distinct
  semantics, not color alone;
- hierarchy may collapse, but order and current position must remain legible;
- navigation and completion are different actions.

Apply to OrgMemory:

- Pack items are a numbered outline with separate **Open** and **Mark
  complete** actions;
- the first incomplete required item is visually identified as **Up next**;
- optional, deprecated, exact-pin, completion, and opaque access-gap states do
  not collapse into one badge;
- completed Pack history stays visible instead of disappearing.

### shadcn/ui — implementation blocks already compatible with OrgMemory

Repository snapshot:
[`shadcn-ui/ui@aa13b0c`](https://github.com/shadcn-ui/ui/tree/aa13b0cb83cd32beb99820df63db1bb9357bc4f6)
(MIT).

Source read:

- `apps/v4/registry/bases/radix/blocks/dashboard-01/components/section-cards.tsx`
- `apps/v4/registry/bases/radix/blocks/dashboard-01/components/data-table.tsx`
- `apps/v4/registry/bases/radix/blocks/preview-02/cards/empty-explore-catalog.tsx`
- `apps/v4/examples/radix/empty-demo.tsx`

Learn:

- compose existing primitives into named blocks; do not introduce a second
  design system;
- labels and keyboard controls must exist even when icon buttons look obvious;
- empty states need one cause, one recovery path, and restrained copy;
- tables suit governance density; cards suit role-oriented discovery.

Apply to OrgMemory:

- reuse the repository's existing Card, Badge, AlertDialog, Progress, Table,
  Tabs, Select, Skeleton, and Sidebar primitives;
- do not import the dashboard block's chart, drag-and-drop, or table
  dependencies for this POC.

### Cognee — lightweight “next incomplete” logic

Repository snapshot:
[`topoteretes/cognee@325acf3`](https://github.com/topoteretes/cognee/tree/325acf356a81545b9892f19ab1ea7b61c51a776b)
(Apache-2.0).

Source read:

- `cognee-frontend/src/app/(app)/dashboard/partials/GettingStartedChecklist.tsx`

Learn:

- calculate progress from durable completion state;
- emphasize the first incomplete item rather than every unfinished item.

Do not copy:

- inline styles, handcrafted SVG, or hiding the entire journey after
  completion. OrgMemory needs theme tokens, icon-library assets, accessibility,
  and retained completion evidence.

## Screen Decisions

| OrgMemory screen | Primary learned pattern | Deliberate difference |
| --- | --- | --- |
| For your role | Backstage stable catalog identity + shadcn discovery cards | permission-filtered, Pack-first, no marketplace popularity/social signals |
| Asset detail / use | Backstage generic entity shell + Langfuse exact-version Prompt detail | one shell supports Prompt, Work Instruction, and Pack |
| Pack journey | Open edX ordered outline + Cognee next-incomplete focus | exact release pins and opaque access gaps are first-class |
| Review / release | Langfuse version history/diff/eval + shadcn dense primitives | approval binds immutable digest; publication and withdrawal remain separate authorities |
| Assistant action | assistant-ui requires-action/receipt pattern | closed OrgMemory allowlist; no “always allow” for POC mutations |

## Rejected Patterns

- marketplace hero, popularity ranking, social rating, and public publishing;
- Prompt-only routing or a separate detail page per Asset type;
- sticky approval toggles for external provider or mutations;
- dashboard charts that do not help select, use, review, or release an Asset;
- copying source styling wholesale or adding a component library that competes
  with the existing OrgMemory primitives;
- exposing denied Asset metadata through counts, previews, cached queries, or
  relation labels.
