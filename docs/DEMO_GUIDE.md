# OrgMemory Demo Guide

Audience: teacher, project reviewer, or design-partner walkthrough.

## Core One-Liner

OrgMemory turns individual AI prompts, workflows, agents, and operating know-how
into reusable, governed organizational capability assets.

## Run Locally

From `D:\OrgMemory`.

Start database:

```powershell
docker compose up -d
```

Start API:

```powershell
.\gradlew.bat :apps:api:bootRun
```

Start web:

```powershell
pnpm -C web dev --host 127.0.0.1 --port 5173
```

Open:

- Web: http://localhost:5173
- API health: http://localhost:8080/api/health
- API docs: http://localhost:8080/swagger-ui.html

Do not show `.env` or the API key during the presentation.

## 7-Minute Demo Flow

1. Dashboard
   - Show total assets, approved assets, usage, and missing backup-owner risk.
   - Explain that this is a capability dashboard, not a generic wiki.

2. Registry
   - Search or filter by asset type.
   - Example: search `image`, `proposal`, `support`, or `codex`.
   - Open an asset detail page.
   - Point out owner, backup owner, type, risk, status, versioned workflow, and usage.

3. Create Asset
   - Paste a raw AI workflow.
   - Click AI enrichment/Generate.
   - Explain that Spring AI suggests title, summary, type, tags, risk, prompt,
     workflow, input schema, output schema, and examples.
   - Submit for review.

4. Review Queue
   - Show the asset in review workflow.
   - Show workflow visualization.
   - Explain that AI-generated metadata is not automatically trusted; humans
     approve before broad reuse.

5. Ask Memory
   - Ask: `Are there any workflows related to image generation?`
   - Ask: `What approved support workflows can I use?`
   - Explain that Ask Memory ranks live registry assets and can stream through
     Spring AI when enabled.

6. Knowledge Graph
   - Show `/graph`.
   - Explain that the graph is derived from assets, owners, departments, types,
     tags, and processes. It is not a separate graph database yet.

7. Knowledge Transfer
   - Show onboarding/offboarding.
   - Explain that the same registry helps new employees ramp and helps the
     company preserve capability when people leave.

8. Close
   - This is not just a prompt library.
   - It is a memory layer with a sharp distinction: raw sources become cleaned
     Knowledge Assets, and reusable AI work becomes Capability Assets with
     owners, backup owners, status, versioning, risk, review, usage, and
     transfer value.

## Suggested Presentation Script

Many employees now use ChatGPT, Claude, Codex, Cursor, n8n, Dify, and other AI
tools to build small but valuable workflows: sales follow-up prompts, proposal
generators, image-generation briefs, renewal triage agents, support copilots,
policy bots, and code review agents. The problem is that these workflows often
live in private histories, personal notes, or isolated tools.

OrgMemory stores them as structured AI Capability Assets. Each asset has a type,
owner, backup owner, status, risk level, versioned prompt/workflow,
input/output schema, and usage tracking. The MVP proves the loop: capture a raw
AI workflow, enrich it with AI, submit it for review, approve and reuse it, then
use the same registry for onboarding and offboarding.

For production, OrgMemory should also store cleaned enterprise knowledge as
Knowledge Assets. Raw source data is not automatically knowledge; it must be
parsed, cleaned, permissioned, and trusted first.

## Demo Asset Types To Mention

- Prompt Template: Executive Decision Memo Prompt
- Workflow Automation: Customer Feedback Analysis Workflow
- AI Agent: Renewal Risk Triage Agent
- Knowledge Bot: Internal Policy Q&A Bot
- Analytics Brief: Weekly Revenue Forecast Brief
- Content Generator: Campaign Variant Generator, Slide Deck Generator
- Data Extraction: Invoice Exception Extractor
- Evaluation Checklist: AI Output Quality Rubric
- Playbook: Incident Communication Playbook
- Handover Pack: Departing Employee Handover Pack
- Governance Guardrail: PII Redaction Guardrail
- Copilot: Support Reply Copilot

## Troubleshooting

If API is not healthy:

```powershell
Get-NetTCPConnection -LocalPort 8080 -State Listen
Get-Content tmp\orgmemory-api.out.log -Tail 80
Get-Content tmp\orgmemory-api.err.log -Tail 80
```

If web is not open:

```powershell
Get-NetTCPConnection -LocalPort 5173 -State Listen
pnpm -C web dev --host 127.0.0.1 --port 5173
```

If AI enrichment/chat falls back:

- Confirm `ORGMEMORY_AI_MODEL_CHAT=openai`.
- Confirm `OPENAI_API_KEY` or `ORGMEMORY_OPENAI_API_KEY` exists.
- The app has fallback normalization, so the demo can continue.

## Verification Commands

Run before presenting if there is time:

```powershell
.\gradlew.bat --no-daemon test
pnpm -C web typecheck
pnpm -C web build
pnpm dlx @playwright/test@latest test tmp/orgmemory.spec.ts --config=tmp/playwright.config.ts --reporter=line
```
