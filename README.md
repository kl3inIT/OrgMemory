# OrgMemory

OrgMemory is an organizational AI memory system. It manages two related but
different asset kinds:

- **Knowledge Assets**: cleaned, trusted enterprise knowledge such as policies,
  SOPs, product docs, decision records, meeting summaries, and domain notes.
- **Capability Assets**: reusable AI prompts, workflows, agents, copilots,
  generators, guardrails, and handover packs that can create an output or run a
  repeatable workflow.

The current product prototype implements the Capability Asset registry first.
Enterprise pilot work must add Knowledge Assets, source ingestion, identity,
permissions, audit, and on-prem operations before real company-wide data is
loaded.

The current prototype proves this loop:

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
- Airbyte planned as the enterprise data-movement layer for pilot ingestion
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
docs/                 product, architecture, roadmap, readiness, and status docs
```

Business logic belongs in `core/` first. Delivery apps expose it through REST,
MCP, worker jobs, or the web UI.

For enterprise pilots, Airbyte should move approved source data into staging.
OrgMemory should then transform staged data into Raw Source Objects, Knowledge
Assets, Capability Candidates, and approved Capability Assets. Airbyte is not
the domain memory layer.

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

Use these before presenting, piloting, or after code changes:

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
- [Startup Strategy](docs/STARTUP_STRATEGY.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Roadmap](docs/ROADMAP.md)
- [Enterprise Readiness](docs/ENTERPRISE_READINESS.md)
- [Status](docs/STATUS.md)
- [Asset Catalog](docs/ASSET_CATALOG.md)
- [Walkthrough Guide](docs/DEMO_GUIDE.md)

## Not Production-Ready Yet

- Full Knowledge Asset ingestion and source management
- Airbyte staging integration
- Full enterprise SSO/SCIM
- Permission-aware retrieval over real enterprise ACLs
- Security review against OWASP ASVS and OWASP LLM Top 10
- On-prem backup, monitoring, incident response, and admin runbook
- Passive surveillance of employee ChatGPT/Claude usage
- Broad connector catalog
- Dedicated graph database
- Marketplace
- Billing
