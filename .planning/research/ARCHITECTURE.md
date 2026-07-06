# Architecture Research

**Domain:** Permission-aware enterprise RAG + AI capability governance, integrated into an existing Spring Modulith monolith (on-prem, single-tenant pilot)
**Researched:** 2026-07-06
**Confidence:** MEDIUM (Spring Modulith mechanics verified against source via Context7 — MEDIUM per seam; ecosystem patterns cross-checked across multiple independent sources — MEDIUM; single-source claims marked LOW inline)

**Milestone framing:** This is a *subsequent* milestone. The existing system (core Spring Modulith domain + apps/api|mcp|worker + React web + PostgreSQL/pgvector/Flyway) is not redesigned. This document specifies how the five new component groups — **identity, ingestion, knowledge, governance, retrieval** — integrate with what exists.

## Standard Architecture

### How the industry structures this

Permission-aware enterprise RAG products (Glean, Onyx/Danswer EE, Azure AI Search) converge on the same shape: **mirror source permissions into your own index at ingestion time, filter locally at query time**. Nobody calls Slack/Google/SharePoint APIs on the retrieval hot path — latency and rate limits make that a non-starter (Oso, Onyx docs — MEDIUM confidence).

Three canonical approaches to source permissions:

| Approach | How | Verdict for OrgMemory |
|----------|-----|----------------------|
| Query-time API filtering | Call source API per result to authorize | Reject — on the critical path, rate-limited, breaks when source is unreachable (on-prem reality) |
| **ACL snapshot sync** | Copy resolved ACLs alongside content at ingest; filter locally | **Adopt** — matches the already-planned `source_acl_snapshot`; industry standard (Glean, Onyx) |
| Permission-logic mirroring | Re-implement source authorization rules over synced metadata (policy engine) | Defer — only needed when a source's ACL API is incomplete (e.g., Notion); revisit per-connector |

The accepted cost of ACL snapshots is **staleness**: a permission revoked in Google Drive stays effective in OrgMemory until the next ACL re-sync. Enterprise products handle this by (a) making the staleness window an explicit, documented SLA (hours, not days), (b) running ACL-only re-sync jobs more frequently than content sync, and (c) deny-by-default for anything unmapped.

### System Overview

```
                    ┌──────────────────────────────────────────────────────┐
                    │  web (existing)          apps/api (existing)          │
                    │  + knowledge browse UI   + auth filter (OIDC)         │
                    │  + ingestion admin UI    + knowledge/ingestion ctrl   │
                    └───────────────┬──────────────────────────────────────┘
                                    │ REST (principal resolved per request)
┌───────────────────────────────────▼───────────────────────────────────────┐
│  core (Spring Modulith) — existing modules: organization, capability      │
│                                                                           │
│  NEW MODULES                                                              │
│  ┌────────────┐   ┌────────────┐   ┌────────────┐   ┌─────────────────┐  │
│  │  identity   │   │ ingestion  │   │ knowledge  │   │   retrieval     │  │
│  │ principals  │   │ staging    │──▶│ knowledge_ │◀──│ hybrid search   │  │
│  │ ext. ids    │   │ jobs       │evt│ asset      │   │ ACL pre-filter  │  │
│  │ groups      │   │ watermarks │   │ chunks +   │   │ RRF + citations │  │
│  │ ACL resolve │◀──│ acl snap   │   │ embeddings │   │                 │  │
│  └────────────┘   └────────────┘   └─────┬──────┘   └─────────────────┘  │
│        ▲                                  │ evt (candidate detected)      │
│        │ direct call (sync authz)         ▼                               │
│  ┌─────┴──────────────────────┐   ┌────────────────────┐                  │
│  │  governance (audit)        │   │ capability (exists) │                  │
│  │ append-only audit_event    │   │ candidate → review  │                  │
│  │ sync write, same txn       │   │ → approved asset    │                  │
│  └────────────────────────────┘   └────────────────────┘                  │
└───────────────────────────────────┬───────────────────────────────────────┘
                                    │
        ┌───────────────────────────▼────────────────────────────┐
        │ PostgreSQL + pgvector                                   │
        │ ┌────────────────┐ ┌──────────────┐ ┌───────────────┐  │
        │ │ staging schema  │ │ domain schema │ │ event_        │  │
        │ │ (Airbyte final  │ │ (trusted      │ │ publication   │  │
        │ │ tables land     │ │ memory)       │ │ (Modulith     │  │
        │ │ here — TRUST    │ │               │ │ outbox)       │  │
        │ │ BOUNDARY)       │ │               │ │               │  │
        │ └────────────────┘ └──────────────┘ └───────────────┘  │
        └─────────────────────────────────────────────────────────┘
              ▲
              │ writes staging ONLY (never domain tables)
        ┌─────┴──────┐          ┌──────────────────────────────┐
        │  Airbyte    │          │ apps/worker (existing shell)  │
        │ (external,  │          │ runs ingestion pipeline jobs, │
        │ deferred    │          │ embedding jobs, ACL re-sync   │
        │ until spine │          │ (scheduled + event-driven)    │
        │ exists)     │          └──────────────────────────────┘
        └────────────┘
```

### Component Responsibilities

| Component | Responsibility | Typical Implementation |
|-----------|----------------|------------------------|
| `identity` module (new) | Internal principals (users, groups), external identity mapping (source user/group → internal principal), effective-permission resolution ("what ACL keys does user U hold?") | JPA entities + a `PermissionResolver` service; OIDC claims populate/link `app_users` |
| `ingestion` module (new) | Staging importers, `raw_source_object`, `normalized_record`, job/watermark state, ACL snapshot capture at ingest time | Custom job tables + Modulith events for stage handoff; runs in `apps/worker` |
| `knowledge` module (new) | `knowledge_asset`, chunks, embeddings, provenance/citations, dedup | JPA + pgvector column on chunk table; embedding job in worker |
| `retrieval` module (new) | Permission-filtered hybrid search (tsvector + pgvector + RRF), candidate ranking for Ask Memory and MCP | Single-SQL hybrid query via `JdbcClient`/native query; called synchronously by api/mcp |
| `governance` module (new) | Append-only `audit_event`, retention, review policy hooks | Direct-call `AuditService` (sync, same transaction); DB-level append-only enforcement |
| `capability` module (existing) | Capability assets, versions, approval, usage — unchanged; gains `capability_candidate` intake from knowledge | Existing services; candidate promotion consumes knowledge events |
| `organization` module (existing) | Org/department/user shell — extended, not replaced; `AppUser` becomes the internal principal anchor | Existing JPA + OIDC subject linkage |
| Airbyte (external) | OAuth, pagination, rate limits, incremental sync for enterprise sources | `abctl` deploy; writes **staging schema only**; deferred until pipeline consumes a cheap importer first (per PROJECT.md decision) |

## Recommended Project Structure

Additions only — existing layout is kept:

```
core/src/main/java/com/orgmemory/core/
├── organization/            # existing (extend AppUser with oidc_subject, status)
├── capability/              # existing (add CapabilityCandidate intake)
├── identity/                # NEW: Principal, ExternalIdentity, PrincipalGroup,
│   │                        #      GroupMembership, PermissionResolver
│   └── internal/            # resolution + group expansion logic (not exported)
├── ingestion/               # NEW: RawSourceObject, NormalizedRecord,
│   │                        #      SourceConnection, IngestionJobRun,
│   │                        #      SourceAclSnapshot, importers SPI
│   └── internal/            # importer implementations, watermark logic
├── knowledge/               # NEW: KnowledgeAsset, KnowledgeChunk (+embedding),
│   └── internal/            #      chunking, dedup, citation records
├── retrieval/               # NEW: RetrievalService (hybrid SQL), RetrievalResult
│   └── internal/
└── governance/              # NEW: AuditService, AuditEvent, retention config
    └── internal/

core/src/main/resources/db/migration/   # V8+: identity, audit, staging, knowledge
                                        # (also: extract seed data V4–V7 out of
                                        #  migrations — blocks clean tenant deploys)
```

### Structure Rationale

- **Five new Modulith modules, not one "enterprise" module:** each has a distinct lifecycle and consumer set. `identity` and `governance` are consumed by everything (direct calls); `ingestion → knowledge → capability` is a pipeline (events). Verify boundaries with the existing Modulith `ApplicationModules.verify()` test.
- **`retrieval` separate from `knowledge`:** retrieval reads knowledge + capability + identity; putting it inside `knowledge` would force `knowledge → capability` dependencies that pollute the pipeline direction.
- **Staging as a separate PostgreSQL schema** (`staging`), not separate DB: keeps one operational unit for on-prem backup/restore, while making the trust boundary explicit and grant-controllable (worker role writes staging; api role need not).

## Architectural Patterns

### Pattern 1: ACL Snapshot with Denormalized ACL Keys (identity/permission model)

**What:** At ingestion time, capture the source object's permissions and resolve them into a flat set of **ACL keys** stored with the object. An ACL key is a string like `user_email:alice@corp.com`, `group:google_drive:eng-team@corp.com`, `slack_channel_member:C012345`, `public`. At query time, resolve the requesting user's held keys (their email identities + expanded group memberships) and filter `WHERE acl_key IN (user's keys)`.

This is Onyx's model (its document index stores an `access_control_list` of such keys per document — LOW confidence on exact internals, docs are EE-gated; the pattern itself is corroborated across Glean/Oso/Paragon writeups — MEDIUM).

**Key design decisions:**
- **Group expansion strategy — expand at sync time, not query time.** A scheduled *group sync* job pulls source group membership (Google groups, Slack channel members, SharePoint/Entra groups incl. nested) and flattens nested groups into direct memberships in `principal_group_membership`. Query-time resolution is then a single indexed join, no recursion. Nested-group flattening is recomputed each group sync.
- **Two sync cadences:** doc-level ACL snapshot happens with every content sync (per object); group membership sync is a separate, more frequent job (it changes independently of content). This is the standard split (Onyx uses "permission sync" jobs distinct from content indexing — MEDIUM).
- **Unmapped-user policy — deny with pending queue.** If a source principal (e.g., `bob@contractor.com` on a Drive doc) has no matching internal principal: the ACL key is still stored, it simply matches no one → deny by default, no special casing. The *reverse* case (an OrgMemory user with no external identity link) gets only `public` and directly-granted assets. An `unmapped_principal` report table surfaces both for admin resolution — this is a pilot admin UI feature, not a blocker.
- **Staleness policy:** record `acl_synced_at` per object; expose it in citations/admin UI; document the pilot SLA (e.g., "revocations propagate within N hours"). Never serve objects whose ACL snapshot is older than a configurable hard limit without flagging.

**When to use:** always, for every ingested object — even manual uploads get an ACL snapshot (`owner + visibility` derived keys), so retrieval has exactly one filtering mechanism.

**Trade-offs:** staleness window (accepted industry-wide); group flattening can be large for big orgs (fine at pilot scale of 20–100 users); requires per-connector ACL fetch code, which is the part Airbyte does NOT do for you (Airbyte moves content/metadata; ACL endpoints often need separate API calls — plan connector-specific ACL fetchers in the worker).

**Example (schema sketch):**
```sql
-- identity module
principal(id, kind user|group, org_id, display_name)
external_identity(id, principal_id, source_system, external_id, external_email)
principal_group_membership(group_principal_id, member_principal_id)  -- flattened

-- ingestion module
source_acl_snapshot(id, raw_source_object_id, acl_key text, permission read|write,
                    captured_at)
-- knowledge module carries forward:
knowledge_asset_acl(knowledge_asset_id, acl_key)   -- denormalized at promotion
```

### Pattern 2: Modulith Event Publication Registry as the Pipeline Backbone (ingestion semantics)

**What:** Use Spring Modulith's Event Publication Registry — a built-in transactional outbox — to drive stage transitions: `RawObjectStaged → NormalizedRecordReady → KnowledgeAssetCreated → CapabilityCandidateDetected`. `@ApplicationModuleListener` is `@Async + @Transactional(REQUIRES_NEW) + @TransactionalEventListener`; publications are persisted in the `event_publication` table within the publishing transaction and marked completed/failed after the listener runs. `spring.modulith.events.republish-outstanding-events-on-restart=true` re-drives incomplete work after a crash. (Verified against Spring Modulith source via Context7 — MEDIUM per confidence seam.)

**Recommendation: Modulith events + custom job tables. Not Spring Batch, not a broker, not Airflow.**

- **Spring Batch** earns its complexity for chunked processing of huge files with restart-from-chunk semantics. The pilot ingests API-shaped incremental data (hundreds–thousands of objects per sync), where per-object idempotency makes chunk-restart machinery redundant. Revisit only for a massive one-time backfill.
- **Modulith events alone are not enough** — they give at-least-once delivery, but you still own pipeline *state*. Add:
  - `ingestion_job_run(id, source_connection_id, kind content|acl|group_sync, status, watermark_before, watermark_after, stats jsonb, error text, started_at, finished_at)` — one row per sync run.
  - **Idempotency key** on `raw_source_object`: unique `(source_connection_id, external_id, content_hash)`. Re-delivered events and overlapping watermark windows become no-ops (`ON CONFLICT DO NOTHING` / version bump when hash differs).
  - **Incremental watermark** per source connection: cursor = source-native updated-at or Airbyte's `_airbyte_extracted_at`; always re-read with a small safety overlap and let the idempotency key discard duplicates (standard watermark+overlap+dedup pattern — MEDIUM).
  - **Poison handling:** listeners retry transient failures (bounded, e.g. Spring Retry); on permanent failure mark the *record* `status=FAILED` with error payload and complete the event — a poison record must not wedge the publication registry. A worker "re-drive failed records" admin action covers recovery. Incomplete publications (crash mid-listener) are separately re-driven by the republish-on-restart mechanism.
- **At-least-once means every listener must be idempotent.** Embedding generation, KA creation, candidate detection — all keyed so double-delivery is harmless.

**When to use:** every cross-module pipeline handoff. Within a single module (e.g., parse → chunk inside `knowledge`), plain method calls in one transaction — do not event-ify internals.

**Trade-offs:** `event_publication` table grows — use completion mode `ARCHIVE` (or `DELETE` for low-value events) rather than default `UPDATE`+manual purge. Async listeners run in worker/api JVMs; pin ingestion listeners to the worker deployable via profile/conditional registration so the API JVM doesn't run pipeline work.

### Pattern 3: Filter-Then-Search Hybrid Retrieval on pgvector 0.8 (permission-aware retrieval)

**What:** Permission filtering happens **inside the SQL query, before results exist** — never in application code after retrieval. pgvector applies `WHERE` clauses after traversing the HNSW index, which with default `hnsw.ef_search=40` and a selective ACL filter silently returns too few (or zero) results — the classic recall-degradation trap. pgvector **0.8.0's iterative index scans** (`SET hnsw.iterative_scan = relaxed_order; SET hnsw.max_scan_tuples = ...`) keep pulling candidates until enough rows pass the filter, fixing this without giving up the index (pgvector release notes + AWS/Nile writeups — MEDIUM).

**Decision matrix for the pilot:**

| Situation | Strategy |
|-----------|----------|
| User's allowed set is small (pilot reality: 20–100 users, scoped sources) | **Exact scan over the filtered subset** — skip HNSW entirely; ORDER BY distance on a few thousand permitted chunks is fast and has perfect recall |
| Allowed set is large (broad `public`/org-wide keys) | HNSW + iterative scan + ACL join in WHERE |
| Never | Post-filter in Java after vector search (leakage risk: over-fetching exposes forbidden content to ranking/logging; under-fetching starves results) |

Pilot recommendation: **start with exact filtered scan** (correctness-first, no index tuning), add the HNSW/iterative-scan path when chunk counts demand it. Verify plans with `EXPLAIN`.

**Hybrid ranking:** combine tsvector full-text and vector similarity with Reciprocal Rank Fusion — two subqueries, `FULL OUTER JOIN`, `score = Σ 1/(60 + rank)`. RRF works on ranks, so no score normalization. **The ACL filter must appear in BOTH subqueries** so neither branch leaks. Hybrid typically buys 8–15% precision over either method alone (multiple independent sources — MEDIUM). Plain SQL; no external search engine at pilot scale.

**Citation plumbing:** retrieval returns chunk IDs → chunks reference `knowledge_asset` → KA references `normalized_record`/`raw_source_object` (source URL, title, `acl_synced_at`). Because filtering happened pre-retrieval, every citation is already permission-checked; render citations only from the retrieved set, and have the LLM cite by index into that set (never let it fabricate source URLs).

**Example (shape):**
```sql
WITH allowed AS (            -- user's held ACL keys, resolved once per request
  SELECT ka.knowledge_asset_id FROM knowledge_asset_acl ka
  WHERE ka.acl_key = ANY(:user_acl_keys)
),
fts AS (
  SELECT c.id, ROW_NUMBER() OVER (ORDER BY ts_rank_cd(c.tsv, q) DESC) rnk
  FROM knowledge_chunk c JOIN allowed a ON a.knowledge_asset_id = c.knowledge_asset_id,
       websearch_to_tsquery(:query) q
  WHERE c.tsv @@ q LIMIT 30
),
vec AS (
  SELECT c.id, ROW_NUMBER() OVER (ORDER BY c.embedding <=> :qvec) rnk
  FROM knowledge_chunk c JOIN allowed a ON a.knowledge_asset_id = c.knowledge_asset_id
  ORDER BY c.embedding <=> :qvec LIMIT 30
)
SELECT COALESCE(fts.id, vec.id) id,
       COALESCE(1.0/(60+fts.rnk),0) + COALESCE(1.0/(60+vec.rnk),0) score
FROM fts FULL OUTER JOIN vec USING (id) ORDER BY score DESC LIMIT :k;
```

### Pattern 4: Synchronous, Same-Transaction Audit Writes (governance)

**What:** Security-relevant audit events (login, import, permission change, approval, export, MCP call, AI answer) are written by a **direct call to `AuditService` inside the same transaction** as the domain change — not an async `@ApplicationModuleListener`. Async gives you a window where the domain change committed but the audit row didn't (process crash) — unacceptable for the events auditors care about; at-least-once async also produces duplicate audit rows.

**Immutability enforcement at the DB level, not just convention:** application role gets `INSERT` + `SELECT` only on `audit_event`; `REVOKE UPDATE, DELETE`; belt-and-braces `BEFORE UPDATE OR DELETE` trigger raising an exception; the app role must not own the table. Optional tamper evidence via hash chaining (`row_hash = sha256(payload || prev_hash)`) — nice for enterprise security review, cheap to add at schema time, painful to retrofit. Retention via monthly partitioning + archive, never in-place delete. (Multiple sources — MEDIUM.)

**When async is fine:** usage analytics / non-security telemetry (asset view counts) can go through events; losing one is tolerable.

**Trade-off:** sync writes add a row insert to every governed operation — negligible at pilot scale; the atomicity guarantee is worth far more.

### Pattern 5: Events for Pipeline, Direct Calls for Queries (module communication rules)

**What:** A simple rule for the new modules:

- **Modulith events** (via publication registry) when the interaction is a *state transition someone else reacts to*: raw object staged, KA created, candidate detected, asset approved. Decouples pipeline stages, survives crashes, lets worker/api host different listeners.
- **Direct bean calls** (public module API) when the caller *needs an answer now*: `PermissionResolver.aclKeysFor(user)`, `RetrievalService.search(...)`, `AuditService.record(...)`. Wrapping request/response in async events adds latency and failure modes for zero benefit.

Dependency direction to enforce in Modulith verification: `ingestion → identity, governance`; `knowledge → identity, governance`; `retrieval → knowledge, capability, identity`; `capability → identity, governance, organization`; nothing depends on `retrieval`; `identity` and `governance` depend on nothing new (governance may reference organization).

## Data Flow

### Ingestion Flow (write path — the trust boundary)

```
Source (Drive/Slack/n8n JSON/file upload)
    ↓  Airbyte (later) writes FINAL typed tables to `staging` schema
    ↓  — note: destination-postgres v3 uses Direct Load; raw airbyte_internal
    ↓    tables are deprecated → importer reads final tables + _airbyte_extracted_at
    ↓  cheap importers (n8n JSON / file upload) write staging directly (first)
[TRUST BOUNDARY — nothing below this line is user-visible]
    ↓  worker: IngestionJobRun (watermark + overlap) reads staging
raw_source_object  (idempotency key: connection + external_id + content_hash)
    +  source_acl_snapshot rows captured per object (connector ACL fetcher)
    ↓  event: RawObjectStaged → normalize listener (parse/clean/PII flag)
normalized_record
    ↓  event: NormalizedRecordReady → knowledge listener (chunk, dedup, embed)
knowledge_asset + knowledge_chunk(+embedding) + knowledge_asset_acl (denormalized keys)
    ↓  event: KnowledgeAssetCreated → candidate detector (AI, never auto-publish)
capability_candidate  →  human review (existing capability module)  →  capability_asset
```

Every stage transition emits an audit event (sync) and is idempotent under event re-delivery.

### Retrieval Flow (read path)

```
User request (web /ask, registry search, MCP tool, export)
    ↓  api resolves principal from OIDC session → identity.PermissionResolver
user's ACL key set (email identities + flattened group memberships + role grants)
    ↓  retrieval.RetrievalService — ONE SQL: ACL filter inside both FTS and
    ↓  vector subqueries → RRF fusion → top-k permitted chunks
permitted chunks + capability assets
    ↓  Ask Memory: LLM answers grounded ONLY in retrieved set; citations by index
response + citations (source URL, acl_synced_at)  +  sync audit_event (query, answer, sources)
```

### Key Data Flows

1. **Group sync (identity freshness):** scheduled worker job per source → fetch groups/memberships → flatten nested groups → rewrite `principal_group_membership` → affects retrieval immediately (query-time key resolution reads current memberships), no re-indexing of documents needed. This is why doc ACL keys reference *groups*, not expanded users.
2. **ACL re-sync (revocation propagation):** lighter-weight than content sync; refreshes `source_acl_snapshot`/`knowledge_asset_acl` for changed objects; drives the staleness SLA.
3. **Airbyte handoff:** Airbyte owns OAuth/pagination/rate-limits/incremental sync and writes staging only. OrgMemory's importer treats Airbyte's final tables as just another staging source with `_airbyte_extracted_at` as the watermark column. Because the importer SPI is the same for n8n-JSON/file-upload importers, deferring Airbyte costs nothing architecturally.

## Build Order (dependency-driven — direct roadmap input)

1. **Identity + auth first** (OIDC login, `principal`/`external_identity`/group tables, roles). Everything downstream keys ACLs and audit actors to principals; retrofitting identity is the most expensive mistake available.
2. **Governance/audit second** (append-only `audit_event`, `AuditService`, DB grants, hash chain). Cheap now; every later feature then wires audit in as it's built instead of retrofitted. Also: extract seed data from migrations V4–V7 (blocks clean tenant deploys).
3. **Ingestion spine third** (staging schema, `raw_source_object`, `normalized_record`, job/watermark tables, Modulith event wiring, **one cheap importer**: n8n JSON or file upload). Proves pipeline semantics before any connector complexity.
4. **ACL snapshots + knowledge fourth** (`source_acl_snapshot`, `knowledge_asset` + chunks + embeddings + denormalized ACL keys, KA browse surface). Depends on identity (keys) and ingestion (objects).
5. **Permission-aware retrieval fifth** (ACL-filtered hybrid SQL, citations, Ask Memory rewire, existing keyword ranker replaced). Depends on knowledge + identity.
6. **MCP tools + Airbyte + hardening last** (permission-aware MCP tools reuse `retrieval`/`capability` services with service-principal auth; Airbyte lands when the staging consumer already works; backup/monitoring/ASVS review).

The critical insight: **steps 1–2 have no research risk and unblock everything; step 5 cannot exist without 1 and 4; Airbyte is deliberately last** despite being the most visible "enterprise" piece.

## Scaling Considerations

| Scale | Architecture Adjustments |
|-------|--------------------------|
| Pilot (20–100 users, 1–3 depts, 2–3 sources) | Everything above as-is; exact filtered vector scan; single Postgres; worker as one JVM |
| Department rollout (1k users, 10+ sources) | HNSW + iterative scans; partition audit_event; multiple worker instances (job claiming via `FOR UPDATE SKIP LOCKED`); ACL re-sync frequency becomes a real SLA conversation |
| Org-wide (10k+ users, 50k-employee org) | Consider dedicated search infra (or ParadeDB/BM25) only if hybrid SQL degrades; evaluate policy-engine mirroring for sources with poor ACL APIs; Airflow only if job DAGs outgrow worker (per existing decision) |

### Scaling Priorities

1. **First bottleneck: ACL key-set size + vector scan cost per query.** Fix: flattened group memberships (already designed), iterative HNSW scans, per-user key-set caching with short TTL.
2. **Second bottleneck: ingestion job throughput / event_publication growth.** Fix: ARCHIVE completion mode, batch listeners, more worker instances.

## Anti-Patterns

### Anti-Pattern 1: Post-filtering retrieval results in application code

**What people do:** vector-search top-50, then drop rows the user can't see in Java.
**Why it's wrong:** two failure modes — *leakage* (forbidden content enters ranking, logs, LLM context) and *starvation* (strong filters leave 0 of 50 survivors while permitted matches exist deeper in the index). Both are silent.
**Do this instead:** ACL predicate inside the SQL (both FTS and vector branches); iterative scans or exact filtered scan; treat "permission checks before retrieval" as a testable invariant (write a test where user B must never retrieve user A's private doc, at every surface: search, ask, MCP, export).

### Anti-Pattern 2: Treating embeddings as permission-neutral

**What people do:** one shared embedding space, assume vectors are safe because they're "just numbers."
**Why it's wrong:** embeddings reconstruct content; similarity scores leak existence. The project constraint already states it: embeddings are not permission boundaries.
**Do this instead:** chunks carry ACL keys; every query path (including MCP and any future "related assets" feature) goes through the same filtered retrieval service. One retrieval service, no side doors.

### Anti-Pattern 3: Airbyte (or any connector) writing into domain tables

**What people do:** point the connector at `knowledge_asset` to "save a step."
**Why it's wrong:** destroys the trust boundary — no ACL snapshot, no dedup, no PII gate, no provenance; schema drift in the source breaks the domain.
**Do this instead:** connectors land in `staging` schema only; the worker importer is the sole writer into `raw_source_object`. Enforce with DB grants (Airbyte's role has no privileges on domain schema).

### Anti-Pattern 4: Async audit for security-relevant events

**What people do:** `@ApplicationModuleListener` on `AssetApproved` writes the audit row eventually.
**Why it's wrong:** crash window loses the audit record for a change that committed; at-least-once duplicates rows; auditors and ASVS reviewers will flag both.
**Do this instead:** sync `AuditService.record(...)` in the same transaction; async only for lossy analytics.

### Anti-Pattern 5: Event-ifying everything between modules

**What people do:** after adopting Modulith events for the pipeline, route synchronous needs (permission resolution, search) through request/reply events.
**Why it's wrong:** adds latency, failure modes, and debugging pain to hot paths; the publication registry is an outbox, not an RPC bus.
**Do this instead:** Pattern 5 rule — events for state transitions, direct public-API calls for queries.

## Integration Points

### External Services

| Service | Integration Pattern | Notes |
|---------|---------------------|-------|
| OIDC provider (Keycloak/Entra/Okta) | Spring Security OAuth2 login + resource server; OIDC `sub`+email link to `app_users`/`external_identity` | Roles from local assignment first; IdP-group mapping later. On-prem pilots often mean Keycloak — test with it |
| Airbyte (self-managed, `abctl`) | Writes staging schema; OrgMemory polls final tables by `_airbyte_extracted_at` watermark | Deferred until cheap importer proves the pipeline (existing decision). destination-postgres v3 = Direct Load, no raw tables — target final tables |
| Source ACL APIs (Drive permissions, Slack members, MS Graph groups) | Worker-side ACL fetchers per connector, separate from content sync | Airbyte does NOT fetch ACLs for you — budget per-connector ACL work explicitly; this is the hidden cost of each new source |
| LLM provider (Spring AI) | Existing pattern kept: enrichment + Ask Memory with local fallback, boots without key | LLM sees only post-filter retrieved content; AI answer + cited sources audited |
| MCP clients (agents) | `apps/mcp` tools call core `retrieval`/`capability` services with an authenticated principal (API key → service principal bound to a user's permissions) | Never a superuser tool surface; every tool call audited; same retrieval service = same ACL filter |

### Internal Boundaries

| Boundary | Communication | Notes |
|----------|---------------|-------|
| ingestion → knowledge | Modulith events (`NormalizedRecordReady`) | At-least-once; listener idempotent by record id |
| knowledge → capability | Modulith event (`CapabilityCandidateDetected`) | AI creates candidates only; human approval stays in capability module |
| api/mcp → retrieval | Direct call | Hot path; principal + ACL keys resolved per request |
| * → identity | Direct call (`PermissionResolver`) | Pure query; no events |
| * → governance | Direct call (`AuditService`), same transaction | Sync for security events (see Pattern 4) |
| worker ↔ api | Shared DB + event_publication table; listeners partitioned by deployable | Pin pipeline listeners to worker via profiles so API JVM stays request-focused |

## Multi-Tenancy Posture

**Single-tenant per install; keep `organization_id` columns; build zero multi-tenant infrastructure.** On-prem deployment *is* the tenant boundary — the strongest isolation available (separate DB, separate keys, separate network). Keep the existing `organizations` table and FK discipline so a future managed/multi-tenant offering has a schema-level seam (RLS on `organization_id` is the natural upgrade path), but do not add RLS, tenant resolvers, or per-tenant config machinery now. The cost is one column already paid for; the alternative (retrofitting org scoping) is the expensive path. (Reasoned recommendation — MEDIUM.)

## Sources

- Spring Modulith event publication registry, `@ApplicationModuleListener`, republish-on-restart, completion modes — Context7 `/spring-projects/spring-modulith` (source-verified; MEDIUM per confidence seam)
- [pgvector 0.8.0 release — iterative index scans](https://www.postgresql.org/about/news/pgvector-080-released-2952/); [AWS: pgvector 0.8.0 filtering on Aurora](https://aws.amazon.com/blogs/database/supercharging-vector-search-performance-and-relevance-with-pgvector-0-8-0-on-amazon-aurora-postgresql/); [Nile: pgvector 0.8.0](https://www.thenile.dev/blog/pgvector-080); [pgEdge filtering docs](https://docs.pgedge.com/pgvector/v0-8-1/filtering/) — MEDIUM (cross-checked)
- [Oso: respect 3rd-party permissions vs sync to your own system](https://www.osohq.com/post/should-you-respect-3rd-party-permissions-or-sync-to-your-own-system-the-rag-chatbot-dilemma); [Onyx access controls](https://docs.onyx.app/security/architecture/access_controls); [Glean permissions-aware AI](https://www.glean.com/perspectives/security-permissions-aware-ai); [Paragon: permissions for production RAG](https://www.useparagon.com/learn/permissions-access-control-for-production-rag-apps/) — MEDIUM (pattern corroborated; Onyx EE internals LOW)
- [Supabase hybrid search (RRF)](https://supabase.com/docs/guides/ai/hybrid-search); [Jonathan Katz: hybrid search with Postgres/pgvector](https://jkatz05.com/post/postgres/hybrid-search-postgres-pgvector/); [ParadeDB: hybrid search missing manual](https://www.paradedb.com/blog/hybrid-search-in-postgresql-the-missing-manual) — MEDIUM
- [Airbyte Postgres destination docs](https://docs.airbyte.com/integrations/destinations/postgres) (raw tables deprecated in v3.0.0, Direct Load); [Destinations V2 upgrade notes](https://docs.airbyte.com/release_notes/upgrading_to_destinations_v2) — MEDIUM
- [PostgreSQL wiki audit trigger](https://wiki.postgresql.org/wiki/Audit_trigger_91plus); [AppMaster: tamper-evident audit trails with hash chaining](https://appmaster.io/blog/tamper-evident-audit-trails-postgresql) — MEDIUM
- Spring Batch positioning: [Spring Batch common patterns](https://docs.spring.io/spring-batch/reference/common-patterns.html); watermark+overlap+dedup: [Unstructured incremental ingestion](https://unstructured.io/insights/incremental-data-ingestion-strategies-for-continuous-pipelines) — MEDIUM

---
*Architecture research for: OrgMemory enterprise pilot foundation (permission-aware RAG + governance in Spring Modulith)*
*Researched: 2026-07-06*
