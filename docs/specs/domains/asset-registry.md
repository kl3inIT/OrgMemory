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

## Source Modules

- `core.assetregistry`
- `core.knowledge.KnowledgeCatalogService`
- `apps.api.assetregistry`
- `apps.api.knowledge.KnowledgeCatalogController`

## Explicitly Deferred

- Assistant Asset tools and orchestration
- final generic Asset web surfaces
- public Asset MCP resources/prompts/tools
- controlled SOP effectivity
- Skill package installation and public marketplace behavior
