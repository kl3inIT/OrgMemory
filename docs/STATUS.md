# OrgMemory Status

Status date: 2026-07-05.

This file is a repository-grounded status summary, not a claim that every command
was run during the latest docs cleanup.

## Implemented

Backend:

- Spring Boot API in `apps/api`
- Spring Modulith domain in `core`
- PostgreSQL/Flyway schema
- seeded organization, departments, users, and enterprise assets
- asset CRUD/list/detail
- asset versions
- approval actions
- backup owner assignment
- usage tracking
- Spring AI enrichment endpoint with fallback
- Spring AI/AI Elements-compatible chat stream
- relational knowledge graph endpoint
- OIDC authentication: Keycloak 26.6.4 dev IdP in compose, Spring Security 7
  resource server, realm-role -> ROLE_* mapping, `/api/me`, `local` profile
  bypass for no-IdP development
- MCP app scaffold
- worker app scaffold

Frontend:

- Vite/React app with route-level pages
- app shell/sidebar/topbar
- dashboard
- registry
- asset detail
- create asset
- review queue
- knowledge transfer
- Ask Memory
- knowledge graph
- analytics/settings
- light/dark mode
- shadcn/ui primitives
- Recharts, React Flow, Cytoscape

## Known Gaps

Product/enterprise:

- authentication is OIDC-backed, but authorization is coarse: realm roles are
  mapped, yet endpoints do not enforce per-role or per-resource (ACL) rules yet
- app_users are not linked to OIDC identities yet (identity mapping design is a
  pre-ingestion task)
- no Airbyte staging integration yet
- no source ingestion tables yet
- no Knowledge Asset table/page yet
- no permission-aware retrieval yet
- no production-grade audit table yet
- no real connector implementation yet
- no deployed pilot hardening yet
- no on-prem backup/restore runbook yet
- no enterprise security review yet

Backend:

- search is mostly structured/keyword/ranker based, not full pgvector semantic
  retrieval
- chat conversations are not persisted
- approval actions require a valid token but not yet the reviewer/admin role
- MCP app exists as scaffold, but domain MCP tools still need implementation

Frontend:

- analytics is still lighter than dashboard
- some workflows are prototype-oriented rather than production-complete
- chunk-size warnings may appear because AI/chat/diagram packages are large

## Prototype-Ready Points

- real backend and database for the current registry prototype
- meaningful asset taxonomy
- create -> enrich -> submit -> review -> approve/reuse flow
- Ask Memory can rank live registry assets
- knowledge graph visualizes asset relationships
- onboarding/offboarding story uses registry ownership/backup-owner concepts

## Verification Commands

Run before presenting or shipping:

```powershell
.\gradlew.bat --no-daemon compileJava
.\gradlew.bat --no-daemon test
pnpm -C web typecheck
pnpm -C web build
pnpm dlx @playwright/test@latest test tmp/orgmemory.spec.ts --config=tmp/playwright.config.ts --reporter=line
```

## Enterprise Readiness Framing

Say:

- This is a product prototype proving the AI Capability Asset workflow.
- Spring AI is integrated, with local fallback for presentation resilience.
- Enterprise auth, source ingestion, Knowledge Assets, permission-aware
  retrieval, audit, MCP tools, and on-prem operations are the next production
  steps.
- It is suitable for thesis/product validation and scoped design-partner review,
  not yet for broad ingestion of real company data.

Do not say:

- Full enterprise production security is complete.
- All connectors are implemented.
- Knowledge Asset ingestion is implemented.
- The knowledge graph is a dedicated graph database.
- Ask Memory is fully permission-aware.
- It is ready for a 50,000-person rollout.
