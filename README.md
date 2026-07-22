# OrgMemory

OrgMemory is a governed organizational memory layer for enterprise AI work. It
keeps two lifecycle objects distinct:

- **Knowledge Assets**: cleaned, trusted, permission-aware, citable knowledge.
- **Capability Assets**: approved prompts, workflows, agents, playbooks, and
  other reusable AI work with ownership, version, risk, usage, and handover.

The current repository contains a Capability Asset registry plus a canonical
source-ingestion ledger and permission-aware hybrid Knowledge Asset retrieval.
It is a production-shaped POC foundation, not an approved production deployment.

## Current Stack

Java 25, Spring Boot 4.1, Spring Modulith 2.1, Spring AI 2.0, PostgreSQL with
pgvector, Flyway, Gradle 9.6, pnpm 11, React 19, Vite 8, TypeScript 7, Tailwind
CSS 4, shadcn/ui, TanStack Query/Router, and AI SDK UI components.

## Repository

```text
core/          domain, application services, JPA, and Flyway
apps/api/      REST, OIDC, secure search, OpenAPI, migration owner
apps/worker/   background source ingestion and indexing boundary
apps/mcp/      reserved MCP delivery boundary; no runtime implementation yet
integrations/  provider adapters, including the official OpenFGA Java SDK
web/           React app shell, secure search, and document management
docs/          vision, roadmap, decisions, specs, tests, and increments
```

## Local Development

```powershell
.\gradlew.bat demoBootstrap
.\gradlew.bat :apps:api:bootRun --args='--spring.profiles.active=dev'
# In another terminal after Flyway has created the schema:
.\gradlew.bat demoSeed
corepack pnpm -C web install
corepack pnpm -C web gen:api
corepack pnpm -C web dev --host 127.0.0.1 --port 5173
```

- Web: http://localhost:5173
- API health: http://localhost:8080/api/health
- API docs in `dev` only: http://localhost:8080/swagger-ui.html

The development path always uses OIDC plus OpenFGA; there is no `permitAll`
profile. Keycloak authenticates users, explicit issuer/subject bindings map them
to internal users, and OpenFGA grants application permissions. The API can boot
without an LLM key, but authorization fails closed when OpenFGA is unavailable.
Never commit `.env` or provider secrets.

Production starts with `--spring.profiles.active=prod`. That profile has no
local-secret fallbacks: database, OIDC, OpenFGA, object-storage, and OpenAI
settings must be supplied explicitly. The API also rejects known development
credentials and non-HTTPS public OIDC/web origins before serving traffic. See
[`application-prod.yml`](apps/api/src/main/resources/application-prod.yml) for
the required environment-variable names.

The reproducible synthetic POC fixtures are documented in
[`demo/README.md`](demo/README.md). The original XLSX is provenance only; no
spreadsheet-specific code is loaded by the application runtime.

The browser is an OIDC BFF client: it stores only an HttpOnly Spring session
cookie, never Keycloak tokens. REST SDKs and TanStack Query options are generated
with Hey API from `contracts/openapi.json`; regenerate after changing the API
contract.

## Verification

```powershell
.\gradlew.bat --no-daemon compileJava
.\gradlew.bat --no-daemon clean test
corepack pnpm -C web typecheck
corepack pnpm -C web build
```

## Documentation

- [Current architecture](ARCHITECTURE.md)
- [Vision and target architecture](docs/vision.md)
- [Roadmap](docs/roadmap.md)
- [Repository conventions](docs/conventions.md)
- [Architecture decisions](docs/decisions)
- [Current behavior specs](docs/specs)
- [Verification contracts](docs/tests)
- [Active implementation plan](docs/increments/active/2026-07-22-secure-hybrid-retrieval/plan.md)
- [Demo bootstrap plan](docs/increments/active/2026-07-22-reproducible-demo-bootstrap/plan.md)
- [Research report](docs/research/orgmemory_research_report_2026-07-06.md)

Start with `CLAUDE.md` when working as a coding agent.
