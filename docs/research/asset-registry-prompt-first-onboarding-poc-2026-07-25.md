# Prompt-First Asset Registry And Role Onboarding POC Research

Date: 2026-07-25

## Executive Decision

The first Asset Registry POC should not start with Screenpipe ingestion or a
Prompt-only application. It should prove a generic governed registry through
four immediately consumable shapes:

- existing permission-aware `KNOWLEDGE`;
- `PROMPT_TEMPLATE`;
- `WORK_INSTRUCTION`;
- `CAPABILITY_PACK`.

The golden use case is role capability onboarding: a second user receives an
authorized Pack, reads the required knowledge, follows a Work Instruction, runs
an approved Prompt Template, and completes a realistic task. `SKILL` follows
after this browser-native reuse path is proven. Controlled `SOP`, Screenpipe
evidence learning, executable workflows, agents, and tool packages remain
defined but are not POC dependencies.

This preserves the original product thesis: OrgMemory is not a prompt library,
an HR platform, or a generic document-control product. It is the governed
system of record and consumption layer for organizational AI capabilities.

## Findings That Correct The Earlier Definition

### One catalog does not mean one operational lifecycle

Asset types share stable identity, ownership, permission, revision, review,
release, provenance, discovery, and audit primitives. They do not share one
operational state machine.

The common kernel is:

```text
mutable AssetDraft
-> immutable AssetRevision + digest
-> ReviewCase bound to that revision
-> immutable AssetRelease
```

An approval always pins a revision and digest. Changing submitted content
creates another revision and invalidates the old approval for publication.

The stable Asset portfolio state is derived:

```text
DRAFT_ONLY -> ACTIVE -> SUNSETTING -> RETIRED
```

Release availability is separate:

```text
AVAILABLE -> DEPRECATED | WITHDRAWN
```

Future SOP effectivity, Skill distribution/moderation, installation, and
retention are additional type-specific lifecycles, not values in the common
enum.

### Machine observations are proposals, not automatically Assets

Manual authoring creates an Asset identity and draft. Passive or machine
discovery should create a private, expiring `AssetProposal`; acceptance creates
the Asset and its first draft. The POC has no passive discovery and therefore
does not require an `AssetProposal` table.

### Knowledge remains side by side

The shipped `KnowledgeAsset` aggregate already owns secure source lineage,
immutable versions, OpenFGA identity, publication convergence, retrieval
projections, and citation behavior. The POC must not rename or migrate its
tables.

The catalog federates Knowledge through a read-only adapter. A Pack pins an
exact `KnowledgeAssetVersion`; authorization continues through the existing
knowledge boundary. There is no duplicate registry identity or copied OpenFGA
tuple set for Knowledge.

### Prompt is a standalone Asset only when it is reusable

The preferred type name is `PROMPT_TEMPLATE`. An ad hoc user message, rendered
prompt, model response, or chat transcript is not an Asset. Those are input or
execution records.

A Prompt Template qualifies as an Asset when it has:

- a stable purpose, audience, owner, and intended reuse;
- `useWhen` and `doNotUseWhen`;
- structured system/user messages or a text template;
- typed variables, validation, defaults, and sensitivity labels;
- an output contract;
- required knowledge, tools, model/runtime compatibility, and data policy;
- examples or evaluation cases;
- immutable released bytes, digest, change note, and evaluation evidence.

Prompt release coordinates do not need SemVer. A sequential release number plus
immutable digest is sufficient. `recommended`, `staging`, or `production` are
movable channels, not release states. Reproducible consumers pin the exact
release and digest.

OpenAI's project-level prompts use drafts, published versions, stable Prompt
IDs, variables, pinned versions, comparison, rollback, and linked Evals.
MLflow and LangSmith likewise treat prompts as versioned registry entities
with aliases/commits, lineage, evaluation, and organizational reuse. These
systems validate Prompt Template as a first-class Asset while also showing why
OrgMemory must offer more than text storage.

### A Pack is the user-value composition

`CAPABILITY_PACK` is the user-facing composite. It pins an ordered set of exact
component releases and adds:

- audience or target role;
- outcome and prerequisites;
- required and optional items;
- step order;
- completion criteria;
- owner, review date, and release history.

`ONBOARDING`, `HANDOVER`, and `ROLE_ENABLEMENT` are Pack purposes, not separate
Asset types.

A Pack never widens access. Effective access to every item is the intersection
of the caller's permission with that component's own policy. A restricted item
is omitted or represented as an opaque access gap; its title, summary, type,
and existence are not disclosed.

### Work Instruction is not SOP

`WORK_INSTRUCTION` describes how one role performs a bounded task. `SOP`
defines a controlled organizational procedure, its applicability, responsible
roles, controls, decisions, and effectivity. A role onboarding POC normally
needs a Work Instruction; a full controlled SOP lifecycle is not a prerequisite.

`PLAYBOOK` remains a legitimate later type for situational guidance that permits
judgment. `WORKFLOW` remains a later executable/orchestration definition with
triggers, nodes, transitions, human steps, and integrations.

### Components become Assets only when governance is independent

A checklist, output template, few-shot set, or evaluation case may initially be
an immutable component of a Prompt, Work Instruction, or Pack release. Promote
it to `CHECKLIST_TEMPLATE`, `OUTPUT_TEMPLATE`, or `EVAL_SUITE` only when it is
independently reused, owned, permissioned, reviewed, versioned, or measured.

Completed checklists, Pack progress, prompt runs, review decisions,
installations, acknowledgements, and evaluation runs are operational records,
not Asset releases.

## Target Taxonomy

| Type | Primary consumption | POC |
| --- | --- | --- |
| `KNOWLEDGE` | read, search, ask, cite | existing adapter |
| `PROMPT_TEMPLATE` | fill, render, run, copy, fork | core |
| `WORK_INSTRUCTION` | read, follow, acknowledge | core |
| `CAPABILITY_PACK` | start and complete a role/use-case journey | core |
| `SOP` | follow the current effective controlled procedure | later profile |
| `SKILL` | inspect, install, invoke, update, pin | next after POC |
| `PLAYBOOK` | follow situational guidance | later |
| `WORKFLOW` | execute an orchestration definition | later |
| `AGENT` | deploy or launch a composed agent | later |
| `TOOL_PACKAGE` | install/connect/call a packaged tool | later |
| `GUARDRAIL_PROFILE` | enforce reusable AI constraints | later |
| `EVAL_SUITE` | validate multiple Asset releases | internal first, later Asset |

Role, department, business process, runtime, and model profile are catalog
context or dependency entities unless they independently satisfy Asset
admission rules.

## Golden POC

The POC is the **L1 Customer Support Capability Onboarding Pack**:

1. A senior support user authors `Triage customer ticket`.
2. The Prompt Template declares ticket variables and a structured output
   contract for category, priority, SLA risk, escalation reason, and response
   draft.
3. It references permission-aware Knowledge about SLA and escalation.
4. A reviewer compares revisions, runs bounded demo cases, and releases it.
5. An onboarding owner publishes a Pack containing exact Knowledge,
   Work Instruction, Prompt, and checklist components.
6. A second authorized user opens **For your role**, follows the Pack, runs the
   Prompt against a mock ticket, and completes the first task.
7. OrgMemory records release IDs, digest, model route, evidence/citations,
   actor, outcome, and Pack progress without persisting unnecessary sensitive
   input.
8. The demo proves an update or withdrawal and owner/backup-owner handover.

This is role capability onboarding, not recruitment, payroll, benefits, device
provisioning, calendar management, or an LMS.

## Generic Product Surface

The POC has four generic surfaces rather than Prompt-specific pages:

1. **For you / Asset catalog** — authorized Packs and Assets appropriate to the
   actor's role and task.
2. **Asset detail / use** — shared identity, owner, release, provenance, and
   type-driven renderer/actions.
3. **Pack journey** — ordered required/optional items, pinned releases, access
   gaps, and progress.
4. **Governance workspace** — draft/revision diff, evaluation/validation,
   review decisions, release history, deprecation, and withdrawal.

An `AssetTypeProfile` supplies payload schema, validator, renderer, consumption
actions, review policy, dependency extraction, evaluation behavior, and
optional adapters. The backend and web must not branch the product around
Prompt-specific routes or database columns.

## Assistant Boundary

The in-app Assistant is the first orchestrated consumer:

- discover and recommend authorized Assets and Packs;
- resolve exact released content;
- ask/cite Knowledge;
- collect variables, render, and run Prompt Templates;
- guide Work Instruction and Pack steps;
- record PromptRun and PackProgress;
- fork a released Asset into a new draft and submit feedback when requested;
- detect stale dependencies and propose updates.

It may prepare diffs, evaluation summaries, release notes, and review requests.
It may not approve its own content, publish, change permissions, withdraw,
revoke, install executable code, or silently update a pinned Pack component.

Every execution pins the actor, Asset/release IDs, digest, model route,
Knowledge/citation versions, tool versions, authorization decision context,
timestamp, and sanitized outcome.

## MCP Boundary

The in-app Assistant, REST API, CLI, and MCP reuse the same application use
cases and authorization decisions. MCP never owns migrations, reads the database
directly, or becomes a privileged bypass.

The first public remote MCP is authenticated, not anonymous. "Public" means
protocol-accessible to authorized external clients; it does not mean public
Assets or a public marketplace.

Initial read-only/deterministic tools:

- `search_assets`;
- `get_asset`;
- `get_asset_release`;
- `get_capability_pack`;
- `resolve_asset_relations`;
- `render_prompt`.

MCP Resources may expose permitted stable metadata and immutable release
content through `orgmemory://assets/...` URIs. MCP Prompts may adapt released,
authorized Prompt Templates for compatible clients. Neither primitive is the
canonical registry storage model.

Remote HTTP MCP must implement OAuth protected-resource discovery, validate
token audience, combine coarse scopes with object-level authorization, validate
inputs, rate-limit calls, sanitize outputs, and preserve opaque denials. The
existing bearer forwarding shape is valid only if MCP and API share a protected
resource audience; otherwise the gateway needs an on-behalf-of/token-exchange
contract.

`run_prompt`, Pack progress, draft fork, and feedback are later controlled
mutations after the in-app flow proves identity, consent, idempotency, retention,
and audit. Approval, publication, withdrawal, permission change, Skill install,
and Tool execution are not exposed by the public POC MCP.

## POC Success Evidence

- a second user finds the correct authorized Pack without knowing Asset IDs;
- that user completes one realistic task with Knowledge, a Work Instruction,
  and a released Prompt Template;
- the output passes the bounded evaluation rubric;
- every consumption record pins exact releases and digests;
- Pack access does not leak denied component metadata;
- editing submitted bytes invalidates the old approval path;
- a withdrawn release cannot be newly consumed;
- a replacement release does not silently mutate an existing Pack;
- owner transfer preserves reuse and flags missing backup ownership;
- the web and MCP use the same application authorization and audit path.

Measure time-to-first-correct-task, first-time-right rate, second-user reuse,
view-to-use conversion, reviewer correction rate, evaluation pass rate,
owner/backup-owner coverage, and permission-denial leakage.

## Sources

- [OrgMemory original research report](orgmemory_research_report_2026-07-06.md)
- [Unified Asset Registry research history](unified-organizational-asset-registry-2026-07-25.md)
- [OpenAI Prompt management](https://help.openai.com/en/articles/9824968-prompt-management-in-playground)
- [MLflow Prompt Registry](https://mlflow.org/docs/latest/genai/prompt-registry/index.html)
- [LangSmith prompt management](https://docs.langchain.com/langsmith/manage-prompts)
- [WHO SOP management example](https://tdr.who.int/docs/librariesprovider10/good-practices-guidance-handbook-for-national-tb-surveys/tc_1.3.-sop-management-example_21-06-2021.pdf)
- [Agent Skills specification](https://agentskills.io/specification)
- [MCP specification](https://modelcontextprotocol.io/specification/2025-11-25)
- [MCP authorization](https://modelcontextprotocol.io/specification/2025-11-25/basic/authorization)
