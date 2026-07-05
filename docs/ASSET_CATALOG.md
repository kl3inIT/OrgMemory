# OrgMemory Asset Catalog

The demo catalog shows that OrgMemory is broader than a prompt library. The MVP
catalog currently stores reusable AI Capability Assets. Production v1 should add
Knowledge Assets as a separate asset kind.

## Knowledge Assets Vs Capability Assets

Use this distinction consistently:

| Layer | Meaning | Examples |
| --- | --- | --- |
| Raw Source | Source-shaped imported data that may be noisy or irrelevant | Slack message, PDF, CRM note, GitHub issue, n8n JSON |
| Knowledge Asset | Cleaned and trusted enterprise knowledge worth retaining | Policy, SOP, product doc, decision record, meeting summary, domain note |
| Capability Candidate | Possible reusable AI capability awaiting review | Detected workflow from a Slack thread, extracted prompt from a doc |
| Capability Asset | Approved reusable AI work that can create output or run a workflow | Prompt template, AI agent, copilot, slide generator, guardrail |

So cleaned knowledge **can be an asset**, but it should be a `Knowledge Asset`,
not a `Capability Asset` unless it includes reusable execution logic,
input/output expectations, owner, versioning, and usage workflow.

## Asset Type Taxonomy

These are Capability Asset types, not raw knowledge types.

| Asset type | Meaning | Demo examples |
| --- | --- | --- |
| `PROMPT_TEMPLATE` | Reusable prompt structure for repeatable tasks | Executive Decision Memo Prompt |
| `WORKFLOW_AUTOMATION` | Multi-step AI workflow with clear inputs/outputs | Customer Feedback Analysis Workflow, n8n Lead Enrichment Automation |
| `AI_AGENT` | Agent behavior/config for triage, coding, or decision support | Renewal Risk Triage Agent, Codex Repo Review Agent |
| `KNOWLEDGE_BOT` | Q&A or RAG-style assistant over approved knowledge | Internal Policy Q&A Bot |
| `ANALYTICS_BRIEF` | Converts data/notes into management-ready brief | Weekly Revenue Forecast Brief |
| `CONTENT_GENERATOR` | Generates reviewed content, slides, images, or video scripts | Campaign Variant Generator, Slide Deck Generator, Product Image Brief |
| `DATA_EXTRACTION` | Extracts structured fields from documents/data | Invoice Exception Extractor |
| `EVALUATION_CHECKLIST` | Rubric/checklist for evaluating outputs | AI Output Quality Rubric |
| `PLAYBOOK` | Versioned operational procedure or talk track | Incident Communication Playbook |
| `HANDOVER_PACK` | Onboarding/offboarding transfer package | Departing Employee Handover Pack |
| `GOVERNANCE_GUARDRAIL` | Privacy, PII, compliance, or quality guardrail | PII Redaction Guardrail |
| `COPILOT` | Human-in-the-loop assistant embedded in a role workflow | Support Reply Copilot |

## Realistic Demo Use Cases

Sales:

- post-demo follow-up email
- proposal generator
- lead qualification prompt
- weekly revenue forecast brief
- account insight brief

Customer Success and Support:

- customer feedback triage
- support reply copilot
- renewal risk triage agent
- onboarding FAQ bot
- voice-of-customer synthesis

Marketing and Content:

- campaign variant generator
- image-generation brief
- ad creative generator
- slide deck generator
- video storyboard/script workflow

Engineering and IT:

- Codex/Claude Code repo review agent
- pull request review checklist
- incident communication playbook
- weekly meeting summarizer

Finance and Operations:

- invoice exception extractor
- policy Q&A
- executive decision memo prompt
- data extraction workflow

Governance and People Ops:

- AI output quality rubric
- PII redaction guardrail
- new hire AI workflow onboarding pack
- departing employee handover pack

## Asset Quality Standard

A good asset should have:

- clear purpose
- required inputs
- expected output
- workflow steps or prompt template
- business process
- AI tool/model
- owner and backup owner
- version
- examples
- limitations or risks
- permission/visibility
- review status
- usage evidence

Weak asset example:

```text
Prompt: Write a better email.
Tag: sales.
Owner: Dat.
```

Strong asset example:

```text
Customer Complaint AI Triage Workflow
- input: customer message, account tier, product area
- steps: summarize -> classify urgency -> draft reply -> suggest escalation
- owner: Support Lead
- backup owner: CS Ops
- status: approved
- risk: medium
- usage count: tracked
- related source: support SOP and Slack triage examples
```
