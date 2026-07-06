# OrgMemory Research Report

## Startup and Product Direction for Enterprise AI Capability Memory

Date: 2026-07-06

Prepared as an individual research report for academic review and startup/product discovery.

---

# Executive Summary

OrgMemory is a startup and research thesis about enterprise AI capability memory. The product should not be framed primarily as a coding project, a chatbot, or a technical demo. It should be framed as a new enterprise product category: a system that helps organizations preserve, govern, reuse, and transfer the AI capabilities created by employees.

The core insight is that companies are not only adopting AI tools. They are also creating new organizational know-how through prompts, workflows, agents, copilots, playbooks, content generators, coding routines, and automation patterns. These assets often remain inside private chat histories, local documents, isolated workflow tools, or informal messages. When employees move teams or leave, companies lose not only documents but also the practical AI operating knowledge that made those employees productive.

The startup thesis is:

OrgMemory turns individual AI work into governed organizational assets with owner, backup owner, version, approval, permission, audit, usage, and handover value.

This is different from generic enterprise search. Microsoft, Google, Glean, Atlassian, and other platforms are already building strong enterprise search, assistant, and agent platforms. OrgMemory should not compete head-on by saying "ask questions over company data." The stronger wedge is AI capability lifecycle management.

The immediate startup risk is not general demand discovery because real enterprise design-partner interest already exists. The immediate risk is trust and product definition: can OrgMemory become a credible enterprise pilot that handles real data, real permissions, real organizational workflows, and real governance expectations?

The recommended product direction is a scoped on-prem enterprise pilot, not a broad company-wide rollout. The first pilot should focus on one to three departments, two to three approved data sources, and a small number of high-value workflows such as sales follow-up, customer support triage, onboarding knowledge transfer, offboarding handover, and internal AI workflow standardization.

# 1. Research Purpose

This report summarizes the product and startup research direction for OrgMemory. It is written as an individual research report for discussion with the professor and for future design-partner conversations.

The purpose is to answer:

- What problem does OrgMemory solve?
- Why is this a startup opportunity rather than only a school project?
- How should the product be positioned?
- Who is the first customer?
- What is the first pilot?
- What should be researched before building too much?
- What should be measured to prove value?
- What risks can kill the product?
- How should the thesis be framed academically?

This report deliberately focuses on product strategy, market positioning, enterprise adoption, and research direction. System design appears only where it affects product feasibility, trust, or enterprise deployment.

# 2. Problem Statement

Organizations are rapidly adopting AI tools through individual employees. A sales representative may have a strong prompt for follow-up emails. A support lead may have a triage workflow. An engineer may have a Claude Code or Codex routine. A marketing manager may have an image-generation brief. A product manager may have an interview synthesis workflow. These are real productivity assets, but they usually remain personal.

The problem is not that companies lack documents. The problem is that companies lack a governed memory of how employees use AI to get work done.

The pain appears in several situations.

## 2.1 Repeated Rebuilding

Different employees rebuild similar prompts, workflows, and agents because they cannot find or trust existing ones. This wastes time and creates inconsistent quality.

## 2.2 Loss of Capability During Turnover

When an employee leaves, the organization may keep their files but lose their AI workflows, prompts, examples, tool settings, and practical operating knowledge.

## 2.3 Weak AI Governance

Many AI workflows are useful but unmanaged. They may have no owner, no review status, no risk level, no version history, and no backup owner. This makes enterprise AI adoption difficult to govern.

## 2.4 Onboarding Friction

New employees need more than a wiki. They need the actual AI workflows and approved assets that experienced employees use to perform their role.

## 2.5 Fragmented AI Work

AI work is spread across ChatGPT, Claude, Copilot, Cursor, Codex, Slack, Teams, Drive, SharePoint, n8n, Dify, Notion, CRM notes, local files, and personal habits. No single memory layer turns these into reusable enterprise assets.

# 3. Product Thesis

The product thesis is that AI capability is becoming a new kind of enterprise asset.

Traditional enterprise knowledge management stores documents, policies, and notes. OrgMemory should go further by storing governed AI capabilities:

- prompts
- workflows
- agents
- copilots
- playbooks
- guardrails
- handover packs
- content generation processes
- decision support routines
- data extraction workflows
- role-specific AI operating knowledge

The product is valuable because it gives these assets a lifecycle:

capture -> normalize -> review -> approve -> reuse -> measure -> transfer -> deprecate

This lifecycle is the main differentiation. A prompt library only stores text. A chatbot answers questions. A workflow automation tool executes flows. OrgMemory should govern the organizational memory of AI capability across tools and roles.

# 4. Why Now

Several market forces make this product timely.

## 4.1 Enterprise AI Is Moving From Experiments To Workflows

Enterprises are no longer only testing AI for simple chat. They are moving toward agentic workflows, role copilots, internal assistants, coding agents, and task automation. This increases the number of reusable AI work patterns that need to be captured and governed.

## 4.2 Employees Are Creating Valuable AI Know-How Before Management Can Govern It

AI adoption often starts from individual productivity. Employees discover what works before the company has a formal process. This creates a gap between actual AI work and official enterprise governance.

## 4.3 Large Platforms Validate The Category But Leave Room For A Focused Wedge

Microsoft, Google, Glean, and Atlassian validate the importance of enterprise AI, search, agents, and organizational knowledge. However, their focus is broad platform adoption. OrgMemory can focus narrowly on AI capability memory, handover, and governance.

## 4.4 On-Prem And Private Deployment Still Matter

Many enterprises are cautious about sending sensitive data to external platforms. An on-prem or private deployment story can be a meaningful wedge, especially for regulated or security-conscious organizations.

## 4.5 AI Governance Is Becoming Operational

Companies need to know which AI assets are approved, which are risky, who owns them, how often they are used, and what happens when owners leave. This is a practical governance problem, not only a technical problem.

# 5. Market Landscape

The market is crowded, but that does not mean OrgMemory has no room. It means OrgMemory must choose a precise product category.

## 5.1 Microsoft 365 Copilot And Copilot Studio

Microsoft is strong in productivity integration, Microsoft Graph, enterprise identity, and agents through Copilot Studio. It can connect to knowledge sources and enable agents inside Microsoft 365 workflows.

Product implication: OrgMemory should not claim to be a better Microsoft assistant. It should complement Microsoft environments by managing reusable AI capability assets across departments, approvals, ownership, and handover.

## 5.2 Google Gemini Enterprise

Google positions Gemini Enterprise as an intranet search, AI assistant, and agentic platform with connectors and permissions-aware access to organizational information.

Product implication: Google validates enterprise AI search and agent platforms. OrgMemory should avoid being only another search layer and instead focus on governed capability lifecycle.

## 5.3 Glean

Glean focuses on Work AI, enterprise search, assistants, automation, and a knowledge graph across enterprise tools.

Product implication: Glean is strong in company context and search. OrgMemory should differentiate through asset ownership, approval, reuse, and knowledge transfer rather than generic answer retrieval.

## 5.4 Atlassian Rovo

Atlassian Rovo brings search, chat, agents, and studio-like capabilities to Atlassian and connected work contexts.

Product implication: Rovo shows that agents and organizational knowledge are merging. OrgMemory can focus on cross-tool governance and transfer of AI workflows.

## 5.5 Workflow And Automation Platforms

n8n, Dify, Workato, and similar platforms help teams build workflows, agents, and automations. They are valuable sources of AI capability artifacts, but they are not necessarily the system of record for organizational AI memory.

Product implication: OrgMemory should integrate with these tools instead of replacing them.

## 5.6 Data Integration Platforms

Airbyte is useful because enterprise data ingestion is painful. It provides connectors and self-managed deployment options. It should be treated as a data movement layer, not as the OrgMemory product itself.

Product implication: OrgMemory should use Airbyte to reduce connector work and focus on memory, governance, and product value.

# 6. Competitive Positioning

Weak positioning:

- "We build a chatbot for company documents."
- "We build a prompt library."
- "We ingest company data and let AI search it."
- "We are a replacement for Microsoft Copilot or Glean."

Stronger positioning:

- "We preserve and govern the AI capabilities your employees create."
- "We turn AI workflows into reusable organizational assets."
- "We help enterprises avoid losing AI operating knowledge during onboarding and offboarding."
- "We give AI enablement teams a system of record for approved prompts, workflows, agents, playbooks, and guardrails."

The product category should be:

Enterprise AI Capability Memory

This phrase is important because it separates OrgMemory from generic knowledge management and generic enterprise search.

# 7. Ideal Customer Profile

The first customers should not be every company. The best early customers have clear pain around knowledge workers, AI adoption, and process repetition.

Strong first customer profile:

- mid-sized to large enterprise
- many knowledge workers
- repeated sales, support, HR, operations, or engineering workflows
- employees already use AI informally
- management wants AI governance
- sensitive data makes private deployment attractive
- onboarding and offboarding are real pains
- departments have measurable repeated tasks

Good first departments:

- Sales
- Customer Success
- Support
- HR and onboarding
- Operations
- Engineering enablement
- AI enablement or digital transformation team

The best design partner is not just a company that is interested. It is a company that can provide:

- a named sponsor
- one or two departments
- clear data scope
- user access for pilot testing
- permission/security requirements
- feedback cadence
- success metrics
- written commitment or paid pilot structure

# 8. Core Use Cases

The first product should focus on use cases that make the value easy to explain.

## 8.1 AI Workflow Registry

Employees submit useful prompts, workflows, agents, and playbooks. OrgMemory normalizes them into structured assets. Reviewers approve or reject them. Other employees reuse them.

Value: reduces repeated rebuilding and improves quality.

## 8.2 Onboarding Memory

A new employee gets recommended approved AI assets for their role, department, and business process.

Value: faster ramp-up and more consistent work.

## 8.3 Offboarding Handover

When an employee leaves, OrgMemory identifies assets they own, assets missing backup owners, and workflows that need transfer.

Value: preserves organizational capability.

## 8.4 AI Governance Registry

AI enablement teams can see which assets are approved, risky, deprecated, missing owner, or frequently used.

Value: gives governance teams visibility without passive surveillance.

## 8.5 Ask Memory

Users ask natural language questions such as "Which approved support workflows can I use?" or "Do we have an asset for image generation?" The answer should cite approved, permissioned assets.

Value: discovery becomes easier without weakening governance.

# 9. Asset Model

The asset model should separate raw information from trusted knowledge and reusable capability.

## 9.1 Raw Source

Raw Source is imported data in its original source shape. It can come from Slack, Teams, Google Drive, SharePoint, GitHub, CRM, n8n, Dify, PDFs, transcripts, or manual uploads.

Raw Source is not automatically knowledge. It may be noisy, stale, duplicated, private, or irrelevant.

## 9.2 Knowledge Asset

Knowledge Asset is cleaned and trusted enterprise knowledge. Examples include policies, SOPs, product documentation, decision records, meeting summaries, and customer process notes.

Cleaned knowledge can be an asset, but it is not automatically a capability.

## 9.3 Capability Candidate

Capability Candidate is a possible reusable AI workflow, prompt, agent, playbook, guardrail, or copilot that should be reviewed.

It may be submitted manually or detected from knowledge and source data.

## 9.4 Capability Asset

Capability Asset is the official governed AI capability. It should include:

- owner
- backup owner
- department
- business process
- AI tool/model
- status
- risk level
- visibility
- required inputs
- expected outputs
- prompt or workflow content
- examples
- version history
- approval history
- usage events
- handover metadata

# 10. Product Scope For Research Stage

Because the project is still in research and early pilot planning, the product should not expand too broadly.

Research-stage product scope should include:

- product thesis validation
- asset taxonomy
- customer discovery interviews
- pilot charter
- capability registry prototype
- onboarding/offboarding story
- Ask Memory discovery story
- governance and approval workflow
- enterprise readiness checklist
- design partner feedback

Research-stage product scope should not yet prioritize:

- broad marketplace
- billing system
- full connector catalog
- full workflow automation platform
- full passive employee monitoring
- complex multi-agent orchestration
- graph database as a core product

# 11. Ingestion Product Strategy

Data ingestion matters because OrgMemory must eventually connect to real enterprise sources. However, ingestion is not the product moat.

The product moat is what happens after data arrives:

- scope approval
- permission preservation
- cleaning
- curation
- candidate detection
- review
- approval
- reuse
- transfer
- audit

## 11.1 Airbyte Role

Airbyte should be used for data movement when connectors fit. It can reduce the need to build connectors from scratch.

Airbyte should move approved data into staging or object storage. OrgMemory should process from staging into memory objects.

This is important because building a connector platform would distract from the product thesis.

## 11.2 Airflow Role

Airflow should not be the default first dependency. It is useful for complex DAG orchestration, backfills, SLA monitoring, and data quality gates. Those needs may appear later.

For the first pilot, Airbyte plus OrgMemory processing is a better fit.

# 12. Trust, Security, And Governance As Product Features

For this product, security is not only a technical requirement. It is part of the value proposition.

Enterprise customers will ask:

- Who can see which assets?
- Does Ask Memory respect permissions?
- Can the system leak restricted data through summaries?
- Can an employee's private AI work be captured without consent?
- Who approved this asset?
- Who owns this workflow?
- What happens when the owner leaves?
- Can we audit usage?
- Can we deploy on-prem?
- Can we remove data?

These questions should shape the product.

Non-negotiable trust requirements:

- identity integration
- role-based access
- source ACL snapshot
- permission-aware retrieval
- audit log
- owner and backup owner
- review workflow
- data retention policy
- sensitive data handling
- backup and restore
- incident response

Relevant security references:

- OWASP ASVS for web application security controls
- OWASP Top 10 for LLM Applications for prompt injection and sensitive information disclosure risks
- NIST AI RMF Generative AI Profile for AI trustworthiness framing
- NIST Cybersecurity Framework 2.0 for enterprise risk governance

# 13. Pilot Strategy

The first pilot should be narrow and credible.

Recommended pilot scope:

- one enterprise tenant
- one to three departments
- 20 to 100 active users
- two to three approved sources
- manual submit and file upload
- one document source
- one workflow source
- approved Capability Asset registry
- Knowledge Asset concept
- review and approval
- Ask Memory with citations
- onboarding/offboarding story
- audit trail

The first pilot should not be a 50,000-person rollout. A large company can be the customer, but the pilot must start with a controlled department scope.

## 13.1 Four-Month Pilot Plan

| Month | Product Goal | Evidence Needed |
| --- | --- | --- |
| Month 1 | Validate pilot charter and enterprise trust requirements | sponsor, department scope, data scope, success metrics, security checklist |
| Month 2 | Prove asset lifecycle and controlled ingestion | submitted assets, reviewed assets, first Knowledge Assets, Airbyte or controlled import path |
| Month 3 | Prove discovery and reuse | Ask Memory with citations, usage tracking, approved assets reused by pilot users |
| Month 4 | Prove enterprise readiness direction | audit report, backup/restore plan, onboarding/offboarding story, pilot feedback |

## 13.2 Pilot Success Metrics

Business metrics:

- number of approved Capability Assets
- number of curated Knowledge Assets
- number of reuse events
- time saved in repeated workflows
- onboarding time reduction hypothesis
- offboarding assets preserved
- percentage of assets with backup owner

Governance metrics:

- percentage of assets reviewed
- number of risky assets flagged
- number of assets deprecated
- number of permission issues found in testing
- number of assets with clear owner

Adoption metrics:

- weekly active pilot users
- assets used per department
- repeated search queries
- assets submitted by employees
- reviewer approval throughput

## 13.3 Design Partner Interview Plan

The next research step should be structured interviews with the enterprise design partner. The goal is not only to ask whether the product is interesting. The goal is to discover the exact workflow pain, the political buyer, the security blocker, and the measurable pilot outcome.

Recommended interview groups:

- executive sponsor or department sponsor
- AI enablement or digital transformation lead
- IT/security representative
- HR or onboarding owner
- department manager
- active AI power user
- employee who regularly creates prompts/workflows
- reviewer or governance stakeholder

Important interview questions:

- Which AI tools are employees already using unofficially?
- Which repeated workflows are being rebuilt by different people?
- What knowledge is usually lost when employees leave?
- What onboarding tasks take the longest today?
- Which AI outputs require review before reuse?
- Which data sources are allowed for a pilot?
- What data sources are too sensitive for the first pilot?
- Who must approve an on-prem deployment?
- What would make the pilot successful after four months?
- What evidence would justify expansion after the pilot?

The output of these interviews should be a short pilot charter, not only interview notes.

## 13.4 Pilot Package To Offer

The first pilot package should be simple enough for the enterprise to understand and narrow enough to deliver.

Suggested pilot package:

- one department or business process
- one sponsor
- one reviewer group
- one approved document source
- one approved workflow source or manual workflow upload
- 20 to 100 active users
- weekly feedback session
- monthly governance review
- final pilot report with usage, approved assets, risks, and expansion recommendation

The pilot should not promise enterprise-wide deployment. It should promise evidence.

Expected pilot outputs:

- approved Capability Asset catalog
- curated Knowledge Asset sample
- onboarding asset pack for one role
- offboarding handover example
- Ask Memory discovery demo with citations
- risk report for missing owners and high-risk assets
- recommendation for whether to expand

# 14. Business Model Hypotheses

OrgMemory should not start with token-based pricing. Enterprise buyers prefer predictable pricing.

Possible business model:

- paid pilot fee
- implementation/setup fee
- annual on-prem license
- support and SLA tier
- connector or deployment support add-ons

The early business goal should be to convert design partner interest into written commitment:

- paid pilot
- LOI
- MOU
- sponsor-approved pilot scope
- data access agreement
- success metric agreement

The product should avoid becoming unpaid custom consulting. Each pilot should create reusable product learning.

## 14.1 Buyer Hypothesis

The likely first buyer is not an individual employee. The buyer is probably one of:

- CIO or IT director
- Head of AI enablement
- Digital transformation leader
- Operations leader
- HR/onboarding leader for a specific use case
- Department head with repeated knowledge-worker workflows

The strongest initial buyer is the person responsible for making AI adoption measurable and governable.

## 14.2 Budget Hypothesis

OrgMemory could fit several budget categories:

- AI enablement budget
- digital transformation budget
- knowledge management budget
- internal productivity budget
- compliance/governance budget
- onboarding and training budget

This matters because the pitch changes by buyer. For AI enablement, the pitch is governance and reuse. For HR, the pitch is onboarding and offboarding. For operations, the pitch is repeated workflow productivity. For IT/security, the pitch is permission-aware and on-prem governance.

## 14.3 Pricing Direction

At research stage, exact pricing should not be fixed. The goal is to learn willingness to pay.

Possible pricing structure:

- paid pilot: fixed fee for setup, discovery, and pilot support
- annual license: based on enterprise size, active users, and deployment scope
- on-prem support: separate support/SLA package
- connector support: priced by complexity or number of supported sources

Avoid pricing directly by token count. The customer should buy OrgMemory as an enterprise memory/governance platform, not as a raw AI API wrapper.

# 15. Product Moat Hypotheses

OrgMemory's moat will not come from having more connectors than Airbyte or better search than Google. The moat must come from product workflow and enterprise trust.

Potential moat:

- structured AI capability ontology
- approval and version lifecycle
- owner and backup owner model
- onboarding/offboarding handover workflow
- usage evidence and reuse analytics
- permission-aware asset discovery
- integration across AI tools and workflow tools
- enterprise trust in on-prem/private deployment
- accumulated catalog of high-quality assets and patterns

The long-term product could become the system of record for enterprise AI capability.

# 16. Academic Framing

The academic framing should focus on organizational AI memory and capability transfer.

Possible thesis framing:

How can enterprises preserve, govern, reuse, and transfer AI capabilities created by employees while respecting privacy, permissions, and organizational ownership?

This connects several research areas:

- knowledge management
- organizational memory
- AI governance
- human-AI collaboration
- enterprise software adoption
- permission-aware retrieval
- workflow reuse
- employee onboarding and offboarding

# 17. Research Questions For Professor Discussion

Suggested questions:

1. Should the thesis frame OrgMemory as enterprise knowledge management, AI governance, or AI capability memory?
2. How much real enterprise pilot evidence is needed for the research to be convincing?
3. How should employee privacy and consent be handled when AI workflows are captured?
4. Which evaluation metrics are academically meaningful: reuse rate, onboarding speed, asset quality, governance coverage, or handover preservation?
5. Should Knowledge Assets and Capability Assets be treated as separate research objects?
6. How should the thesis compare OrgMemory with Microsoft Copilot, Glean, Gemini Enterprise, and Rovo without becoming a generic competitor analysis?
7. What ethical review is needed before handling real enterprise data?
8. Is on-prem deployment an academic contribution or mainly a product requirement?

# 18. Research Evidence To Collect Next

The next research stage should collect evidence instead of only building features.

Evidence to collect:

- interviews with enterprise sponsor and department leads
- list of current AI workflows employees already use
- examples of lost knowledge during onboarding/offboarding
- current process for approving AI tools or prompts
- risk concerns from IT/security stakeholders
- list of data sources the enterprise is willing to pilot
- willingness to pay or commit to a pilot
- expected ROI areas
- top repeated workflows by department

Interview targets:

- AI enablement lead
- department manager
- IT/security lead
- HR/onboarding owner
- sales or support team lead
- active AI power user
- reviewer/governance stakeholder

# 19. Professor Reporting Narrative

When reporting to the professor, the strongest narrative should be:

1. Enterprises are adopting AI through individual employees before governance catches up.
2. This creates hidden AI capability assets: prompts, workflows, agents, playbooks, and operating knowledge.
3. These assets are currently hard to preserve, approve, reuse, measure, and transfer.
4. Existing platforms focus heavily on search, assistants, automation, or connectors.
5. OrgMemory focuses on AI capability lifecycle and organizational transfer.
6. The research contribution is the model and workflow for turning AI work into governed organizational memory.
7. The startup contribution is a scoped on-prem enterprise pilot for real companies.

The professor-facing message should avoid sounding like a list of features. It should sound like a research problem:

How can an enterprise convert individual AI work into governed organizational memory without violating privacy, permissions, or trust?

Supporting evidence to show:

- market landscape and why the timing is right
- asset taxonomy
- distinction between raw source, knowledge asset, candidate, and capability asset
- pilot use cases
- enterprise trust requirements
- evaluation metrics
- roadmap for evidence collection

# 20. Product Risks

| Risk | Product Impact | Mitigation |
| --- | --- | --- |
| Positioning as generic chatbot | Competes directly with large platforms | Position as AI capability memory and handover |
| Permission leakage | Destroys enterprise trust | permission-aware retrieval, ACL snapshots, audit tests |
| Low-quality asset catalog | Users stop trusting registry | review workflow, quality rubric, deprecation |
| Employee surveillance concern | Low contribution and resistance | user-controlled capture, transparent policy, no passive capture early |
| Overbuilding infrastructure | Slow pilot delivery | use Airbyte, keep pilot scope narrow |
| No clear ROI | Sponsor loses interest | measure reuse, onboarding, handover, time saved |
| Too much custom work | Startup becomes consulting | reusable product primitives, repeatable pilot package |

# 21. Conclusion

OrgMemory is promising if it stays focused on startup/product value instead of becoming a broad technical platform too early.

The product should not be sold as "AI search over documents." It should be sold as enterprise AI capability memory: a governed way to preserve and reuse the AI workflows employees create.

The research should focus on product category definition, enterprise trust, asset lifecycle, pilot evidence, and measurable organizational value. The technical system matters, but only as the mechanism that supports the product thesis.

The next milestone should be a professor-ready and enterprise-ready research package:

- clear thesis
- market positioning
- pilot charter
- asset model
- governance model
- success metrics
- risks and mitigations
- evidence from real enterprise discussions

# References

- Microsoft Copilot Studio knowledge sources: https://learn.microsoft.com/en-us/microsoft-copilot-studio/knowledge-copilot-studio
- Microsoft 365 Copilot agents overview: https://learn.microsoft.com/en-us/microsoft-365/copilot/extensibility/agents-overview
- Google Gemini Enterprise documentation: https://docs.cloud.google.com/gemini/enterprise/docs
- Google Gemini Enterprise product page: https://cloud.google.com/gemini-enterprise
- Glean Work AI platform: https://www.glean.com/
- Atlassian Rovo product page: https://www.atlassian.com/software/rovo
- Atlassian Rovo agents documentation: https://support.atlassian.com/rovo/docs/agents/
- Airbyte data replication connectors: https://docs.airbyte.com/integrations
- Airbyte sources, destinations, and connectors: https://docs.airbyte.com/platform/move-data/sources-destinations-connectors
- Airbyte Self-Managed Enterprise: https://docs.airbyte.com/platform/enterprise-setup
- Airbyte abctl deployment: https://docs.airbyte.com/platform/deploying-airbyte/abctl
- OWASP Application Security Verification Standard: https://owasp.org/www-project-application-security-verification-standard/
- OWASP Top 10 for Large Language Model Applications: https://owasp.org/www-project-top-10-for-large-language-model-applications/
- NIST AI Risk Management Framework Generative AI Profile: https://www.nist.gov/publications/artificial-intelligence-risk-management-framework-generative-artificial-intelligence
- NIST Cybersecurity Framework 2.0: https://www.nist.gov/cyberframework
- McKinsey, Building the foundations for agentic AI at scale: https://www.mckinsey.com/capabilities/mckinsey-technology/our-insights/building-the-foundations-for-agentic-ai-at-scale
