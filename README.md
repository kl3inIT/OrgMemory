# OrgMemory

OrgMemory is an organizational AI memory system. It manages two related but
different asset kinds:

- **Knowledge Assets**: cleaned, trusted enterprise knowledge such as policies,
  SOPs, product docs, decision records, meeting summaries, and domain notes.
- **Capability Assets**: reusable AI prompts, workflows, agents, copilots,
  generators, guardrails, and handover packs that can create an output or run a
  repeatable workflow.

The current MVP implements the Capability Asset registry first. Production v1
should add Knowledge Assets and source ingestion.

The MVP proves this loop:

```text
employee submits AI workflow
-> OrgMemory normalizes it into a Capability Asset
-> reviewer approves it
-> other users search, understand, and reuse it
-> usage and ownership signals support onboarding/offboarding
```

## Current Stack

- Java 25
- Spring Boot 4.1
- Spring Modulith 2.1
- Spring AI 2.0
- Spring Data JPA
- PostgreSQL 18 + pgvector
- Flyway
- Gradle Kotlin DSL
- Vite 8 + React 19 + TypeScript 6
- Tailwind CSS v4
- shadcn/ui local primitives
- TanStack Router and Query
- AI Elements-compatible chat stream
- React Flow and Cytoscape for workflow/graph visualization

## Repository Layout

```text
core/                 domain model, services, repositories, Flyway migrations
apps/api/             REST API, Spring AI endpoints, OpenAPI, health
apps/mcp/             Spring AI MCP server scaffold for future agent access
apps/worker/          worker scaffold for future ingestion/enrichment jobs
web/                  Vite React web app
docs/                 product, architecture, roadmap, demo, and status docs
```

Business logic belongs in `core/` first. Delivery apps expose it through REST,
MCP, worker jobs, or the web UI.

## Local Quickstart

Start PostgreSQL:

```powershell
docker compose up -d
```

Run backend:

```powershell
.\gradlew.bat :apps:api:bootRun
```

Run frontend:

```powershell
pnpm -C web install
pnpm -C web dev --host 127.0.0.1 --port 5173
```

Open:

- Web: http://localhost:5173
- API health: http://localhost:8080/api/health
- API docs: http://localhost:8080/swagger-ui.html
- MCP app, optional scaffold: http://localhost:8081

## AI Configuration

The app boots without an OpenAI key. AI enrichment/chat falls back to local
normalization when Spring AI is disabled or the model call fails.

Use `.env` or environment variables:

```properties
OPENAI_API_KEY=...
ORGMEMORY_AI_MODEL_CHAT=openai
ORGMEMORY_OPENAI_MODEL=gpt-5.5
```

Do not commit `.env`.

## Verification

Use these before demo or after code changes:

```powershell
.\gradlew.bat --no-daemon compileJava
.\gradlew.bat --no-daemon test
pnpm -C web typecheck
pnpm -C web build
```

Optional browser smoke:

```powershell
pnpm dlx @playwright/test@latest test tmp/orgmemory.spec.ts --config=tmp/playwright.config.ts --reporter=line
```

## Documentation

- [Product Brief](docs/PRODUCT_BRIEF.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Roadmap](docs/ROADMAP.md)
- [Status](docs/STATUS.md)
- [Asset Catalog](docs/ASSET_CATALOG.md)
- [Demo Guide](docs/DEMO_GUIDE.md)

## Non-Goals For Current MVP

- Full Knowledge Asset ingestion and source management
- Passive surveillance of employee ChatGPT/Claude usage
- Full enterprise SSO/SCIM
- Broad connector catalog
- Dedicated graph database
- Marketplace
- Billing
- Full production permission system
