# OrgMemory MVP Audit

Last verified locally: July 5, 2026.

## Scope

In scope for MVP:

- AI Capability Registry for prompts, workflows, agents, copilots, playbooks, guardrails, and handover assets.
- Create asset flow with AI enrichment.
- Human review workflow: draft -> in review -> approved/rejected/deprecated.
- Search/filter/reuse and usage tracking.
- Dashboard, Registry, Create, Review Queue, Knowledge Transfer, Ask Memory, Analytics, Settings.
- Spring AI backed real AI calls using OpenAI key from environment.
- AI Elements chat UI streaming from Spring endpoint.

Out of scope for MVP:

- Enterprise auth/SSO and role enforcement.
- Browser extension/passive capture.
- Full semantic vector search UI.
- Production deployment hardening.
- Multi-tenant security controls beyond seeded demo data.

## Backend Audit

Implemented:

- Spring Boot API in `apps/api`.
- PostgreSQL/Flyway schema in `core/src/main/resources/db/migration`.
- Domain modules for organizations, departments, users, capability assets, versions, usage events, and approval events.
- `AssetType` enum with 12 categories:
  `PROMPT_TEMPLATE`, `WORKFLOW_AUTOMATION`, `AI_AGENT`, `KNOWLEDGE_BOT`, `ANALYTICS_BRIEF`, `CONTENT_GENERATOR`, `DATA_EXTRACTION`, `EVALUATION_CHECKLIST`, `PLAYBOOK`, `HANDOVER_PACK`, `GOVERNANCE_GUARDRAIL`, `COPILOT`.
- `GET /api/assets` supports `status`, `assetType`, and `q`.
- `POST /api/assets` creates a draft and version.
- `PATCH /api/assets/{id}/submit-review|approve|reject|deprecate` records review actions.
- `POST /api/assets/{id}/usage` records usage.
- `GET /api/assets/{id}/versions` returns workflow/prompt/schema version data.
- `POST /api/ai/assets/normalize` calls Spring AI when enabled and returns structured asset draft metadata.
- `POST /api/ai/chat` streams AI SDK UI Message Stream-compatible frames for AI Elements.

Verified backend state:

- Flyway migrated local DB from v3 to v5.
- Baseline seed catalog contains 22 enterprise-demo assets.
- Local API health endpoint returns ok.
- AI normalize returned `source=spring-ai` and a typed draft for a support copilot.
- Chat stream endpoint returned status 200 and UI message stream frames in earlier verification.

Known backend gaps:

- Search is keyword filtering, not yet pgvector semantic retrieval.
- Approval permissions are not enforced by real auth.
- Usage tracking records events but does not yet distinguish copied/run/shared UX actions deeply.
- Chat does not persist conversation history in the database.

## Frontend Audit

Implemented:

- Vite/React app split into routes and pages rather than one giant `App.tsx`.
- TanStack Router routes:
  `/`, `/registry`, `/create`, `/review`, `/transfer`, `/ask`, `/analytics`, `/settings`.
- shadcn primitives used for shell, sidebar, cards, tables, tabs, selects, buttons, badges, alerts, charts, progress, avatar, and forms.
- Light/dark theme via shadcn token layer.
- Dashboard uses shadcn chart wrapper plus Recharts.
- Registry has status/type filters and uses real backend assets.
- Create page supports AI enrichment, Save Draft, and Submit for Review as separate real API actions.
- Review Queue shows persisted version workflow data through React Flow.
- Ask Memory uses AI Elements conversation/message/prompt input components.
- Knowledge Transfer page uses the same asset registry data for onboarding/offboarding story.

Known frontend gaps:

- Some owner/department display names are still demo-mapped by index instead of joining API user records.
- Asset detail is represented inside Registry/Review flow; there is no dedicated `/assets/:id` detail route yet.
- Analytics page is lightweight placeholder compared with Dashboard.
- The build has chunk-size warnings from AI Elements/Streamdown and diagram-related packages; build still succeeds.

## Verification

Commands that passed:

```powershell
.\gradlew.bat --no-daemon test
pnpm -C web typecheck
pnpm -C web build
pnpm dlx @playwright/test@latest test tmp/orgmemory.spec.ts --config=tmp/playwright.config.ts --reporter=line
```

Playwright result:

- 9 tests passed.
- Covered route render, sidebar shell, Registry asset type filter, Review React Flow workflow visualization, and Create Submit for Review flow.

Local services verified:

- API: `http://localhost:8080/api/health`
- Web: `http://localhost:5173`

## Demo Readiness Verdict

Ready for MVP demo with honest framing.

Strong demo points:

- Real Spring Boot API and PostgreSQL data, not static frontend-only mock data.
- Real Spring AI/OpenAI integration for enrichment and chat.
- Asset taxonomy is broad enough to look like enterprise capability memory, not a tiny prompt library.
- UI flow demonstrates capture -> enrich -> submit -> review -> search/filter -> reuse -> handover.

Say clearly in presentation:

- This is an MVP proving behavior and workflow.
- Production auth, semantic search, and enterprise integrations are next phase.
