# OrgMemory

OrgMemory is an AI Capability Registry: a system of record for an organization's
AI prompts, workflows, agent configurations, and operational know-how.

The MVP goal is to turn individual AI know-how into reusable, governed, and
transferable AI Capability Assets.

## Architecture

```text
core/                 domain + Spring Modulith modules
apps/
  api/                REST API, Flyway migrations, OpenAPI
  mcp/                MCP server surface for future agent access
  worker/             scheduled/background AI and ETL jobs
web/                  Vite + React + TypeScript + Tailwind
```

Stack:

- Spring Boot 4.1
- Java 25
- Gradle Kotlin DSL
- Spring Modulith 2.1
- PostgreSQL + pgvector
- Flyway
- Vite + React 19 + TypeScript + Tailwind v4

OpenAPI can still be exposed by the API for inspection, but this MVP has only
one web client and does not keep a separate `contracts/` folder.

## Quickstart

```powershell
docker compose up -d
.\gradlew.bat build
.\gradlew.bat :apps:api:bootRun
```

In another terminal:

```powershell
pnpm -C web install
pnpm -C web dev
```

URLs:

- API health: http://localhost:8080/api/health
- Web: http://localhost:5173
- MCP later: http://localhost:8081/mcp

## MVP Scope

Build first:

- Capability Asset Registry
- Asset creation form
- Search and discovery
- Usage tracking
- Version history
- Basic review workflow for demo governance
- Lightweight onboarding/offboarding panels for the demo story

Do not build yet:

- Browser extension
- Passive capture from ChatGPT/Claude
- Slack/Notion/Drive integrations
- Knowledge graph database
- Full enterprise SSO
- Marketplace

The source brief says Phase 1 should prove the registry, not passive capture.
The target demo metric is 20 prompts or workflows submitted and reused by at
least 5 people in the demo environment.
