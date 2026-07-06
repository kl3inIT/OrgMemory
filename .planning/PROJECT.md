# OrgMemory

## What This Is

OrgMemory is an AI Capability Registry — the organizational memory layer for enterprise AI work. It turns individual prompts, workflows, agent configurations, and AI operating know-how into governed organizational assets that can be approved, reused, measured, versioned, and transferred during onboarding/offboarding. Target deployment is on-prem enterprise, with real design-partner opportunity already available.

## Core Value

The lifecycle of an AI Capability Asset — capture → normalize → review → approve → reuse → measure → transfer → deprecate — with permission safety strong enough that an enterprise trusts it with real workflows, permissions, and data.

## Business Context

- **Customer**: Enterprise AI enablement teams / team leads; employees consume approved assets
- **Revenue model**: On-prem enterprise product (design-partner pilot first; billing out of scope for now)
- **Success metric**: A deployable scoped pilot (1 tenant, 1-3 departments, 20-100 users) that a design partner runs on real data
- **Strategy notes**: `Organizational AI Memory_ The Capability Layer for the AI Enterprise.docx` (origin brief), `docs/PRODUCT_BRIEF.md`, `docs/ENTERPRISE_READINESS.md`

## Requirements

### Validated

<!-- Inferred from the existing registry prototype (working, seeded, demo-ready). -->

- ✓ Capability Asset CRUD/list/detail with versions, approval events, usage tracking — existing
- ✓ Create-asset flow with Spring AI enrichment (local fallback when no LLM key) — existing
- ✓ Review queue with approve/reject/deprecate and backup-owner assignment — existing
- ✓ Ask Memory chat ranking live registry assets (AI Elements-compatible SSE stream) — existing
- ✓ Relational knowledge graph visualization (assets/owners/departments/tags) — existing
- ✓ Onboarding/offboarding knowledge-transfer surfaces, dashboard/analytics/settings shell — existing
- ✓ Monorepo architecture: `core` (Spring Modulith) + `apps/api|mcp|worker` + `web` (Vite/React/shadcn), PostgreSQL + pgvector + Flyway — existing

### Active

<!-- Direction: move from registry prototype to enterprise pilot foundation.
     Exact v1 scope to be decided after full-project research (user decision). -->

- [ ] Ingestion & governance spine: raw_source_object → normalized_record → knowledge_asset → capability_candidate, with staging boundary
- [ ] Source ACL snapshot at ingestion time + identity mapping design (source principals → OrgMemory users)
- [ ] Permission-aware retrieval: filter before keyword search, vector search, AI answers, MCP responses, export
- [ ] Real authentication/authorization (OIDC first) with admin/reviewer/contributor/viewer roles
- [ ] Production-grade immutable audit events (login, import, permission change, approval, use, export, MCP call, AI answer)
- [ ] Worker pipeline semantics: idempotency, retries, dedup, incremental watermark, job state
- [ ] At least one real import path (n8n workflow JSON and/or file upload; Airbyte staging when a connector fits)
- [ ] Permission-aware MCP tools (`search_capability_assets`, `get_capability_asset`, `create_asset_candidate`, `record_asset_usage`, `generate_handover_pack`)
- [ ] Pilot operations baseline: backup/restore, monitoring, runbook, OWASP ASVS + LLM Top 10 review

### Out of Scope

- Browser extension / passive capture — no privacy/incentive model yet; capture stays explicit
- Hand-built broad connector catalog — Airbyte is the data-movement layer when connectors fit
- Airflow — only if Airbyte + worker can't handle pipeline dependencies/backfills/SLA
- Neo4j / dedicated graph DB — relational graph visualization is sufficient for now
- Multi-agent orchestration, marketplace, full SSO (SAML/SCIM), billing — post-pilot
- Company-wide rollout / broad ingestion of real data — pilot must prove permissions, audit, and trust first

## Context

- Brownfield: working registry prototype (Spring Boot 4.1, Java 25, Spring Modulith 2.1, PostgreSQL + pgvector, Flyway; React 19, Vite, Tailwind v4, shadcn/ui). Docs in `docs/` are the product source of truth; `CLAUDE.md` carries engineering rules.
- Architecture was independently reviewed (this session): decisions rated sound (~85%); the three undesigned hard parts are (1) identity/ACL mapping, (2) worker pipeline semantics, (3) permission filtering mechanics with pgvector (filter-then-search vs search-then-filter). These must be designed before ingestion-spine migrations.
- Known code-level debts: seed/demo data mixed into Flyway migrations V4–V7 (blocks clean tenant deploys); graph derivation and AI normalization logic live in `apps/api` instead of `core`.
- Known gaps (docs/STATUS.md): no real auth, no ingestion tables, no Knowledge Asset surface, no permission-aware retrieval, no production audit table, search is keyword/ranker-based (not semantic), chat not persisted, MCP tools not implemented.
- User decision this session: do NOT stand up Airbyte until the staging schema and worker pipeline exist; validate the pipeline with a cheap importer (n8n JSON / file upload) first.

## Constraints

- **Tech stack**: Spring Boot 4.1 / Java 25 / Spring Modulith / PostgreSQL + pgvector / Flyway; React 19 / Vite / Tailwind v4 / shadcn — already established, do not churn
- **Architecture**: business logic in `core` first, exposed via `api`/`mcp`/`worker`; every JPA change pairs with a Flyway migration; `ddl-auto=validate`; API owns migrations
- **Security**: permission checks run BEFORE retrieval, vector search, AI answers, MCP responses, export; embeddings are not permission boundaries; no production claims until identity, ACLs, audit, retention, backup, monitoring, security review exist
- **Product**: approved assets must be reusable by non-creators; AI may create candidates but never auto-publishes; asset metadata must include the AI tool used; missing backup owners are first-class risk
- **Delivery**: app must boot without an LLM key; verification gates (compileJava, typecheck, tests, runtime check) before calling work done

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Four-layer memory model (RawSource → KnowledgeAsset → CapabilityCandidate → CapabilityAsset) | Encodes trust boundary in the data model; imported data is not automatically knowledge | — Pending |
| Airbyte for data movement, staging-only boundary | Don't rebuild connector catalog; OrgMemory owns governance after data lands | — Pending |
| Defer Airbyte until staging schema + worker pipeline exist | Airbyte output has no consumer yet; validate pipeline with cheap importer first | — Pending |
| Modular monolith (core + api/mcp/worker), Spring Modulith | One domain, one DB; avoid distributed-system cost at this scale | ✓ Good |
| Relational graph over Neo4j | Prototype traversal needs are met by PostgreSQL relations | ✓ Good |
| Design identity/ACL mapping + pipeline semantics + permission-filter mechanics BEFORE ingestion migrations | Schema of source_acl_snapshot and job tables depends directly on these decisions | — Pending |
| First GSD milestone scope decided after full-project research | User chose research-first; requirements/roadmap follow research findings | — Pending |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-07-06 after initialization*
