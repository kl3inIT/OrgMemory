# OrgMemory Product Brief

## One-Liner

OrgMemory is the organizational memory layer for enterprise AI work. It manages
clean enterprise knowledge and converts reusable AI work into governed,
transferable Capability Assets.

## Problem

Organizations are adopting AI through individual employees. A sales rep builds a
follow-up prompt. A support lead builds a triage workflow. An engineer creates a
Claude/Codex repo routine. A marketing manager writes a reusable image-generation
brief. These assets often remain in private chat histories, personal notes,
Slack threads, or undocumented workflow tools.

When employees move teams or leave, the organization loses:

- prompt libraries and reusable AI workflows
- agent configurations and tool-specific operating knowledge
- decision context and examples
- domain expertise embedded in AI interactions
- onboarding knowledge for future employees

The result is repeated rebuilds, inconsistent governance, slower onboarding, and
AI capability that scales through individuals instead of systems.

## Product Thesis

The differentiator is not raw ingestion, search, MCP, or a generic prompt
library. Those are supporting capabilities.

The differentiator is the lifecycle of an **AI Capability Asset**:

```text
capture -> normalize -> review -> approve -> reuse -> measure -> transfer -> deprecate
```

If OrgMemory only ingests enterprise data and lets an AI search it, it competes
directly with Glean, Microsoft, Airbyte Agents, Workato, Zapier, Notion, and
internal data platforms. The stronger wedge is governed AI capability transfer.

OrgMemory should be built as an enterprise product, not a classroom artifact.
The near-term target is a scoped on-prem design-partner pilot where real
enterprise data is handled only after identity, permissions, audit, data
classification, and operational controls are in place.

Because real enterprise design-partner opportunity exists, the immediate startup
risk is not generic demand discovery. The immediate risk is delivery and trust:
can OrgMemory safely handle real workflows, real permissions, real data, and
real operational expectations inside an enterprise environment?

Airbyte should be used for enterprise data movement when connectors fit, but it
is not the product moat. OrgMemory's moat is the memory lifecycle after data
lands in staging: ACL snapshot, normalization, Knowledge Asset curation,
Capability Candidate detection, human review, approval, audit, permission-aware
retrieval, reuse, and handover.

## AI Capability Asset

An AI Capability Asset is a reusable unit of organizational AI capability. It is
not just a prompt and not just a wiki page.

Examples:

- prompt template
- workflow automation
- AI agent configuration
- knowledge bot
- analytics brief
- content or slide generator
- data extraction workflow
- evaluation checklist
- playbook
- handover pack
- governance guardrail
- copilot

Minimum metadata:

- title and summary
- asset type
- department and business process
- AI tool/model used
- owner and backup owner
- approval status and risk level
- visibility and permissions
- prompt/workflow content
- input and output schema
- examples
- version history
- usage events
- approval events
- handover metadata

## Memory Model

Do not collapse raw sources, knowledge, and capabilities into one object. The
correct production model has four layers:

```text
Raw Source
-> Knowledge Asset
-> Capability Candidate
-> Capability Asset
```

Raw Source:

- untrusted or source-shaped data imported from Slack, Teams, Google Drive,
  SharePoint, GitHub, CRM, n8n, Dify, PDFs, transcripts, or manual uploads
- may be noise, duplicate, stale, private, irrelevant, or low quality
- should not automatically be considered knowledge

Knowledge Asset:

- cleaned, normalized, trusted enterprise knowledge
- examples: policy, SOP, product documentation, decision record, meeting
  summary, customer/process domain note
- used for search, citations, grounding, Ask Memory, and candidate generation
- can be an organizational asset, but it is not automatically a Capability Asset

Capability Candidate:

- a possible reusable workflow/prompt/agent/playbook detected from a Knowledge
  Asset or submitted manually
- must be reviewed before becoming official

Capability Asset:

- reusable AI work that can create an output or run a repeatable workflow
- has owner, backup owner, version, approval status, input/output schema, usage,
  and handover value

This lets OrgMemory have a broader **Memory Registry** without weakening the
meaning of **Capability Asset**.

## Prototype Scope

The current prototype proves a narrow behavior loop:

1. A user submits an AI prompt/workflow/agent idea.
2. OrgMemory normalizes it into structured metadata.
3. The user saves it as a draft or submits it for review.
4. A reviewer approves/rejects/deprecates it.
5. Other users search and open the approved asset.
6. Users click Use Asset and usage is tracked.
7. Onboarding/offboarding surfaces reuse the same registry data.

Current prototype surfaces:

- dashboard
- capability registry
- asset detail
- create asset with AI enrichment
- review queue
- Ask Memory
- knowledge transfer
- knowledge graph visualization
- analytics/settings

Future enterprise pilot surfaces should add:

- raw sources
- knowledge assets
- capability candidates
- source connector/staging health
- import scope and retention controls

## Target Users

Employees:

- save reusable AI workflows
- find approved assets
- use/copy assets faster than rebuilding from scratch
- get recognized as contributors

Team leads/reviewers:

- approve useful assets
- assign owners and backup owners
- remove weak or risky assets
- see department capability and handover risk

Admins/AI enablement:

- manage organizational capability memory
- understand which AI assets are reused
- identify missing ownership and governance gaps
- prepare future MCP/API/ingestion integrations

## Positioning

Weak:

```text
We ingest company data and let AI search it.
```

Strong:

```text
We convert enterprise AI work into governed, reusable, transferable capability assets.
```

Vietnamese pitch:

```text
OrgMemory giúp doanh nghiệp biến các prompt, workflow, agent và kinh nghiệm vận hành AI
của từng nhân viên thành tài sản AI dùng chung: có owner, backup owner, version,
review, risk, usage và khả năng chuyển giao khi onboarding/offboarding.
```

## Key Risks

Contribution risk:

- Employees may not want to share workflows if it feels like extra work,
  surveillance, or loss of personal advantage.

Quality risk:

- If the asset standard is weak, the registry becomes a low-quality prompt dump.

Enterprise risk:

- Permissions, privacy, connectors, and trust are harder than UI.
- On-prem customers will require backup/restore, auditability, logs,
  monitoring, incident response, and a clear data-retention story.
- A permission leak through Ask Memory, graph, citations, exports, or MCP tools
  would damage enterprise trust immediately.

Feature risk:

- Large platforms can add prompt libraries or connector search. OrgMemory must
  own the asset lifecycle, not generic storage.

## Product Principles

- Automatic detection may create candidates, but official assets require human
  review.
- Raw source data is not automatically knowledge.
- Cleaned knowledge can be a Knowledge Asset, but it is not automatically a
  Capability Asset.
- Do not passively capture all employee AI usage in the current prototype.
- Approved assets must be reusable by someone other than the creator.
- Usage tracking is product evidence, not vanity analytics.
- Missing backup owners are first-class risk.
- Permission filtering must happen before retrieval in production.
- Airbyte output must land in staging first; it must not bypass OrgMemory
  validation and become trusted memory directly.
