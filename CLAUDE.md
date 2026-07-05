# CLAUDE.md

This file provides guidance to AI coding agents working in this repository.

OrgMemory is an AI Capability Registry. The product turns individual prompts,
workflows, agent configurations, and AI operating know-how into governed
organizational assets that can be approved, reused, measured, versioned, and
transferred during onboarding or offboarding.

The origin brief is
`Organizational AI Memory_ The Capability Layer for the AI Enterprise.docx`.
Use it as the product source of truth when scope is unclear.

## Product Direction

The core object is an **AI Capability Asset**. It is not just a prompt and not
just a wiki page. It should have owner, backup owner, department, prompt/workflow
content, input/output expectations, status, visibility, version history, usage
events, approval events, and handover metadata.

The MVP is an **AI Capability Registry**:

```text
employee submits prompt/workflow
-> OrgMemory normalizes it into a Capability Asset
-> others search/reuse it
-> usage is tracked
-> review/onboarding/offboarding surfaces show the future governance path
```

The source brief explicitly says Phase 1 should build the registry, not the
passive capture system. Browser extensions, ChatGPT/Claude capture, knowledge
graph, and HRIS integrations are later phases.

## Stack

Spring Boot 4.1, Java 25, Gradle Kotlin DSL, Spring Modulith 2.1, Spring Data
JPA, PostgreSQL + pgvector, Flyway. Frontend: Vite, React 19, TypeScript,
Tailwind v4, TanStack Query/Router-ready. OpenAPI may be exposed by the API for
inspection, but this MVP has only one web client and no separate contract folder.

Spring AI is part of the intended stack, but the first scaffold does not require
an LLM key to boot. Add model-specific starters only when implementing AI
enrichment or embeddings.

## Frontend Component Rules

Use a library-first strategy for web UI:

- Prefer shadcn/ui local components in `web/src/components/ui` for primitives,
  layout surfaces, navigation, forms, overlays, tabs, badges, cards, buttons,
  and inputs.
- If a maintained component or domain library exists for the job, use it before
  hand-rolling. This includes calendar, kanban, data table, command palette,
  editor, chart, resizable panels, and sidebar patterns.
- Add missing shadcn components through the same local-component pattern used in
  this repo and Northstar. Keep `web/components.json` aligned with Tailwind v4,
  the `new-york` style, and `lucide` icons.
- Custom UI code is acceptable only for product-specific composition and state,
  not for rebuilding generic components that already exist.

## Commands

```powershell
docker compose up -d
.\gradlew.bat build
.\gradlew.bat --no-daemon compileJava
.\gradlew.bat :core:test
.\gradlew.bat :apps:api:bootRun

pnpm -C web install
pnpm -C web dev
pnpm -C web typecheck
pnpm -C web build
```

## Architecture

One domain, three deployables:

- `core/`: business logic and Spring Modulith modules under `com.orgmemory.core.*`
- `apps/api/`: REST controllers, OpenAPI, Flyway migration owner
- `apps/mcp/`: future MCP tools for agents such as `search_capability_assets`
- `apps/worker/`: scheduled/background ETL, embeddings, enrichment, handover jobs
- `web/`: Vite SPA with small local API client helpers

Build features in `core` first, then expose them through `api`, `mcp`, or
`worker`. Do not duplicate business logic in delivery apps.

Flyway migrations live in `core/src/main/resources/db/migration`. API runs
migrations. MCP and worker use the existing schema and should keep Flyway off in
normal runtime config.

## Domain Rules

- An approved asset must be reusable by someone other than the creator.
- Approval status matters: `draft`, `in_review`, `approved`, `rejected`,
  `deprecated`.
- Asset metadata must include the AI tool used, because Phase 1 validates
  prompt/workflow submission with structured metadata.
- Missing backup owners are a first-class risk, because offboarding is one of the
  product's core pains.
- Usage tracking is product evidence, not vanity analytics.
- Do not build passive employee monitoring. Capture should be explicit until the
  product has a clear privacy and incentive model.

## Do Not Build Yet

- Browser extension capture
- Slack/Notion/Google Drive ingestion
- Neo4j or a full knowledge graph
- Multi-agent orchestration
- Marketplace
- Full SSO and billing

## Project Skills

Both `.claude/skills` and `.codex/skills` contain the same project-local skills:

- `orgmemory-verify-api-symbol`: use before adding unfamiliar framework/library symbols.
- `orgmemory-static-analysis`: use before claiming backend/web/config/database work is done.
- `orgmemory-create-test`: use when adding or changing behavior that should be protected.

Keep the Claude and Codex copies in sync when editing these skills.

## Verification Gates

Code compiling is not enough. Use these gates before calling a task done:

1. Static/API: `.\gradlew.bat --no-daemon compileJava` and `pnpm -C web typecheck`
2. Context: `.\gradlew.bat --no-daemon clean test`
3. Runtime/render: start API and web, then verify health and at least one browser
   flow

Never use `bootRun` as the context-load gate because it does not exit.

## Write Traps

- Every JPA entity change needs a matching Flyway migration.
- `ddl-auto` must stay `validate`.
- Package root is `com.orgmemory`.
- App classes are `OrgMemoryApiApplication`, `OrgMemoryMcpApplication`, and
  `OrgMemoryWorkerApplication`.
