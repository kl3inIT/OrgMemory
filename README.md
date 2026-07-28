# OrgMemory

OrgMemory is a governed organizational memory layer for enterprise AI work.
It turns uploads and approved connector evidence into versioned, citable
Knowledge Assets without weakening source permissions.

The current repository contains a canonical source-ingestion ledger, stable
Knowledge Asset identities with immutable versions, permission-aware hybrid
retrieval, an in-app Assistant, and the foundations for a secure knowledge
graph. It is a production-shaped POC foundation, not an approved production
deployment.

## Current Stack

Java 25, Spring Boot 4.1, Spring Modulith 2.1, Spring AI 2.0, PostgreSQL with
pgvector, Flyway, Gradle 9.6, pnpm 11, React 19, Vite 8, Next.js 16, Fumadocs,
TypeScript, Tailwind CSS 4, shadcn/ui, TanStack Query/Router, and AI SDK UI
components.

## Repository

```text
core/          domain, application services, JPA, and Flyway
apps/api/      REST, OIDC, secure search, OpenAPI, migration owner
apps/worker/   background source ingestion and indexing boundary
apps/mcp/      authenticated read-only MCP delivery adapter
apps/cli/      authenticated Skill and MCP command-line client
apps/web/      React product shell, secure search, and Asset management
apps/docs/     independent public Fumadocs portal
integrations/  provider adapters, including the official OpenFGA Java SDK
docs/          vision, roadmap, decisions, specs, tests, and increments
package.json + pnpm-workspace.yaml + pnpm-lock.yaml
               root ownership for the three Node packages
```

## Local Development

```powershell
.\gradlew.bat demoBootstrap
.\gradlew.bat :apps:api:bootRun --args='--spring.profiles.active=dev'
# In another terminal after Flyway has created the schema:
.\gradlew.bat demoSeed
corepack pnpm install --frozen-lockfile
corepack pnpm --filter @orgmemory/web gen:api
corepack pnpm --filter @orgmemory/web dev --host 127.0.0.1 --port 5173
corepack pnpm --filter @orgmemory/docs dev
# Containerized docs without starting product dependencies:
docker compose --profile docs up --build docs
```

- Web: http://localhost:5173
- Docs: http://localhost:3000
- API health: http://localhost:8080/api/health
- API docs in `dev` only: http://localhost:8080/swagger-ui.html

The development path always uses OIDC plus OpenFGA; there is no `permitAll`
profile. Keycloak authenticates users, explicit issuer/subject bindings map them
to internal users, and OpenFGA grants application permissions. The API can boot
without an LLM key only when
`ORGMEMORY_ASSISTANT_RETRIEVAL_ENGINE=CANONICAL_HYBRID` is selected explicitly.
The default `GRAPH_RAG` runtime requires configured chat and embedding models;
authorization fails closed when OpenFGA is unavailable.
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
corepack pnpm --filter @orgmemory/web typecheck
corepack pnpm --filter @orgmemory/web build
corepack pnpm --filter @orgmemory/docs check
corepack pnpm --filter @orgmemory/docs build
```

## Documentation

- [Current architecture](ARCHITECTURE.md)
- [Vision and target architecture](docs/vision.md)
- [Roadmap](docs/roadmap.md)
- [Repository conventions](docs/conventions.md)
- [Architecture decisions](docs/decisions)
- [Current behavior specs](docs/specs)
- [Verification contracts](docs/tests)
- [Knowledge Asset foundation increment](docs/increments/completed/2026-07-23-knowledge-asset-foundation/plan.md)
- [Demo bootstrap plan](docs/increments/active/2026-07-22-reproducible-demo-bootstrap/plan.md)
- [Research report](docs/research/orgmemory_research_report_2026-07-06.md)

Start with `CLAUDE.md` when working as a coding agent.
