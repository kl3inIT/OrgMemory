# Project Research Summary

**Project:** OrgMemory — Enterprise Pilot Foundation (permission-aware RAG + AI capability governance, on-prem)
**Domain:** Enterprise AI Capability Registry / governed organizational AI memory
**Researched:** 2026-07-06
**Confidence:** MEDIUM-HIGH overall (stack HIGH; features/architecture/pitfalls MEDIUM, strongly cross-corroborated)

## Executive Summary

This milestone turns a working registry prototype into an enterprise pilot that can survive a customer security review. The research converges on one dominant conclusion: **the trust layer is the product**. Every credible comparable (Glean, Onyx EE, Microsoft Purview/Copilot) gates enterprise deals on the same set of features — SSO, permission-aware retrieval, source ACL sync, and immutable audit — and the industry-standard architecture for permission-aware RAG is uniform: snapshot source ACLs at ingestion, resolve them to denormalized ACL keys, and filter **inside the SQL query before retrieval**, never in application code afterward. OrgMemory's moat (capability-asset lifecycle, handover packs, ownership-risk governance) is real — no competitor combines permission-aware search with AI asset lifecycle — but every differentiator sits downstream of the boring spine: identity, audit, staging, ACL snapshots, filtered retrieval.

The recommended approach adds five new Spring Modulith modules (identity, ingestion, knowledge, retrieval, governance) to the existing monolith, with no new infrastructure: Keycloak as brokered OIDC IdP, Spring Security 7 resource server, Spring AI 2.0 (embeddings only — explicitly NOT its PgVectorStore, whose filter DSL cannot express ACL joins), pgvector 0.8.2 with iterative index scans, PostgreSQL full-text + RRF for hybrid search, and Spring Modulith's JDBC event publication registry as the transactional-outbox pipeline backbone. No Kafka, no Elasticsearch, no Spring Batch, no dedicated vector DB at pilot scale (20–100 users).

The key risks are all leak-shaped and all preventable at schema-design time: post-retrieval filtering (structural leak requiring a retrieval rewrite if discovered late), HNSW recall collapse under selective ACL filters (silent — fixed by iterative scans or exact filtered scans), derived-artifact leaks (summaries/graph/analytics tables without ACL columns), stale ACL snapshots (revoked access that keeps working), and fail-open identity mapping. PROJECT.md's three "undesigned hard parts" (identity/ACL mapping, pipeline semantics, permission-filter mechanics) each map directly to a critical pitfall and **all three must be design-complete before ingestion-spine migrations are written**, or the failures get baked into the schema.

## Key Findings

### Recommended Stack

Base stack (Boot 4.1 / Java 25 / Modulith 2.1 / pgvector / React 19) is established; these are additions only:

**Core technologies:**
- **Spring Security 7 OAuth2 Resource Server + Method Security**: generic-OIDC JWT auth; `@PreAuthorize` in `core` covers api/mcp/worker with one mechanism
- **Keycloak 26.6**: pilot IdP and identity *broker* — OrgMemory only ever speaks generic OIDC even against customer Entra ID/Okta
- **Spring AI 2.0.x GA**: hard requirement for Boot 4 (1.x is incompatible); use `EmbeddingModel` + local ONNX Transformers (preserves boots-without-LLM-key); do NOT use `PgVectorStore` for retrieval
- **pgvector 0.8.2 iterative index scans**: resolves the filter-then-search question — ACL join in WHERE, `hnsw.iterative_scan='strict_order'` fixes under-fetch
- **PG full-text + RRF**: hybrid search in one SQL query; no Elasticsearch at pilot scale
- **Spring Modulith Events (JDBC starter)**: built-in transactional outbox between ingestion stages; Flyway owns the `event_publication` DDL
- **Microsoft Presidio 2.2** (optional sidecar): on-prem PII detection; Java regex fallback behind a `PiiDetector` interface
- **`SELECT ... FOR UPDATE SKIP LOCKED`** job-claim loop in worker; JobRunr only as escape hatch

**What NOT to use:** Spring AI VectorStore abstraction, PG Row-Level Security as primary ACL mechanism, Kafka/RabbitMQ, Elasticsearch, Quartz, cloud DLP APIs, Spring AI 1.x, hand-built connector catalogs.

### Expected Features

**Must have (P1 — pilot fails security review or thesis without):**
- OIDC SSO + role model (admin/reviewer/contributor/viewer)
- Permission pre-filter in ONE shared retrieval service (keyword + vector + chat + graph + MCP + export)
- Source ACL snapshot at ingestion + identity mapping (source principals → internal users)
- Immutable audit log (append-only DB grants, covering login → import → approval → use → export → MCP → AI answer)
- Staging boundary (raw_source → normalized → knowledge_asset → capability_candidate)
- One real import path end-to-end (n8n JSON / file upload) with idempotent/retried/watermarked worker jobs
- Ask Memory citations resolvable only to permitted assets
- Admin surface (roles, import scope approval, job/source health, audit viewer, unresolved-identity queue)
- Retention/deletion with cascade to embeddings; manual classification labels honored by retrieval
- Permission-aware MCP tools (search/get) with per-call audit; ops baseline (backup/restore drill, monitoring, runbook)
- Seed data split out of Flyway V4–V7 (blocks clean tenant deploy)

**Differentiators (P2, during pilot):** Capability Candidate detection (AI proposes, human approves), handover packs, label-based deployment (Langfuse pattern), ownership-risk analytics, permission-aware knowledge graph, certification/staleness badges.

**Defer (P3/v2+):** Airbyte document source, PII auto-detection, answer provenance UI, SAML/SCIM, broad connectors, marketplace.

**Anti-features:** passive capture, auto-publishing AI-detected assets, embeddings as permission boundary, per-employee surveillance analytics, chat over raw staging, god-mode MCP service account.

### Architecture Approach

Industry-standard shape: **mirror source permissions into your own index at ingestion, filter locally at query time** (ACL snapshot sync — Glean/Onyx model). Five new Modulith modules with a strict communication rule: **Modulith events for pipeline state transitions** (RawObjectStaged → NormalizedRecordReady → KnowledgeAssetCreated → CandidateDetected), **direct calls for queries** (PermissionResolver, RetrievalService, AuditService). ACLs become denormalized string keys (`user_email:...`, `group:...`, `public`) resolved at query time via flattened group memberships (expanded at sync time, not query time). Audit writes are **synchronous, same-transaction** — never async listeners for security events. Staging is a separate PostgreSQL schema enforced by DB grants; Airbyte (deferred) writes staging only. Retrieval starts with **exact filtered scan** (correctness-first at pilot scale), upgrading to HNSW + iterative scan when chunk counts demand. Single-tenant per install; keep `organization_id` columns, build zero multi-tenant infrastructure.

### Critical Pitfalls

1. **Post-retrieval permission filtering** — structural leak (LLM context, rankings, existence hints); ACL predicate must live inside SQL; one authorization-scoped query path for every surface. Recovery cost if discovered late: a retrieval rewrite.
2. **HNSW recall collapse under selective filters** — silent zero-results for restricted users; teams "fix" it by weakening the filter. Prevent with iterative scans/exact scans + filtered-recall benchmarks at 1%/10%/50% selectivity.
3. **Derived-artifact leaks** — summaries, graph edges, analytics, exports without ACL/provenance columns. Columns are cheap at schema time, a migration nightmare later.
4. **Stale ACL snapshots** — revoked access keeps working; define a written staleness budget and build ACL re-sync as a first-class monitored job.
5. **Identity mapping fails open** — nested groups, guests, unmapped principals; fail closed with an admin unresolved-identity queue.
6. **Non-idempotent ingestion** — dedup keys + transactional watermarks + dead-letter table, designed before migrations.
7. **Indirect prompt injection (EchoLeak-class)** — retrieved content as data never instructions; no remote-image rendering in chat; structured MCP responses.
8. **Seed data in migrations V4–V7** — fix in the first phase; 10x cheaper before a customer deploy exists.

## Implications for Roadmap

Research produced an explicit dependency-driven build order (ARCHITECTURE.md "Build Order" + PITFALLS.md phase mapping agree):

### Phase 1: Foundation — Identity, Auth & Cleanup
**Rationale:** Everything downstream keys ACLs and audit actors to principals; retrofitting identity is the most expensive mistake available. Seed-data extraction is cheapest now.
**Delivers:** OIDC login (Keycloak + Spring Security 7), roles, principal/external_identity/group tables, seed data out of V4–V7, clean-tenant install verified.

### Phase 2: Governance — Immutable Audit
**Rationale:** Cheap now; every later feature wires audit in as built instead of retrofitted. Audit IS the product for this buyer.
**Delivers:** Append-only `audit_event` (DB grants + trigger + hash chain), sync `AuditService`, audit viewer basics.

### Phase 3: Ingestion Spine + First Importer
**Rationale:** Proves pipeline semantics (idempotency, watermarks, dead-letter, Modulith events) before any connector complexity. Requires the three "undesigned hard parts" to be design-complete FIRST (identity mapping schema, pipeline semantics, filter mechanics).
**Delivers:** Staging schema, raw_source_object/normalized_record, job/watermark tables, Modulith JDBC events, n8n JSON or file-upload importer end-to-end, double-run/crash-resume tests.

### Phase 4: ACL Snapshots + Knowledge Layer
**Rationale:** Depends on identity (keys) and ingestion (objects). Derived tables get ACL/provenance columns from day one (pitfall 3).
**Delivers:** source_acl_snapshot, identity mapping + unresolved queue, knowledge_asset + chunks + embeddings + denormalized ACL keys, ACL re-sync job with staleness budget, classification labels.

### Phase 5: Permission-Aware Retrieval
**Rationale:** The core promise; cannot exist without phases 1 and 4. Highest technical risk — needs the filtered-recall spike.
**Delivers:** Shared RetrievalService (hybrid tsvector + pgvector + RRF, ACL filter in both branches), Ask Memory rewire with permitted-only citations, canary-leak tests across every surface, injection hardening.

### Phase 6: MCP Tools, Admin Surface & Pilot Hardening
**Rationale:** MCP reuses the retrieval service (never a copy); ops and admin gates close the security-review checklist. Airbyte lands only after the staging consumer works.
**Delivers:** Permission-aware MCP tools with API-key service principals + per-call audit, admin surface completion, retention/deletion cascade, backup/restore drill, ASVS/LLM-Top-10 review, runbooks. Differentiators (candidate detection, handover packs, labels, ownership analytics) slot in as P2 items during/after this phase.

### Research Flags

**Needs phase-level research (`--research-phase`):**
- **Phase 4:** identity mapping schema (custom, no library exists; PROJECT.md pre-migration design task)
- **Phase 5:** ACL-filtered HNSW recall/latency spike at realistic selectivity; embedding model benchmark (MiniLM vs bge/gte); store model id + dimension per row regardless

**Standard patterns, skip research:** Phases 1–2 (OIDC + append-only audit are thoroughly documented), Phase 3 (watermark/dedup/outbox patterns well established), Phase 6 (checklist-driven).

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Versions verified against official docs/Context7 (Spring AI 2.0 Boot-4 hard requirement, pgvector 0.8.2, Keycloak 26.6); mcp-security and JobRunr Boot-4 compat are MEDIUM |
| Features | MEDIUM | Cross-checked vendor docs (Glean, Onyx, Purview, Langfuse); strong consensus on table stakes; moat claim corroborated by absence in comp set |
| Architecture | MEDIUM | Modulith mechanics source-verified; ACL-key/RRF/outbox patterns corroborated across multiple independent sources; Onyx EE internals LOW |
| Pitfalls | MEDIUM | Cross-verified incl. CVE write-ups (EchoLeak); pgvector filter behavior consistent across sources |

**Overall: MEDIUM-HIGH.** The direction is unambiguous; remaining uncertainty is quantitative (recall/latency numbers), not directional.

### Gaps to Address

- **Filtered-recall benchmark numbers** — no substitute for a spike with realistic ACL selectivity (Phase 5)
- **Identity mapping schema** — custom design work; must precede ingestion migrations (Phase 3/4 gate)
- **Embedding model choice** for on-prem quality — benchmark during retrieval phase; schema supports coexisting models
- **mcp-security maturity** — keep API keys as pilot mechanism; re-evaluate OAuth2-for-MCP later
- **Per-chunk vs per-document ACL granularity** — decide after the recall spike

## Sources Summary

- Official docs via Context7 (HIGH): Spring Security 7, Spring AI 2.0, Spring Modulith, pgvector changelog
- Official vendor releases (HIGH): spring.io blog (Spring AI 2.0 GA), keycloak.org, Presidio releases
- Vendor product docs (MEDIUM): Glean, Onyx, Langfuse, Collibra, Microsoft Purview/Learn, Airbyte, modelcontextprotocol.io
- Cross-checked practitioner sources (MEDIUM): permission-aware RAG consensus (Oso, Paragon, Databricks, RheinInsights), hybrid search/RRF (Supabase, ParadeDB, jkatz), audit hash-chaining (PostgreSQL wiki), EchoLeak CVE-2025-32711 write-ups
- Project-internal (HIGH): PROJECT.md, ENTERPRISE_READINESS.md, PRODUCT_BRIEF.md

Full source lists in the individual research files.

---
*Synthesized from STACK.md, FEATURES.md, ARCHITECTURE.md, PITFALLS.md*
*Research completed: 2026-07-06*
