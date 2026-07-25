# Asset Registry Spec

## Current Behavior

The Asset Registry stores generic Asset identity, ownership, authorization,
drafts, immutable revisions, review decisions, and immutable releases.
`PROMPT_TEMPLATE`, `WORK_INSTRUCTION`, and `CAPABILITY_PACK` are the enabled
payload profiles. Each profile validates its own versioned JSON contract while
the shared registry remains free of type-specific columns.

Consumers always address an exact authorized release. A withdrawn release
cannot start new consumption. Forking creates a new Asset draft from an exact
release payload and does not copy reviews or approvals.

### Prompt Template

A Prompt Template release contains exactly one text template or ordered
system/user messages. It declares typed variables, required/default behavior,
sensitivity, optional regex and allowed values, output contract, data policy,
compatibility, Knowledge requirements, limitations, and at most ten bounded
evaluation cases.

Rendering is deterministic and rejects unknown, missing, or invalid variables
before a provider call. Inserted variables and retrieved Knowledge are marked
as untrusted data. Execution pins the exact release digest and resolved AI
route. Optional grounding uses the canonical permission-aware retrieval path,
and the run stores only citation identifiers plus a sanitized output digest by
default. Raw sensitive variables and raw output are not retained.

Evaluation executes only the bounded cases embedded in a release. Release
comparison reports the two exact evaluation results; it does not change a
release or promote a mutable alias.

### Work Instruction

A Work Instruction release declares purpose, audience, prerequisites,
completion outcome, responsible role, and ordered steps. Each step includes an
expected result, check, optional escalation, prohibited actions, and bounded
Asset or Knowledge references.

Following and acknowledging always use an exact authorized release.
Acknowledgement is actor-derived and idempotent.

### Capability Pack

A Capability Pack release declares its purpose, audience, prerequisites,
expected outcome, owner, review date, completion criteria, and ordered
required/optional items. It must contain at least one required item. Each item
pins either an exact registry release or an exact Knowledge Asset version.

Assignment and progress are actor-scoped and idempotent. Every component is
authorized independently when the journey is read or updated. Accessible items
retain order; inaccessible components collapse into one opaque access-gap flag
without exposing denied titles, types, or counts. A replacement release never
rewrites an existing Pack pin.

### Federated Knowledge

Knowledge remains owned by the canonical Knowledge ledger. The read-only
catalog lists only current active versions visible through the canonical
OpenFGA and ACL scope. Capability Packs reference exact Knowledge version IDs;
the registry does not create duplicate Asset rows or copy Knowledge tuples.

### Assistant And Web Consumption

The in-app Assistant exposes a closed action allowlist for discovery,
permission-aware Knowledge search, Prompt preparation/render/run, Work
Instruction guidance, Pack start/read/progress, explicit release fork, and
feedback. Recommendations are computed from live `CAN_USE` authorization and
contain an exact non-withdrawn release reference. External provider calls and
every state-changing action require an explicit confirmation flag.

Each action appends a trace that pins the actor, action, exact release
references, authorization context, citation identifiers, model route when
applicable, and a sanitized input/output shape or digest. Traces do not retain
raw Prompt variables, provider output, or credentials. The allowlist has no
approval, publication, withdrawal, role/permission mutation, or arbitrary
execution action.

The authenticated web application provides four generic surfaces:

- **For your role** lists only exact releases the current actor can use.
- **Asset detail / use** shares identity, provenance, and release selection,
  then renders Prompt, Work Instruction, or Capability Pack profile actions.
- **Pack journey** preserves ordered exact pins, required/optional progress,
  opaque access gaps, and replacement-release impact.
- **Governance workspace** exposes revision comparison, evaluation, review,
  release history, deprecation, and withdrawal through the registry's existing
  authorization checks.

Server state is fetched through generated clients and TanStack Query. URL state
belongs to TanStack Router; no global client store is used for authorization or
Asset payloads.

## Source Modules

- `core.assetregistry`
- `core.assistant.AssistantAssetToolService`
- `core.knowledge.KnowledgeCatalogService`
- `apps.api.assetregistry`
- `apps.api.assistant.AssistantAssetToolController`
- `apps.api.knowledge.KnowledgeCatalogController`
- `web.features.assets`

## Explicitly Deferred

- public Asset MCP resources/prompts/tools
- controlled SOP effectivity
- Skill package installation and public marketplace behavior
