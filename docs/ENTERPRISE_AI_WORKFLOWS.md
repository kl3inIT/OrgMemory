# OrgMemory Enterprise AI Workflow Catalog

Muc tieu cua catalog nay la lam du lieu demo thuc te hon: OrgMemory khong chi luu prompt don le, ma luu nhieu loai "AI capability asset" ma doanh nghiep thuong dung trong sales, customer success, support, finance, IT, HR, governance, product, va marketing.

## Research Basis

Nhung nguon tham khao chinh:

- McKinsey State of AI 2025: AI value dang xuat hien manh o marketing/sales, strategy, product/service development, software/IT, customer operations, va workflow redesign.
- McKinsey Economic Potential of Generative AI: customer operations, marketing/sales, software engineering, R&D, va knowledge management la cac nhom co tiem nang lon.
- Deloitte AI Dossier: doanh nghiep can nhin AI use case theo function, industry, ROI, risk, va responsible AI.
- Microsoft Work Trend Index 2024: knowledge workers dung AI nhieu, nhung rui ro lon la BYOAI, thieu plan, thieu governance va privacy.
- Google Cloud gen AI use cases: cac use case thuc te tap trung vao support, operations, search/Q&A, summarization, va content/workflow acceleration.
- ServiceNow gen AI use cases: employee service, customer service, ITSM, GRC, va workflow automation la nhom ung dung gan voi enterprise operations.
- IBM knowledge management: RAG, summarization, classification, HR/talent, va customer service la cac nhom knowledge-management phu hop.

## Asset Type Taxonomy

OrgMemory MVP hien co field `assetType` that phan loai capability assets:

| Asset type | Mo ta | Vi du trong demo |
| --- | --- | --- |
| `PROMPT_TEMPLATE` | Prompt co cau truc de tai su dung cho mot cong viec lap lai | Executive Decision Memo Prompt, Proposal Generator |
| `WORKFLOW_AUTOMATION` | Workflow gom nhieu buoc, dau vao/dau ra ro rang | Customer Feedback Analysis Workflow, Product Research Synthesizer |
| `AI_AGENT` | Agent co nhiem vu, logic triage, va next action | Renewal Risk Triage Agent |
| `KNOWLEDGE_BOT` | Bot hoi dap dua tren knowledge/policy da duyet | Internal Policy Q&A Bot |
| `ANALYTICS_BRIEF` | Bien du lieu/ghi chu thanh brief cho quan ly | Weekly Revenue Forecast Brief, Account Insight Brief |
| `CONTENT_GENERATOR` | Tao noi dung co review, brand, compliance | Campaign Variant Generator, Post-demo Follow-up Email |
| `DATA_EXTRACTION` | Trich xuat truong du lieu tu van ban/tai lieu | Invoice Exception Extractor |
| `EVALUATION_CHECKLIST` | Rubric/checklist de danh gia output AI | AI Output Quality Rubric |
| `PLAYBOOK` | Huong dan tac nghiep va talk track duoc version hoa | Incident Communication Playbook, Competitive Battlecard |
| `HANDOVER_PACK` | Tai san phuc vu onboarding/offboarding va backup owner | Departing Employee Handover Pack |
| `GOVERNANCE_GUARDRAIL` | Guardrail cho privacy, PII, policy, risk | PII Redaction Guardrail |
| `COPILOT` | Tro ly trong workflow cua nhan vien, con nguoi duyet cuoi | Support Reply Copilot |

## Seeded Enterprise Assets

Baseline sau Flyway `V5__asset_types_and_enterprise_asset_catalog.sql` co 22 demo assets, trong do 12 assets moi dai dien cho 12 asset types:

| Asset | Type | Department | Status | Why it is realistic |
| --- | --- | --- | --- | --- |
| Executive Decision Memo Prompt | Prompt Template | Operations | Approved | Leaders need repeatable decision memos from raw notes. |
| Renewal Risk Triage Agent | AI Agent | Customer Success | In Review | CS teams triage renewals from usage, cases, and account context. |
| Internal Policy Q&A Bot | Knowledge Bot | People Operations | Approved | Employees ask recurring policy questions; source-aware answers reduce HR load. |
| Campaign Variant Generator | Content Generator | Marketing | Draft | Marketing teams create channel-specific copy variants under brand constraints. |
| Invoice Exception Extractor | Data Extraction | Finance | In Review | Finance teams extract anomalies and follow-ups from invoices and PO data. |
| Weekly Revenue Forecast Brief | Analytics Brief | Sales | Approved | Sales leadership needs weekly forecast summaries and risk accounts. |
| AI Output Quality Rubric | Evaluation Checklist | Governance | Approved | Teams need a common quality gate before approving AI outputs. |
| Incident Communication Playbook | Playbook | IT | Approved | ITSM incidents need safe internal/customer updates. |
| Departing Employee Handover Pack | Handover Pack | People Operations | In Review | Offboarding must preserve workflows, ownership, and backup owners. |
| PII Redaction Guardrail | Governance Guardrail | Governance | Approved | Personal data and customer identifiers need redaction before sharing. |
| Support Reply Copilot | Copilot | Customer Success | Approved | Support agents draft replies faster, but keep human approval. |
| Product Research Synthesizer | Workflow Automation | Product | Draft | Product teams synthesize interviews, support themes, and competitor notes. |

## Demo Thesis

OrgMemory should be explained as a capability layer:

1. Employees already create valuable prompts, workflows, agent behaviors, and AI operating practices.
2. Without a registry, those assets stay in private ChatGPT/Claude histories or personal notes.
3. OrgMemory captures them as structured assets with owner, backup owner, status, version, risk, inputs, outputs, workflow steps, usage, and handover value.
4. The business value is not "more prompts"; it is reuse, governance, and continuity when people change roles or leave.
