# OrgMemory Startup Strategy

Status date: 2026-07-05.

OrgMemory should be judged as a real enterprise startup, not a school project.
The project already has potential enterprise design partners, so the key risk is
not abstract market demand. The key risk is delivery, trust, and safe handling
of real enterprise data.

## Business Thesis

OrgMemory should not be positioned as another enterprise search or generic RAG
chatbot. That market is crowded by Microsoft, Google, Glean, Atlassian, and many
internal data platforms.

The wedge is narrower and stronger:

```text
AI Capability Memory for enterprises.
```

OrgMemory turns AI work into governed organizational assets:

- cleaned enterprise Knowledge Assets
- reusable prompts and prompt templates
- AI workflows
- agent configurations
- copilots
- playbooks
- guardrails
- handover packs

Those assets should have owners, backup owners, versions, review status, risk,
permissions, citations, usage events, and onboarding/offboarding value.

## What Changed With Real Enterprise Partners

If enterprises are already willing to pilot around this thesis, the immediate
job is not to prove that the pain exists. It is to convert interest into a
controlled pilot:

- named executive or department sponsor
- pilot charter
- source/data scope
- success metrics
- security and privacy constraints
- deployment environment
- timeline
- paid pilot, LOI, MOU, or written commitment

Do not turn this into an open-ended research build. Enterprise interest must be
captured as scope, access, timelines, and success criteria.

## Positioning

Weak positioning:

```text
Ask questions over company documents.
```

Stronger positioning:

```text
Preserve, govern, reuse, and transfer the AI capabilities your employees create.
```

Vietnamese pitch:

```text
OrgMemory giúp doanh nghiệp biến tri thức đã làm sạch, prompt, workflow, agent
và kinh nghiệm vận hành AI của nhân viên thành tài sản tổ chức có owner, backup
owner, approval, version, permission, audit, usage và handover.
```

## Ideal First Customer Profile

Start with organizations that have:

- many knowledge workers
- repeated sales, support, operations, HR, or engineering workflows
- employees already using ChatGPT, Claude, Codex, Cursor, n8n, Dify, or similar
  tools informally
- sensitive data that favors on-prem or private deployment
- onboarding/offboarding pain
- internal AI governance pressure

Good first departments:

- Sales
- Customer Success and Support
- HR and onboarding
- Operations
- Engineering enablement

## Product Boundary

Do not compete head-on as a connector platform. Use Airbyte for enterprise data
movement when connectors fit.

Do not compete head-on as workflow automation. Integrate with n8n, Dify,
Workato, or internal automation tools where useful.

Do not compete head-on as generic enterprise search. Search and Ask Memory are
interfaces over governed assets, not the whole product.

OrgMemory must own the lifecycle:

```text
capture -> normalize -> review -> approve -> reuse -> measure -> transfer
```

## Pilot Business Model

For real enterprises, prefer:

- paid pilot fee
- implementation/setup fee
- annual on-prem license after pilot
- support/SLA tier
- connector/support add-ons

Avoid pricing by prompt count or token count. Enterprise buyers usually want
predictable platform and support pricing.

## Pilot Success Metrics

Measure outcomes that executives understand:

- number of approved Capability Assets
- number of cleaned Knowledge Assets
- reuse events per department
- time saved in repeated workflows
- onboarding assets reused by new employees
- offboarding assets with backup owner assigned
- high-risk assets reviewed or deprecated
- percentage of AI workflows with owner and approval status

## Kill Risks

These can kill trust quickly:

- permission leakage through Ask Memory, graph, citations, exports, or MCP tools
- silently ingesting private employee AI sessions
- claiming production readiness before auth, ACL, audit, and backup are real
- building broad features instead of a narrow pilot slice
- turning Airbyte staging output into trusted knowledge without OrgMemory review
- letting the registry become a low-quality prompt dump

## Four-Month Business Goal

The goal is not a 50,000-person rollout. The goal is a credible, scoped,
enterprise pilot that can expand.

Best target:

- one enterprise tenant
- one to three departments
- 20-100 active users
- two to three approved sources
- Airbyte-backed staging for source data where connectors fit
- Knowledge Assets and Capability Assets separated
- permission-aware Ask Memory
- audit trail
- clear ROI story

That is enough to turn thesis interest into a serious enterprise product path.
