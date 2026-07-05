# OrgMemory Demo Guide

Audience: teacher / MVP presentation.

Core one-liner:

OrgMemory turns individual AI prompts, workflows, agents, and operating know-how into reusable, governed organizational capability assets.

## Run Locally

From `D:\OrgMemory`.

Start database:

```powershell
docker compose up -d
```

Start API with environment from `.env`:

```powershell
Get-Content .env | ForEach-Object {
  if ($_ -match '^\s*#' -or $_ -notmatch '=') { return }
  $parts = $_ -split '=', 2
  [Environment]::SetEnvironmentVariable($parts[0].Trim(), $parts[1].Trim(), 'Process')
}
.\gradlew.bat :apps:api:bootRun
```

Start web:

```powershell
pnpm -C web dev --host 127.0.0.1 --port 5173
```

Open:

- Web: http://localhost:5173
- API health: http://localhost:8080/api/health

Do not show the API key in the presentation.

## 7 Minute Demo Flow

1. Dashboard
   - Show the organization has many AI capability assets.
   - Point out approved assets, missing backup owners, usage trend, risk alerts.

2. Registry
   - Search or filter by Type.
   - Example filter: `AI Agent` -> `Renewal Risk Triage Agent`.
   - Explain that OrgMemory stores prompt/workflow metadata, ownership, risk, status, and usage.

3. Create New Asset
   - Paste or use the seeded raw workflow text.
   - Click `Generate` to show Spring AI enrichment.
   - Mention AI suggests title, summary, asset type, tags, risk level, prompt, workflow, schemas.
   - Click `Submit for Review`.

4. Review Queue
   - Show the submitted asset appears in the review workflow.
   - Show React Flow workflow visualization.
   - Explain human approval is required before broad reuse.

5. Ask Memory
   - Ask: `What approved support or customer workflows do we have?`
   - Explain the chat UI is AI Elements streaming from Spring AI.
   - Use matched assets on the right as the product behavior: find, understand, then reuse.

6. Knowledge Transfer
   - Show onboarding and offboarding.
   - Explain that the same registry helps new employees ramp and helps the company keep capability when employees leave.

7. Close
   - This is not just a prompt library.
   - It is a capability layer: reusable assets, owners, backup owners, review, versioning, usage, and handover.

## Asset Types To Mention

Use these examples if asked why the catalog is realistic:

- Prompt Template: Executive Decision Memo Prompt.
- Workflow Automation: Customer Feedback Analysis Workflow.
- AI Agent: Renewal Risk Triage Agent.
- Knowledge Bot: Internal Policy Q&A Bot.
- Analytics Brief: Weekly Revenue Forecast Brief.
- Content Generator: Campaign Variant Generator.
- Data Extraction: Invoice Exception Extractor.
- Evaluation Checklist: AI Output Quality Rubric.
- Playbook: Incident Communication Playbook.
- Handover Pack: Departing Employee Handover Pack.
- Governance Guardrail: PII Redaction Guardrail.
- Copilot: Support Reply Copilot.

## Suggested Presentation Script

"Many employees now use ChatGPT or Claude to build small but valuable workflows: sales follow-up prompts, renewal triage agents, support copilots, policy bots, and finance extraction workflows. The problem is that those workflows live in private chat histories or personal notes. When someone moves teams or leaves, the company loses the capability.

OrgMemory stores those workflows as structured AI capability assets. Each asset has a type, owner, backup owner, approval status, risk level, versioned prompt/workflow, input/output schema, and usage tracking. The MVP proves the loop: capture an AI workflow, enrich it with AI, submit for review, approve and reuse it, then use the same asset graph for onboarding and offboarding."

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

If AI enrichment fails:

- Check `.env` contains `OPENAI_API_KEY` and `ORGMEMORY_AI_MODEL_CHAT=openai`.
- The app has local fallback normalization, so the demo can continue, but say real AI is disabled if fallback appears.

## Verification Commands

Run before presenting if there is time:

```powershell
.\gradlew.bat --no-daemon test
pnpm -C web typecheck
pnpm dlx @playwright/test@latest test tmp/orgmemory.spec.ts --config=tmp/playwright.config.ts --reporter=line
```
