# Pitfalls Research

**Domain:** Enterprise AI Capability Registry — permission-aware RAG + AI asset lifecycle governance, on-prem pilot
**Researched:** 2026-07-06
**Confidence:** MEDIUM (web sources cross-checked across multiple independent vendors, CVE write-ups, and platform docs; provider-tier per classify-confidence seam)

Scope: what goes wrong when a registry prototype moves to an enterprise pilot with real identities, real ACLs, and real data — exactly the OrgMemory active milestone (OIDC, ingestion spine, ACL snapshots, permission-aware pgvector retrieval, immutable audit, MCP tools, pilot ops).

## Critical Pitfalls

### Pitfall 1: Post-Retrieval Permission Filtering (Filter-After-Search)

**What goes wrong:**
The system retrieves top-k chunks first, then removes unauthorized ones before display. Two leaks result: (a) the LLM already consumed unauthorized content in its context — summaries, answers, and refusals can paraphrase restricted material even when the citation is stripped; (b) result counts, rankings, and "similar assets" hints reveal the existence of restricted content. Post-filtering is the default shape of every naive RAG tutorial, so teams inherit it without deciding it.

**Why it happens:**
Retrieval libraries (Spring AI `VectorStore.similaritySearch`, LangChain retrievers) return documents first and make filtering look like an app-layer concern. Permission checks get bolted on where they're visually convenient (the response renderer) rather than where they're safe (the query).

**How to avoid:**
Enforce ACL predicates inside the SQL query — pgvector queries must carry a `WHERE` clause derived from the caller's resolved principal set before any embedding distance is computed or any row leaves PostgreSQL. Make one shared authorization-scoped repository/query builder the *only* path to knowledge/asset rows, used identically by keyword search, vector search, Ask Memory grounding, MCP tools, graph derivation, analytics, and export. Ban any code path that fetches rows and filters in Java/TypeScript.

**Warning signs:**
- Any service method that takes rows and a user and returns a subset.
- Ask Memory prompt assembly that doesn't receive an already-filtered candidate list.
- Tests that only assert "restricted asset not shown," never "restricted asset not in LLM context."

**Phase to address:**
Permission-aware retrieval phase — must be designed before the ingestion-spine migrations (schema of `source_acl_snapshot` depends on the filter mechanics; PROJECT.md already flags this).

---

### Pitfall 2: pgvector HNSW Recall Collapse Under Selective ACL Filters

**What goes wrong:**
With HNSW, pgvector post-filters index candidates: it walks the graph for `ef_search` candidates, *then* applies the `WHERE` clause. When a user's allowed set is a small fraction of the corpus (normal in a departmental pilot — a viewer may see 1–5% of rows), most candidates get discarded and queries return far fewer than k results or zero. The dangerous failure is the team's reaction: search "looks broken," and someone weakens or removes the permission predicate to fix relevance.

**Why it happens:**
HNSW graphs are built over the whole dataset; arbitrary predicate subsets break graph traversal efficiency. This is a known limitation across vector DBs, not a pgvector bug — but pgvector's default behavior makes it silent (no error, just missing results).

**How to avoid:**
Design for it explicitly: (a) use pgvector 0.8+ iterative index scans (`hnsw.iterative_scan`) so the scan continues until k allowed rows are found; (b) for small allowed sets, pre-filter to the allowed row IDs and do exact (sequential) scan — exact search over a few thousand permitted rows is fast and has perfect recall; (c) benchmark filtered recall with realistic ACL selectivity (1%, 10%, 50%) as an acceptance criterion, not an afterthought.

**Warning signs:**
- Vector search tests only run against a corpus where the test user can see everything.
- "Search returns nothing for restricted users" bug reports.
- `ef_search` being cranked up repeatedly to compensate.

**Phase to address:**
Permission-aware retrieval phase; include a filtered-recall benchmark in its verification gate.

---

### Pitfall 3: Derived Artifacts Leak Across ACL Boundaries

**What goes wrong:**
The primary rows are filtered correctly, but derived data leaks: AI-generated summaries of restricted sources, knowledge-graph edges connecting a visible asset to a restricted one, embedding-similarity "related assets," usage analytics aggregates ("most used prompt in Legal"), citations pointing at documents the reader can't open, and export/handover packs assembled server-side without re-checking the *recipient's* permissions. Every derivation is a copy of data that escaped its ACL.

**Why it happens:**
ACLs are modeled as a property of the source row, not as a property that propagates through lineage. Enrichment workers, graph builders, and analytics jobs run as a privileged service identity and write outputs into tables that have no ACL columns of their own.

**How to avoid:**
Every derived table (summaries, embeddings, graph edges, candidate records, analytics rollups) must carry a provenance link to its source rows and inherit the *most restrictive* effective ACL of its inputs. Handover packs and exports are generated per-recipient at request time through the same filtered query path — never pre-generated. Graph API responses prune nodes/edges the caller can't see rather than rendering them anonymized (existence is still a leak).

**Warning signs:**
- Any table with content columns but no ACL/provenance columns.
- Graph or analytics endpoints that don't take the caller's identity.
- "Generate handover pack" implemented as a stored artifact rather than a per-request render.

**Phase to address:**
Ingestion & governance spine phase (schema-level: provenance + inherited ACL columns from day one); re-verified in the retrieval and MCP phases.

---

### Pitfall 4: Stale ACL Snapshots — Revoked Access That Keeps Working

**What goes wrong:**
ACLs are snapshotted at ingestion time (correct design), but nothing refreshes them. An employee leaves, moves departments, or a source document is locked down — and OrgMemory keeps serving it for days or forever. Terminated-employee-still-retrieving is the single most cited enterprise-search vulnerability, and in a pilot it's the incident that ends the pilot.

**Why it happens:**
Snapshot-at-ingest is treated as the whole answer. Revocation propagation (re-sync cadence, event-driven invalidation, offboarding hooks) is deferred as "phase 2" and never scheduled. Meanwhile OIDC group claims cached in sessions add a second staleness layer.

**How to avoid:**
Define a written staleness budget per surface (e.g., source ACL re-sync ≤24h; OrgMemory-native role/visibility changes effective immediately; session/group-claim TTL ≤15min). Build ACL re-sync as a first-class worker job with its own watermark and monitoring. Offboarding in OrgMemory must immediately disable the user *and* trigger review of assets they own — that's the product's own pitch, so it must work in the platform itself.

**Warning signs:**
- No `acl_synced_at` timestamp or no alert when it ages past budget.
- Permission change in the source system requires full re-ingestion to take effect.
- Role changes only apply after logout/login.

**Phase to address:**
Source ACL snapshot / identity mapping phase (design the refresh contract); pilot ops phase (monitor staleness as an SLO).

---

### Pitfall 5: Identity Mapping Fails Open — Unmapped Principals, Nested Groups, Guests

**What goes wrong:**
Source ACLs reference principals (Entra object IDs, Google emails, Slack user IDs, AD groups) that don't cleanly map to OrgMemory users. Known industry failures: SCIM/Entra provisioning does not expand nested group memberships (users arrive with empty `groups` arrays and authorization silently fails); guest/B2B accounts have mismatched identifiers (UPN vs mail); service accounts and non-human principals can't be mapped at all. Teams then choose a default — and defaulting *open* ("unmapped means everyone") is a breach, while defaulting *closed* without visibility makes content silently invisible and users file "search is broken" tickets.

**Why it happens:**
Identity mapping looks like a lookup table until real directories arrive with nesting, guests, deleted users, and per-source ID formats. Connector docs (Google Cloud Search, Agentspace) require explicit identity-mapping stores for a reason.

**How to avoid:**
Fail closed, but make it observable: unmapped principals deny access AND land in an admin "unresolved identities" queue with counts per source. Flatten nested groups at snapshot time and record the flattening depth/timestamp. Store the raw source principal alongside the mapped OrgMemory user so mappings are re-resolvable when the directory changes. Treat MCP/service identities as first-class principals with their own (narrow) grants, never as a superuser.

**Warning signs:**
- ACL snapshot schema stores only mapped user IDs (raw principal discarded).
- No admin surface showing unmapped-principal counts.
- Group membership resolved live from OIDC claims only, with no directory sync for group *contents*.

**Phase to address:**
Source ACL snapshot / identity mapping phase — PROJECT.md correctly lists this as one of three undesigned hard parts; it must precede ingestion migrations.

---

### Pitfall 6: Non-Idempotent Ingestion — Duplicates, Watermark Bugs, Poison Records

**What goes wrong:**
Worker retries or backfills re-insert the same source objects, producing duplicate knowledge assets (and duplicate embeddings that skew retrieval). Watermarks advance before the batch commits, so a crash loses records silently; or windows overlap and double-count. A single malformed record (poison) fails deserialization, the job retries it forever, and the whole pipeline stops — head-of-line blocking. Failures discarded without recording become silent data loss the customer discovers before you do.

**Why it happens:**
Prototype importers are written append-only and happy-path. Idempotency requires a deterministic natural key per source object and upsert semantics — a design decision, not a patch. Watermark correctness requires transactional coupling of "data written" and "watermark advanced."

**How to avoid:**
Every `raw_source_object` carries a deterministic dedup key (source system + source ID + content hash) with a unique constraint; writes are upserts. Advance watermarks in the same transaction as the batch write. Bounded retries (e.g., 3) then route to a dead-letter table with the error, payload reference, and an admin requeue surface. Job-state table with per-run counts (read/written/skipped/dead-lettered) so drift is visible. These semantics are exactly PROJECT.md's second undesigned hard part — design before migrations.

**Warning signs:**
- No unique constraint on the staging table's source identity.
- Retry logic wrapping a whole job instead of per-record handling.
- No dead-letter table in the schema; no per-run job metrics.

**Phase to address:**
Worker pipeline semantics phase (design), first-importer phase (n8n JSON / file upload validates the semantics cheaply before Airbyte, per the standing decision).

---

### Pitfall 7: Indirect Prompt Injection From Ingested Content Reaching Ask Memory and MCP

**What goes wrong:**
An ingested document, n8n workflow description, or submitted prompt contains hidden instructions (white-on-white text, HTML comments, Unicode tricks) that the LLM executes when the content is retrieved into Ask Memory or an MCP tool response. EchoLeak (CVE-2025-32711) proved this is zero-click in production systems: a crafted email made M365 Copilot exfiltrate data via a markdown image URL the client auto-fetched. One poisoned document contaminates every future query that retrieves it. OrgMemory is doubly exposed: its core objects *are* prompts, so injection payloads look like legitimate content; and MCP hands retrieved content to external agents that can act on it (OWASP LLM01 + LLM06 excessive agency).

**Why it happens:**
Retrieved content is concatenated into the prompt as if it were trusted. The trust boundary that the four-layer memory model encodes in the *data* (RawSource ≠ Knowledge) isn't mirrored in the *prompt assembly*.

**How to avoid:**
Treat all retrieved content as data, never instructions: delimit it in structured blocks with an explicit system instruction that content cannot alter behavior. Sanitize on render — the chat UI and any MCP consumer must not auto-fetch remote images/URLs from model output (the EchoLeak exfiltration channel); render citations as internal links only. MCP tools return structured JSON fields, not free-text blobs an agent will interpret. AI-generated candidates never auto-publish (already a product principle — keep it as the injection firewall too). Add an LLM Top 10 red-team pass with seeded hostile documents to the security-review gate.

**Warning signs:**
- Prompt templates that interpolate retrieved text without delimiters or role separation.
- Chat UI rendering raw markdown from the model including remote images.
- MCP tool responses that include verbatim source content with no origin/trust labeling.

**Phase to address:**
Ask Memory hardening phase and MCP phase; verified in the pilot-hardening security review (ASVS + LLM Top 10).

---

### Pitfall 8: Demo Data Baked Into Migrations Blocks Clean Tenant Deploys

**What goes wrong:**
Seed/demo rows live inside Flyway versioned migrations (OrgMemory's V4–V7, already a known debt). The first real pilot deploy ships fictional employees, departments, and assets into a customer database; deleting them breaks FK chains and audit history; and because versioned migrations are immutable, you can't remove them without checksum repair gymnastics. A design partner seeing fake employees in their on-prem install reads it as amateurism.

**Why it happens:**
Demo-readiness pressure during prototyping; Flyway makes seeding via migration the path of least resistance.

**How to avoid:**
Move all seed data out of versioned migrations before any pilot install: use Flyway callbacks or a profile-gated seeder (`demo` profile), keep V-migrations schema-only. Since this is pre-production, a one-time squash/baseline is acceptable now and much cheaper than after a customer deploy exists.

**Warning signs:**
- `INSERT` statements in `core/src/main/resources/db/migration/V*.sql`.
- No documented "clean tenant" install path tested against an empty database.

**Phase to address:**
First phase of the milestone (foundation/cleanup) — cheapest now, expensive after the pilot schema grows on top of it.

---

### Pitfall 9: Pilot Perceived as Surveillance — Contribution Collapses

**What goes wrong:**
Usage tracking, audit events, and offboarding "capability capture" read to employees as monitoring. Contribution drops to zero, or worse, works councils/HR halt the pilot. Separately, contributions that do arrive stall in an under-resourced review queue (one reviewer, no SLA), the registry looks stale, and the pilot's success metric — reuse by non-creators — never materializes. Enterprise AI research consistently shows adoption/trust failure, not technology failure, kills >80% of pilots between PoC and production.

**Why it happens:**
Analytics get built for admins first; the employee-facing value story ("your asset got reused 12 times") is deferred. Review capacity is treated as a workflow feature, not a staffing commitment from the design partner.

**How to avoid:**
Frame every tracked event as contributor-visible value: usage analytics must be explainable to employees (ENTERPRISE_READINESS.md already requires this — make it a UI requirement, e.g., contributors see their own impact; managers see aggregates, not per-person keystroke trails). No passive capture (already policy). Set a review SLA with the design partner in the pilot agreement (e.g., 5 business days), surface queue-age on the dashboard, and support delegated reviewers per department. Keep pilot scope contractual: 1–3 departments, expansion only after permission/audit/trust gates pass — resist the partner's own scope-creep pressure.

**Warning signs:**
- Analytics screens showing per-employee activity to managers.
- Review queue median age growing week over week.
- Pilot stakeholders asking to "just index the whole Drive" in week 2.

**Phase to address:**
Pilot ops / rollout phase (pilot agreement + dashboards); analytics design in whichever phase touches usage events.

---

### Pitfall 10: On-Prem Ops Debt — Untested Restores, No Rollback Story, Leaked Secrets

**What goes wrong:**
Backups exist but the restore has never been executed; the first real restore attempt (during an incident, at the customer's site, without your infrastructure) fails or loses the pgvector extension state. Flyway has no automatic down-migrations — a bad migration on customer hardware with no rollback plan means a multi-hour outage negotiated over the customer's change-control process. Secrets (LLM keys, OIDC client secrets, DB passwords) end up in application logs, `application.yml` in the repo, or customer-visible stack traces.

**Why it happens:**
Ops work has no demo value, so it's perpetually deferred. On-prem removes every safety net a cloud team is used to (managed backups, quick redeploys, your own observability).

**How to avoid:**
Backup/restore drill on a production-like stack (Postgres + pgvector + Flyway history) as an explicit phase deliverable with a written runbook and timing measurement — before pilot data exists. Adopt fix-forward as the documented migration strategy, with pre-migration backup as the true rollback (Redgate's guidance: rollback scripts are not a substitute for restore). Migrations tested against a restored copy of realistic data in CI. Secrets via environment/externalized config only; log-scrubbing verified; a secrets checklist in the install runbook.

**Warning signs:**
- No `RESTORE.md`/runbook or a runbook that has never been executed end-to-end.
- Migration merged without being run against a non-empty database.
- `grep` finds keys or passwords in repo, logs, or error responses.

**Phase to address:**
Pilot ops phase — but the restore drill and secrets hygiene should be pulled earlier (before the first real import), not left to "month 4."

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Post-filter permissions in app code | Ships search fast | Structural leak; rewrite of every retrieval path | Never for real data |
| Seed data in versioned migrations | Instant demo | Blocks clean tenant deploys; FK-entangled cleanup | Never once a pilot exists (fix now) |
| Append-only ingestion without dedup keys | Simple importer | Duplicate assets + duplicate embeddings skew retrieval; unfixable without re-ingest | Only for throwaway local tests |
| Single service identity for worker/MCP with full DB access | No auth plumbing | Any injection or bug becomes total data access | MVP only if MCP is disabled for pilot users |
| Skipping ACL columns on derived tables ("add later") | Faster schema | Retrofitting lineage/ACL onto populated tables is a migration nightmare | Never — columns are cheap now |
| Deferring audit table ("logs are enough") | Less schema work | Logs are mutable, unqueryable, not evidence; fails enterprise review | Never for this product — audit IS the product |
| Business logic in `apps/api` instead of `core` (existing graph/enrichment debt) | Faster iteration | MCP/worker can't reuse it; permission logic forks | Acceptable pre-pilot; repay before MCP phase |

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| OIDC (Entra/Keycloak) | Trusting group claims alone; claims get truncated (Entra caps ~200 groups → sends a groups-overage link instead) and don't include nested groups | Use claims for authn + coarse roles; sync group membership separately; handle overage; flatten nesting |
| OIDC | Roles resolved only at login; session carries stale roles for hours | Short session/claim TTL or per-request role resolution for sensitive operations |
| pgvector | Assuming HNSW respects `WHERE` before scanning | Iterative scans (0.8+), exact scan on small allowed sets, filtered-recall benchmarks |
| Spring AI | Assuming app boots without LLM config once model starters are added | Keep enrichment behind an interface with local fallback (existing constraint — preserve it through the retrieval rework) |
| Airbyte (later) | Letting connector output land in domain tables | Staging-only boundary (already policy); also snapshot ACLs at the same sync, not in a later pass — or the ACL and content watermarks diverge |
| n8n JSON import | Trusting workflow JSON structure across n8n versions; embedded credentials in exported JSON | Schema-validate per version; strip/flag credential nodes on import (secrets in workflow exports are common) |
| MCP | Tools authenticated as a service account, executing with service-level data access | Propagate end-user identity into every MCP tool call; tools use the same filtered query path as the web app; audit each call |
| Flyway | Expecting `undo`/rollback in OSS Flyway | Fix-forward + pre-migration backup; test migrations against restored realistic data |

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| HNSW post-filter recall collapse | Restricted users get few/zero search results | Iterative scans / exact scan on allowed subset; selectivity benchmarks | Allowed fraction <~10% of corpus — i.e., immediately in a departmental pilot |
| Per-row permission check via app callback | Search latency grows linearly with candidates | Set-based SQL predicate from pre-resolved principal set | A few thousand candidate rows |
| Resolving user's group closure on every request | Login/search latency spikes; DB hot spot | Materialize flattened principal sets with TTL; invalidate on membership change | >50 concurrent users with deep group nesting |
| Embedding everything at ingest synchronously | Import jobs time out; retries duplicate embeddings | Async embedding via worker with idempotent upsert per chunk | First real document source (>1k docs) |
| Audit writes on the request path, synchronous | p95 latency degradation on every action | Write-behind/outbox audit with guaranteed delivery; never drop events silently | Pilot load (~100 users) if audit table is un-indexed/un-partitioned |
| Graph derivation over full asset table per request | Graph page slows as registry grows | Precompute edges in worker with ACL-aware pruning at read time | ~10k assets/edges |

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Treating embeddings as anonymized/safe (OWASP LLM08) | Embedding inversion/membership inference recovers content; vectors bypass ACLs | ACL metadata on every vector row; vectors deleted when source is deleted; no cross-tenant vector reuse |
| Rendering model-output markdown with remote images in chat | EchoLeak-class zero-click exfiltration channel | Strip/proxy remote images and links in Ask Memory UI; CSP on the SPA |
| MCP tool descriptions/params echoed into prompts | Tool-definition injection steers agent behavior | Static, reviewed tool definitions; no dynamic tool text from stored content |
| Export/handover paths skipping permission checks | Bulk leak in one click — the highest-volume leak surface | Exports go through the same filtered query path, per-recipient, audited |
| Unmapped principals defaulting to org-wide visibility | Silent cross-department leak on first real import | Fail closed + unresolved-identity admin queue |
| Audit table with UPDATE/DELETE grants | "Immutable" audit that an admin can rewrite; fails evidence review | Append-only via DB grants (INSERT-only role), hash-chain or at least monotonic sequence check |
| LLM/OIDC/DB secrets in repo, logs, or error pages | Customer security review failure; credential theft | Externalized config, log scrubbing, secrets checklist in runbook |
| AI answers citing sources the user can't open | Existence + metadata leak via citations | Citations resolved against the caller's allowed set; unresolvable citations dropped, not shown locked |

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| Silent permission filtering ("no results" with no explanation) | Users think search is broken; file tickets; lose trust | Distinguish "nothing matches" from "results may be restricted" without revealing what's hidden |
| Per-employee activity dashboards visible to managers | Surveillance perception; contribution collapse | Contributor sees own impact; managers see department aggregates only |
| Review queue with no SLA/age indicators | Contributors stop submitting after items rot in review | Queue age surfaced, delegated reviewers, agreed SLA in pilot contract |
| Offboarding flow framed as "capture their knowledge" | Feels extractive; departing employees disengage | Frame as handover continuity: owner reassignment + backup-owner completeness |
| AI-enriched candidates auto-filled and near-publish | Low-quality dump; reviewers rubber-stamp | Candidates visibly provisional; require human-added metadata (owner, tool, I/O expectations) before review |

## "Looks Done But Isn't" Checklist

- [ ] **Permission-aware search:** often filters the visible list but not LLM context, graph, analytics, exports, MCP — verify with a canary restricted document asserted absent from *every* surface, including paraphrase in AI answers
- [ ] **ACL snapshots:** often snapshot-at-ingest only — verify a revocation in the source propagates within the staleness budget
- [ ] **Identity mapping:** often handles direct members only — verify nested-group and guest-account fixtures resolve, and unmapped principals land in the admin queue
- [ ] **Idempotent ingestion:** often "worked once" — verify re-running the same import twice produces zero new rows, and a mid-batch crash resumes without loss or duplicates
- [ ] **Immutable audit:** often just a table — verify UPDATE/DELETE are denied at the DB-grant level and every listed event type (login, import, permission change, approval, use, export, MCP call, AI answer) actually writes
- [ ] **OIDC auth:** often happy-path login only — verify role downgrade mid-session, group-overage claims, and logout/back-channel behavior
- [ ] **Backup/restore:** often a cron job — verify a timed restore drill on a clean machine recreates a working app (schema + pgvector + Flyway history)
- [ ] **Boots without LLM key:** often broken by the retrieval rework — re-verify after each AI-touching phase
- [ ] **Clean tenant deploy:** often blocked by seed data in V4–V7 — verify install against an empty DB yields zero demo rows

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Post-filter architecture discovered late | HIGH | Introduce the authorization-scoped query layer, migrate every read path onto it, add canary-leak tests; effectively a retrieval rewrite |
| Recall collapse in filtered vector search | MEDIUM | Enable iterative scans / switch small-allowed-set queries to exact scan; re-benchmark; no data migration needed |
| Duplicate ingestion already in DB | MEDIUM | Backfill dedup keys, merge duplicates (careful: usage/approval events reference asset IDs), re-embed once |
| Stale-ACL incident during pilot | HIGH (trust) | Immediate re-sync + audit-log disclosure to the partner of what was retrievable by whom; add staleness SLO monitoring; honesty is the only trust recovery |
| Demo data shipped to a customer DB | MEDIUM | Scripted purge with FK-aware ordering + Flyway checksum repair; prevent instead — cost is 10x post-deploy |
| Prompt-injection incident via Ask Memory | MEDIUM | Quarantine the poisoned source row (raw-source layer makes this possible), purge derived artifacts by provenance link, add the payload to red-team fixtures |
| Failed migration at customer site | HIGH | Restore pre-migration backup (this is why the drill exists), fix forward, re-run; document in incident log |

## Pitfall-to-Phase Mapping

Phases named by milestone activity area (roadmap not yet drawn):

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| Seed data in migrations (8) | Foundation/cleanup (first) | Empty-DB install produces zero demo rows |
| Post-filter leakage (1) | Retrieval/permission design (before ingestion migrations) | Canary restricted doc absent from all surfaces incl. LLM context |
| pgvector recall collapse (2) | Permission-aware retrieval | Filtered-recall benchmark at 1%/10%/50% selectivity |
| Derived-artifact leaks (3) | Ingestion & governance spine (schema) | Provenance + ACL columns on every derived table; graph/export leak tests |
| Stale ACL snapshots (4) | ACL snapshot & identity mapping | Revocation propagates within written staleness budget |
| Identity mapping fail-open (5) | ACL snapshot & identity mapping | Nested/guest/unmapped fixtures; unresolved-identity queue populated |
| Non-idempotent pipeline (6) | Worker pipeline semantics + first importer | Double-run import → zero new rows; crash-resume test; dead-letter surfaced |
| Prompt injection (7) | Ask Memory hardening + MCP | Red-team fixture docs; no remote-image rendering; LLM Top 10 review pass |
| Surveillance/adoption failure (9) | Pilot ops / rollout | Pilot agreement scope + review SLA; contributor-facing analytics only |
| Ops debt (10) | Pilot ops (drills pulled before first real import) | Timed restore drill; migration tested on restored data; secrets scan clean |

**Ordering implication:** the three "undesigned hard parts" in PROJECT.md (identity/ACL mapping, pipeline semantics, permission-filter mechanics) each map to a critical pitfall (5, 6, 1/2). All three must be *design-complete before ingestion-spine migrations*, or pitfalls 3–6 get baked into the schema.

## Sources

Confidence tiers per `classify-confidence` seam: cross-verified web findings = MEDIUM; single-source web = LOW. No claim below is single-source unless noted.

- Permission leakage in RAG: [kirkryan.co.uk — Item-Level Permissions in RAG](https://kirkryan.co.uk/item-level-permissions-in-rag-why-your-vector-database-needs-access-control/), [unified.to — Permissions, Security, and Compliance in RAG Pipelines](https://unified.to/blog/permissions_security_and_compliance_in_rag_pipelines), [truto.one — Document-Level RBAC for RAG Pipelines](https://truto.one/blog/how-to-maintain-document-level-rbac-in-enterprise-rag-pipelines/), [ragaboutit.com — The Permission Layer Problem](https://ragaboutit.com/the-permission-layer-problem-why-your-enterprise-rag-is-a-security-time-bomb/), [Petronella — Secure RAG Architecture Patterns](https://petronellatech.com/blog/secure-rag-enterprise-architecture-patterns-for-accurate-leak-free-ai/)
- Stale ACL / permission sync: [Glean — security features in enterprise search](https://www.glean.com/perspectives/how-do-security-features-affect-enterprise-search), [Knostic — Glean data security / oversharing](https://www.knostic.ai/blog/glean-data-security), [Glean — permissions structure for secure generative AI](https://www.glean.com/blog/secure-generative-ai-for-the-enterprise-requires-the-right-permissions-structure)
- pgvector filtered search: [dev.to (MongoDB) — No pre-filtering in pgvector means reduced ANN recall](https://dev.to/mongodb/no-pre-filtering-in-pgvector-means-reduced-ann-recall-1aa1), [yudhiesh — The Achilles Heel of Vector Search: Filters](https://yudhiesh.github.io/2025/05/09/the-achilles-heel-of-vector-search-filters/), [Clarvo — Optimizing filtered vector queries in PostgreSQL](https://www.clarvo.ai/blog/optimizing-filtered-vector-queries-from-tens-of-seconds-to-single-digit-milliseconds-in-postgresql), [arXiv — Filtered ANN Search: System Design and Performance](https://arxiv.org/pdf/2602.11443)
- Identity mapping: [WorkOS — Azure Entra nested groups and Directory Sync limitations](https://workos.com/blog/azure-entra-nested-groups-directory-sync), [Microsoft Learn — group claims](https://learn.microsoft.com/en-us/entra/identity/hybrid/connect/how-to-connect-fed-group-claims), [Google Cloud — Agentspace identity mapping](https://cloud.google.com/agentspace/agentspace-enterprise/docs/identity-mapping), [Databricks — SCIM provisioning limits](https://docs.databricks.com/aws/en/admin/users-groups/scim/aad)
- Ingestion pipeline: [Fivetran — Idempotence failure-proofs your pipeline](https://www.fivetran.com/blog/idempotence-failure-proofs-data-pipeline), [Prefect — Idempotent data pipelines](https://www.prefect.io/blog/the-importance-of-idempotent-data-pipelines-for-resilience), [Unstructured — Data ingestion challenges for AI](https://unstructured.io/insights/data-ingestion-common-challenges-and-solutions-for-ai), [Redpanda — DLQ message reprocessing](https://www.redpanda.com/blog/reliable-message-processing-with-dead-letter-queue)
- Prompt injection / EchoLeak: [Rescana — CVE-2025-32711 EchoLeak](https://www.rescana.com/post/cve-2025-32711-zero-click-echoleak-vulnerability-in-microsoft-365-copilot-enables-stealth-data-exfiltration-via-prompt-i), [Sentra — Copilot EchoLeak analysis](https://sentra.io/blog/copilot-echoleak-prompt-injection), [arXiv — EchoLeak: first real-world zero-click prompt injection](https://arxiv.org/html/2509.10540v1), [Lakera — Indirect Prompt Injection](https://www.lakera.ai/blog/indirect-prompt-injection), [VentureBeat — prompt injection targeting agents, RAG, routers](https://venturebeat.com/security/prompt-injection-is-exploiting-enterprise-ais-biggest-design-flaws-by-targeting-agents-rag-pipelines-and-model-routers)
- Pilot failure modes: [CIO Dive — Why enterprise AI pilots fail](https://www.ciodive.com/news/why-enterprise-ai-pilots-fail/808751/), [EPAM — Why 80% of AI pilots fail to scale](https://www.epam.com/insights/ai/blogs/enterprise-ai-deployment-challenges), [Forbes — Why AI adoption is failing inside companies](https://www.forbes.com/sites/kathycaprino/2026/06/26/why-ai-adoption-is-failing-inside-many-companies/), [Interact — enterprise AI governance gap](https://www.interactsoftware.com/blog/the-enterprise-ai-governance-gap/)
- Ops / Flyway rollback: [Redgate — Failed database deployments: roll back or fix forward](https://www.red-gate.com/hub/product-learning/flyway/failed-flyway-database-deployments-roll-back-or-fix-forward/), [Redgate — Implementing a roll back strategy](https://documentation.red-gate.com/fd/implementing-a-roll-back-strategy-138347142.html), [Harness — Database rollback strategies](https://www.harness.io/harness-devops-academy/database-rollback-strategies-in-devops)
- Project-internal (HIGH confidence, first-party): `D:\OrgMemory\.planning\PROJECT.md`, `D:\OrgMemory\docs\ENTERPRISE_READINESS.md`, `D:\OrgMemory\docs\PRODUCT_BRIEF.md`
- OWASP baselines referenced by the project: [OWASP LLM Top 10](https://owasp.org/www-project-top-10-for-large-language-model-applications/), [OWASP ASVS](https://owasp.org/www-project-application-security-verification-standard/)

---
*Pitfalls research for: enterprise permission-aware AI capability registry (prototype → on-prem pilot)*
*Researched: 2026-07-06*
