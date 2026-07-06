# Stack Research

**Domain:** Enterprise pilot foundation for an on-prem AI Capability Registry — OIDC auth, permission-aware RAG on pgvector, ingestion pipeline, immutable audit
**Researched:** 2026-07-06
**Confidence:** HIGH (Spring/pgvector versions verified against official docs), MEDIUM (pattern-level web findings)

**Scope note:** This is a *subsequent-milestone* stack research. The base stack (Spring Boot 4.1 / Java 25 / Spring Modulith 2.1 / PostgreSQL + pgvector / Flyway / React 19 / Vite / Tailwind v4 / shadcn) is established and per PROJECT.md must not churn. Everything below is what to ADD for the enterprise pilot foundation.

## Recommended Stack

### Core Technologies

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Spring Security OAuth2 Resource Server | 7.x (Boot 4.1 BOM-managed) | API authenticates via JWT from any OIDC issuer | Boot 4 pairs with Spring Security 7. `oauth2ResourceServer(jwt)` + `JwtDecoders.fromIssuerLocation(...)` gives generic-OIDC support so the pilot works against Keycloak today and a customer's Entra ID/Okta tomorrow with only config changes. Confidence: HIGH (Spring Security 7.0 reference docs) |
| Spring Security Method Security | 7.x | Role enforcement (`admin`/`reviewer`/`contributor`/`viewer`) | `@EnableMethodSecurity` + `@PreAuthorize` on `core` service methods puts authorization in the domain layer, not controllers — matches "business logic in core first" and covers API, MCP, and worker paths with one mechanism. Not enabled by default; must be turned on explicitly. Confidence: HIGH |
| Keycloak | 26.6.x | Pilot/dev OIDC identity provider + broker | Current release (26.6.0, Apr 2026). The standard on-prem OSS IdP. Critically, it is also an identity *broker*: put Keycloak in front of the customer's Entra ID/Okta so OrgMemory only ever speaks generic OIDC. 26.6 adds IdP mappers that assign federated users to organization groups from external claims — directly useful for department/role mapping. Confidence: HIGH (keycloak.org release notes) |
| Spring AI | 2.0.x (GA 2026-05-28) | Embeddings, chat, RAG plumbing, MCP server | Spring AI 2.0 is the line built for Boot 4.0/4.1 + Framework 7 + Jackson 3; 1.1.x stays on Boot 3.5. If the repo is on a 1.x/2.0-milestone, pin to 2.0.x GA this milestone. Confidence: HIGH (spring.io blog "Spring AI 2.0.0 GA Available Now", 2026-06-12) |
| Spring AI MCP Server starter | 2.0.x (`spring-ai-starter-mcp-server-webmvc`) | Permission-aware MCP tools in `apps/mcp` | `@McpTool`-annotated methods with generated schemas; the MCP scaffold already exists in the repo. Add `spring-ai-community/mcp-security` for OAuth2 on the MCP transport when moving past API keys. Confidence: HIGH for starter, MEDIUM for mcp-security (community project) |
| pgvector | 0.8.2 (latest; 0.9 does not exist) | Vector similarity with ACL pre-filtering | 0.8.x has iterative index scans (`SET hnsw.iterative_scan = 'strict_order'` / `'relaxed_order'`), which is *the* feature that makes filtered HNSW correct: without it, a restrictive ACL filter silently returns fewer than LIMIT rows. This resolves PROJECT.md's open "filter-then-search vs search-then-filter" question: filter in SQL, let iterative scan fix under-fetch. Confidence: HIGH (pgvector changelog) |
| PostgreSQL full-text search (built-in) | PG 17/18 | Keyword half of hybrid search | `tsvector` + GIN index + `websearch_to_tsquery`, fused with the vector ranking via Reciprocal Rank Fusion (`1/(60+rank)`) in one SQL query. No Elasticsearch needed at 20–100 pilot users. Confidence: MEDIUM (multiple independent practitioner sources; consistent pattern) |
| Spring Modulith Events (JDBC) | 2.1 (already in repo; add `spring-modulith-starter-jdbc`) | Transactional outbox between ingestion stages | The Event Publication Registry persists events in an `event_publication` table in the same transaction as the state change; `@ApplicationModuleListener` handlers run async in their own transaction; incomplete publications are resubmittable (`republish-outstanding-events-on-restart`). This is a built-in outbox — no Kafka, no new infra, aligned with the modular monolith. Confidence: HIGH (Spring Modulith docs/examples) |
| Microsoft Presidio | 2.2.x (2.2.362, Mar 2026) | On-prem PII/sensitive-data detection in the ingestion worker | The de facto OSS standard for self-hosted PII detection: MIT, actively released, ships as Docker REST services (`presidio-analyzer`/`presidio-anonymizer`) the Java worker calls over HTTP. Custom recognizers cover org-specific patterns (API keys, internal IDs). GLiNER plugs in as a recognizer if NER quality needs a boost. Confidence: MEDIUM-HIGH (official repo + releases verified) |

### Supporting Libraries

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `oidc-client-ts` + `react-oidc-context` | latest | SPA OIDC login (Authorization Code + PKCE) | Web app authenticates as a public PKCE client against Keycloak; API stays a pure resource server. Standard SPA pattern; a BFF is the post-pilot hardening path, not a pilot requirement |
| `spring-boot-starter-oauth2-client` | Boot 4.1 BOM | Client-credentials tokens | Only when worker/MCP need to call the API as a service identity via OAuth2 instead of API keys |
| Spring AI `TransformersEmbeddingModel` (`spring-ai-starter-model-transformers`) | 2.0.x | Local ONNX embeddings (e.g., all-MiniLM-L6-v2, 384-dim) | Preserves the "boots without an LLM key" constraint and works fully on-prem. Use OpenAI-compatible config (vLLM/Ollama endpoint) when the customer provides a model server; record `embedding_model` + dimension per row so models can coexist |
| PostgreSQL `SELECT ... FOR UPDATE SKIP LOCKED` (no library) | built-in | Worker job-claim loop | Ingestion job state (idempotency keys, watermarks, retry counts, dedup hashes) is *domain data* per the four-layer model — own it in Flyway-managed tables; a `@Scheduled` poller claims jobs with SKIP LOCKED. Cluster-safe if a second worker ever runs |
| JobRunr | 8.x | Escape hatch for job execution | Only if the custom job loop grows painful (need dashboard, complex backoff, high job volume). Auto-retry with backoff + dashboard + Postgres storage; supports virtual threads. Verify Boot 4/Jackson 3 compatibility before adopting |
| Flyway (already present) | Boot 4.1 BOM | Audit table hardening | The audit migration itself issues `REVOKE UPDATE, DELETE` and creates the block-mutation trigger — immutability is schema, not convention |

### Development Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| Keycloak in `docker-compose` | Local/dev/pilot IdP | Realm export JSON checked into repo: realm, 4 roles, test users per role; `spring.security.oauth2.resourceserver.jwt.issuer-uri` points at it |
| Presidio analyzer container in `docker-compose` | Local PII detection | Optional profile; worker must degrade gracefully (flag "PII scan skipped") when absent, mirroring the LLM-key fallback rule |
| Spring Security `spring-security-test` | Auth testing | `jwt()` request post-processors to test role enforcement per endpoint without a live IdP |

## Key Architecture-Level Choices (stack-relevant)

**1. Do NOT use Spring AI's `PgVectorStore` for permission-aware retrieval.** The autoconfigured `VectorStore` writes a generic `vector_store` table with JSON metadata and its filter DSL cannot express joins against `source_acl_snapshot`/visibility tables. Permission filtering must be a SQL JOIN executed *before* ranking. Use Spring AI only for `EmbeddingModel`; run retrieval through your own Flyway-managed tables via `JdbcTemplate`/JPA with a hand-written hybrid query. Confidence: HIGH (PgVectorStore schema verified in 2.0.0 API docs).

**2. Pre-filter ACLs, never post-filter.** Consensus enterprise-RAG pattern: attach permission metadata at ingestion (ACL snapshot), enforce with pre-retrieval filtering inside the retrieval query. Post-filtering leaks under app bugs and prompt injection and wastes the candidate pool. With pgvector: ACL join in WHERE + `hnsw.iterative_scan='strict_order'` (set per-transaction) so the index keeps scanning until LIMIT authorized rows are found. Confidence: MEDIUM (consistent across many sources; validate recall/latency in a spike with realistic ACL selectivity).

**3. Ingestion pipeline = Modulith events + own job tables, not Spring Batch.** Spring Batch 6 (the Boot 4-compatible line) earns its complexity for large restartable chunked ETL; pilot ingestion (2–3 sources, staged normalization with per-object idempotency) is better served by: staging tables → `@Scheduled` claim loop (SKIP LOCKED) → Modulith events between stages (`RawSourceObjectRegistered` → normalize → `NormalizedRecordReady` → …), with the event publication registry providing at-least-once redelivery. Handlers must be idempotent (dedup on content hash + source id). Revisit Batch only for large backfills. Confidence: MEDIUM-HIGH.

**4. Immutable audit = plain Postgres, hardened.** Append-only `audit_event` table; app role gets INSERT/SELECT only (`REVOKE UPDATE, DELETE`); `BEFORE UPDATE OR DELETE` trigger raises exception (defends against injection and admin-tool accidents); hash chain column (`hash = HMAC(prev_hash || canonical_payload)`) for tamper evidence; optionally anchor the chain head externally later. Append-only without role separation is not tamper-evident — document that the DB superuser remains trusted for the pilot. No extra technology needed. Confidence: MEDIUM (well-documented pattern, incl. PostgreSQL wiki).

**5. Service identity for MCP/API: API keys first, OAuth2 later.** Roadmap Month 4 says "API keys/service auth". Ship hashed API keys (own table, per-key principal + role + audit trail) for MCP agents in the pilot; adopt `mcp-security` (OAuth2 for MCP) when agent clients support it. Every MCP tool call resolves to an internal user/service principal and runs through the same method-security checks. Confidence: MEDIUM.

**6. Identity mapping (source principals → internal users).** No off-the-shelf library exists; this is schema design: `identity_mapping(source_system, source_principal_id, app_user_id, mapping_status, confidence)` populated at ACL-snapshot time, with unmatched principals quarantined (asset stays invisible rather than defaulting open). Keycloak brokering reduces the problem for login identity; source-system ACL principals (Slack user IDs, Drive emails) still need this table. Confidence: HIGH that it's custom; design work flagged for phase research.

## Installation

```kotlin
// core/build.gradle.kts (versions come from Boot 4.1 + Spring AI 2.0 BOMs)
implementation("org.springframework.boot:spring-boot-starter-security")
implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
implementation("org.springframework.modulith:spring-modulith-starter-jdbc") // event_publication outbox
implementation("org.springframework.ai:spring-ai-starter-model-transformers") // local ONNX embeddings

// apps/mcp
implementation("org.springframework.ai:spring-ai-starter-mcp-server-webmvc")

// BOM
implementation(platform("org.springframework.ai:spring-ai-bom:2.0.0"))
```

```bash
# web
pnpm -C web add oidc-client-ts react-oidc-context
```

```yaml
# docker-compose additions (pilot profile)
# keycloak: quay.io/keycloak/keycloak:26.6  (start-dev + realm import for local)
# presidio: mcr.microsoft.com/presidio-analyzer  (optional profile)
```

```sql
-- retrieval session setting (per query/transaction)
SET LOCAL hnsw.iterative_scan = 'strict_order';
```

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|-------------------------|
| Keycloak (pilot IdP + broker) | Direct OIDC against customer Entra ID/Okta | If the design partner refuses an extra service; the resource-server config already supports any issuer — Keycloak is a default, not a dependency |
| SPA PKCE + resource server | BFF (token handler / Spring Cloud Gateway) | Post-pilot security hardening, or if the security review rejects tokens in the browser |
| Modulith events + SKIP LOCKED job tables | JobRunr 8.x | Job volume/observability outgrows the custom loop; JobRunr adds dashboard + managed retries on the same Postgres |
| Modulith events + job tables | Spring Batch 6 | Large restartable backfills/reprocessing of historical corpora (post-pilot) |
| Custom hybrid SQL (tsvector + pgvector + RRF) | ParadeDB `pg_search` (BM25) | If PG-native `ts_rank` quality proves insufficient; BM25-in-Postgres is a drop-in upgrade path that avoids Elasticsearch |
| Presidio sidecar | Java-only regex/deterministic recognizers | Acceptable v0 inside the worker (emails, credentials, key patterns) if the pilot infra team blocks a Python container at first; keep the detector behind an interface so Presidio slots in |
| Local ONNX embeddings (Transformers) | Customer-hosted OpenAI-compatible endpoint (vLLM/Ollama) | Prefer customer endpoint when available (better models); local ONNX is the guaranteed-offline floor |

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| Spring AI `PgVectorStore` / `VectorStore` abstraction for retrieval | Generic schema + metadata-filter DSL cannot express ACL joins; permission checks must be SQL pre-filters | Own tables + `EmbeddingModel` + hand-written hybrid query |
| Airflow | Project rule; Airbyte + worker covers pilot orchestration | Modulith events + worker job tables |
| Neo4j / graph DB | Project rule; relational graph is sufficient | PostgreSQL relations |
| Hand-built connector catalog | Project rule; connectors are Airbyte's job — and Airbyte is deferred until the staging schema + worker pipeline exist (user decision) | n8n JSON / file-upload importer first, Airbyte when a connector fits |
| Full SSO (SAML) / SCIM provisioning | Project rule (post-pilot); OIDC covers pilot login | Generic OIDC + Keycloak brokering |
| Kafka / RabbitMQ for the pipeline | New infra for a modular monolith with one DB; Modulith's JDBC registry gives transactional at-least-once delivery already | `spring-modulith-starter-jdbc` |
| Elasticsearch / OpenSearch | Second search infra to secure/back up on-prem; PG FTS + pgvector handles pilot scale | Hybrid SQL with RRF |
| Quartz | Legacy ergonomics: manual retries, manual thread mgmt; both modern options beat it | SKIP LOCKED loop, or JobRunr/db-scheduler |
| Cloud DLP (Google DLP, AWS Macie, Azure Purview APIs) | Violates on-prem constraint — sends customer content to cloud APIs | Presidio (self-hosted) |
| Spring AI 1.x line | Not compatible with Boot 4 (1.1.x targets Boot 3.5); mixing causes classpath/Jackson-3 breakage | Spring AI 2.0.x GA |
| Row-Level Security (PG RLS) as the primary ACL mechanism | App connects as one DB role; per-request `SET ROLE`/session-var RLS is fragile with connection pools and hides logic from the domain layer | Explicit ACL joins in `core` retrieval queries (auditable, testable); RLS optionally later as defense-in-depth |

## Stack Patterns by Variant

**If the design partner is Microsoft-first (Entra ID):**
- Broker Entra ID through Keycloak (OIDC brokering) rather than integrating Entra directly, so role/department mapping logic stays in one place.

**If the pilot environment cannot run Python containers:**
- Ship the Java deterministic PII recognizer set (regex + checksum validators) behind a `PiiDetector` interface; add the Presidio sidecar when infra approves.

**If retrieval latency with ACL filters degrades (highly selective permissions):**
- First try `hnsw.iterative_scan='relaxed_order'` + raised `hnsw.max_scan_tuples`; then partial indexes per visibility tier; only then consider pgvectorscale. Do not reach for a dedicated vector DB.

**If a second worker instance is needed:**
- The SKIP LOCKED claim pattern is already cluster-safe; add instance heartbeats to the job table before considering JobRunr.

## Version Compatibility

| Package | Compatible With | Notes |
|---------|-----------------|-------|
| Spring Boot 4.1.x | Spring Security 7.x, Spring Framework 7, Jackson 3 | Boot 4 dropped Jackson 2 — audit any third-party lib (JobRunr, etc.) for Jackson 3 support before adding |
| Spring AI 2.0.0 GA | Spring Boot 4.0/4.1 ONLY | Hard requirement; 2.0 cannot load in a Boot 3.x context and 1.1.x cannot load in Boot 4 (verified: spring.io blog + GitHub discussion #5149) |
| Spring Modulith 2.1 | Spring Boot 4.x | Already in repo; JDBC events starter adds `event_publication` table — pair with a Flyway migration (`ddl-auto=validate` means Modulith must not create it itself: set `spring.modulith.events.jdbc.schema-initialization.enabled=false` and own the DDL) |
| Spring Batch 6.0.x | Spring Boot 4.x | Only relevant if backfills demand it later |
| pgvector 0.8.2 | PostgreSQL 13–18 | Iterative scans need ≥0.8.0; confirm the pilot's Postgres image bundles ≥0.8.0 (e.g., pgvector/pgvector:pg17) |
| Keycloak 26.6.x | Any OIDC RP | Runs on-prem via container; no Spring version coupling |
| Presidio 2.2.x | HTTP from any JVM | Python sidecar; no JVM dependency coupling |
| Java 25 | All of the above | Spring AI 2.0 needs Java 17+ (21 recommended); 25 fine |

## Gaps / Flags for Phase Research

- **ACL-filtered HNSW recall/latency at realistic selectivity** — needs a spike with production-like ACL distribution before committing to per-chunk vs per-document ACL granularity (Phase: permission-aware retrieval).
- **Identity mapping schema** — custom design, no library; PROJECT.md already flags it as a pre-migration design task.
- **JobRunr/db-scheduler Boot 4 (Jackson 3) compatibility** — verify at adoption time; not needed for the recommended path.
- **mcp-security maturity** — community project; treat as MEDIUM confidence and keep API keys as the pilot mechanism.
- **Embedding model choice for on-prem quality** (MiniLM vs bge/gte class models via ONNX or vLLM) — benchmark during the retrieval phase; schema should store model id + dimensions per embedding row to allow migration.

## Sources

- `/websites/spring_io_spring-security_reference_7_0` (Context7) — OIDC login, JWT resource server, method security — HIGH
- `/websites/spring_io_spring-ai_2_0_0` (Context7) — PgVectorStore schema/builder, `@McpTool`, Transformers ONNX embedding properties — HIGH
- `/spring-projects/spring-modulith` (Context7) — event publication registry, `@ApplicationModuleListener`, outbox example, republication — HIGH
- `/pgvector/pgvector` (Context7) — changelog (0.8.2 latest, 2026-02-25), iterative scans, partial HNSW indexes, `hnsw.ef_search` — HIGH
- spring.io blog 2026-06-12 "Spring AI 2.0.0 GA Available Now"; GitHub spring-ai discussion #5149 — Boot 4 hard requirement — HIGH
- keycloak.org release posts (26.5.0 Jan 2026, 26.6.0 Apr 2026) — versions and brokering features — HIGH
- github.com/microsoft/presidio + releases (2.2.362) — on-prem PII detection, GLiNER recognizer — MEDIUM-HIGH
- ParadeDB "Hybrid Search in PostgreSQL: The Missing Manual"; dev.to/lpossamai RRF walkthrough; AlloyDB hybrid search docs — hybrid SQL + RRF pattern — MEDIUM
- Cerbos, TianPan.co (2026-05), Databricks community — ACL pre-filtering consensus for enterprise RAG — MEDIUM
- PostgreSQL wiki (Audit trigger), Tracehold/AppMaster hash-chain writeups — immutable audit pattern — MEDIUM
- jobrunr.io comparison docs + practitioner comparisons — job scheduler landscape — MEDIUM (vendor-authored source for JobRunr claims)

---
*Stack research for: OrgMemory enterprise pilot foundation (auth, permission-aware RAG, ingestion, audit)*
*Researched: 2026-07-06*
