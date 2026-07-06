# Feature Research

**Domain:** Enterprise AI Capability Registry / governed organizational AI memory (permission-aware RAG + AI asset lifecycle governance, on-prem pilot)
**Researched:** 2026-07-06
**Confidence:** MEDIUM (cross-checked web research anchored in official vendor docs: Glean, Onyx, Langfuse, Microsoft Purview, modelcontextprotocol.io; classified via gsd classify-confidence)

**Scope note:** This milestone is *subsequent*. The prototype already has capability asset CRUD/versions/approval/usage, AI-enriched create flow, review queue, Ask Memory chat, knowledge graph, onboarding/offboarding transfer views, and dashboard/analytics. Those are NOT re-listed as table stakes. This file covers what an **enterprise pilot** additionally requires, mined from Glean, Onyx (ex-Danswer), AnythingLLM, Langfuse/PromptLayer, Collibra/DataHub, and Microsoft Purview + M365 Copilot governance.

## Feature Landscape

### Table Stakes (Enterprise Pilot Fails Without These)

Every credible product in this category gates enterprise deals on these. Onyx literally puts SSO, RBAC, document permissions, and audit logs behind its Enterprise Edition license — that is the market telling you what enterprises pay for.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| OIDC SSO + role model (admin/reviewer/contributor/viewer) | First question in every security review; Onyx/Glean/every vendor gates on it. MFA delegated to IdP. | MEDIUM | Spring Security OIDC. Map org/department/group claims to OrgMemory principals. SAML/SCIM deferred (already Out of Scope) but document the user-lifecycle story (deactivate on IdP removal). |
| Permission filtering BEFORE retrieval (keyword, vector, AI answer, MCP, export) | Industry consensus: ACL as pre-filter predicate inside the query; "if the model sees it, it can leak it." Glean's core promise is "you only see what you can see in the source." | HIGH | pgvector: mandatory WHERE clause on ACL principals joined/denormalized into the search query — never post-filter results. Two invariants: unauthorized rows never reach the app layer; never enter the prompt. |
| Source ACL snapshot at ingestion + identity mapping | Glean and Onyx connectors sync source permissions maps as a first-class feature. Without it, permission-aware retrieval has nothing to filter on. | HIGH | Snapshot source principals (users/groups) per raw_source_object at import time; map source principals → OrgMemory users/groups. This is one of the three "undesigned hard parts" in PROJECT.md — design before migrations. |
| Immutable audit log (login, import, permission change, approval, use, export, MCP call, AI answer w/ referenced assets) | Purview sets the bar: prompts AND responses audited, including which files were referenced. Security reviews require authn events, privileged actions, config changes, data access/export; retention ≥ 1 year. | MEDIUM | Append-only table, no UPDATE/DELETE grants. Record actor, action, object, timestamp, and for AI answers the cited asset IDs. Export as CSV/JSON for the customer's SIEM. |
| Citations in every AI answer, pointing only to permitted assets | Glean's trust story is grounded answers with citations to internal docs; Purview audits referenced files. An uncited AI answer is unverifiable and fails review. | MEDIUM | Ask Memory already ranks assets; pilot needs citations resolvable to an asset/knowledge-asset the caller can open. Citation link itself must re-check permission (no leaking titles of forbidden docs). |
| Staging boundary: raw_source → normalized → knowledge_asset → capability_candidate | Data catalogs distinguish raw/curated/certified; Purview distinguishes labeled vs groundable content. Imported data must not silently become trusted memory. | HIGH | Already the project's four-layer model. Airbyte (or any importer) writes staging only; promotion is explicit and audited. |
| At least one real import path (n8n JSON / file upload) | A pilot with only hand-typed assets doesn't prove the ingestion spine. Glean/Onyx lead with connectors; you only need ONE working end-to-end. | MEDIUM | n8n workflow JSON export or file upload validates: staging → ACL snapshot → normalize → knowledge asset → candidate. Airbyte deferred until this works (per PROJECT.md decision). |
| Worker pipeline semantics: idempotent, retried, deduped, watermarked, observable job state | Import that duplicates or silently drops data destroys trust in week one. Admins need a "connector/import health" surface (Glean has it; Purview DSPM has it). | HIGH | Job table with state machine; incremental watermark per source; dedup by content hash; retry with backoff. Surface job status in admin UI. |
| Admin surface: user/role management, import scope approval, source health, audit viewer | Security reviews require demonstrable admin controls; Glean admins configure connectors and gate agent tools ("Always allow" vs "Needs approval"). | MEDIUM | Extends existing settings shell. Import scope approval before a source is enabled is an Enterprise Readiness gate. |
| Retention & deletion controls (policy per source scope; deletion actually deletes chunks/embeddings) | Vendor checklists demand retention policies, deletion guarantees, export formats. Purview applies retention to AI-referenced artifacts. | MEDIUM | Deleting a raw source must cascade to derived normalized records, embeddings, and index entries — orphaned embeddings are a leak vector. Legal-hold-style export of a scope is a plus. |
| Data classification labels (public/internal/confidential/restricted) honored by retrieval | Purview's model: labeled/sensitive content can be excluded from AI grounding. Enterprise Readiness lists classification as a gate. | MEDIUM | Label at knowledge-asset level minimum; retrieval filter = permission AND classification policy. Full PII auto-detection can be v1.x; manual labeling at ingestion scope is enough for pilot. |
| Permission-aware MCP tools with service identity + per-call audit | MCP consensus: OAuth-style delegation, least-privilege scope per tool, verify scope per tool call, audit who/what/when. Agent access is the product's stated future. | HIGH | Tools (`search_capability_assets`, `get_capability_asset`, etc.) must execute AS a mapped user identity, reuse the exact same pre-retrieval filter path as the web app, and emit audit events. Never a god-mode service account. |
| Ops baseline: backup/restore drill, health checks, monitoring/alerts, install runbook | On-prem buyers ask "what happens when it breaks" before "what does it do." Standard vendor-pilot checklist item. | MEDIUM | pg_dump/restore drill documented; Spring Actuator health; log/trace/alert story; migration rollback procedure. |
| Encryption in transit (TLS 1.2+) + secrets management + at-rest story | Universal checklist item (TLS, AES-256 at rest or documented equivalent via disk/DB encryption on-prem). | LOW | Mostly deployment/runbook work, but must be documented for the security review. Credentials for connectors encrypted at rest (Onyx sells this as a feature). |

### Differentiators (The Moat — Where OrgMemory Competes)

The claimed moat is the capability-asset lifecycle. Research confirms nobody in the comp set owns this combination: Glean/Onyx do permission-aware search but have no asset lifecycle; Langfuse/PromptLayer do prompt versioning but are developer tools with no org governance, ownership, or handover; Collibra/DataHub do governance workflows but for data tables, not AI capabilities.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Capability Candidate detection from Knowledge Assets (human-reviewed, never auto-published) | Turns ingested knowledge into reusable AI capability — the step no competitor has. Collibra's "certification" + Langfuse's "registry" fused. | HIGH | AI proposes; reviewer disposes. Candidate → review queue → Capability Asset. Detection quality can start simple (heuristics + LLM classification on knowledge assets). |
| Certification/trust signal on assets (approved = certified badge, staleness/review-due indicators) | Data catalogs prove certification badges drive reuse of trusted assets over random ones. Extends existing approval statuses into a visible trust system. | LOW | Add review-due dates and staleness detection to existing approval model. Cheap, high perceived governance value. |
| Handover packs (offboarding: generate transfer bundle of owned assets, usage context, backup-owner assignment) | The origin pain. No competitor addresses AI-capability offboarding at all. Prototype has transfer views; pilot makes them generate real artifacts. | MEDIUM | `generate_handover_pack` MCP tool + UI export. Depends on ownership/backup-owner enforcement already present. |
| Label-based prompt deployment (production/staging labels on asset versions, fetch-by-label API) | Langfuse's best pattern applied to governed org assets: teams consume "the approved production version" programmatically, admins control label movement (protected labels). | MEDIUM | Version history exists; add labels as pointers + protected-label rules tied to roles. Makes the registry consumable by tooling, not just humans. |
| Ownership-risk analytics (missing backup owners, orphaned assets on offboarding, unused approved assets) | "Missing backup owner is first-class risk" is a product principle no comp has. Governance analytics that map to a real HR event. | LOW | Queries over existing data; surface on dashboard. Strong demo material for design partners. |
| Permission-aware knowledge graph (edges filtered by viewer permissions) | Graph views leak by default (Enterprise Readiness names graph edges as a leak channel). A graph that provably respects ACLs is itself a trust differentiator. | MEDIUM | Reuse the same pre-filter path when deriving visible nodes/edges. Move graph derivation from `apps/api` into `core` while doing this (known debt). |
| Usage evidence tied to approval lifecycle (reuse by non-creators, per-department adoption, cited-in-answers counts) | Converts usage tracking into governance evidence: which approved assets earn their status. PromptLayer/Langfuse measure prompts; nobody measures organizational reuse. | LOW | Extends existing usage events with "used by non-creator" and "cited in AI answer" dimensions. |
| Explainable AI answer provenance (which assets, which permissions path, why you were allowed to see this) | Purview audits AI answers for admins; showing provenance to END USERS builds the employee trust the brief says is a contribution risk. | MEDIUM | Store chat + retrieval trace; render "answered from these 3 approved assets you have access to." |

### Anti-Features (Deliberately Do Not Build)

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| Passive capture of employee AI sessions (browser extension, chat-history scraping) | "Get all the knowledge automatically" | Surveillance perception kills contribution (brief's #1 contribution risk); no privacy/incentive model; poisons pilot trust permanently | Explicit submission + candidate detection from *approved* ingested sources only |
| Auto-publishing AI-detected assets | "Reduce reviewer burden" | Destroys the meaning of "approved"; one bad auto-published asset with leaked content ends the pilot | AI creates candidates only; human review is the product, not overhead |
| Embeddings/similarity as a permission boundary ("it won't retrieve it if it's not relevant") | Simpler pipeline; one index | Industry consensus is explicit: post-retrieval filtering leaks; embeddings encode content and can surface it via summaries/graph/analytics | Mandatory ACL pre-filter predicates in every query path (search, vector, chat, MCP, graph, export, analytics) |
| Broad connector catalog for the pilot (Slack + Teams + Drive + Notion + ...) | "More sources = more value"; Glean has 275 | Each connector multiplies ACL-mapping complexity; ingest-everything pilots stall in security review; Glean spent years here | One cheap importer (n8n JSON / file upload) first; Airbyte staging for ONE document source when the spine works |
| Cross-department analytics that expose individual usage ("who uses AI least") | Managers ask for it | Turns usage evidence into surveillance; violates "explainable to employees without feeling like surveillance" gate | Department/asset-level aggregates; individual data visible only to the individual |
| Full SSO matrix (SAML + SCIM + LDAP) for pilot | Enterprise RFP checklists list them | Weeks of work; a scoped 20–100 user pilot with one IdP needs OIDC only | OIDC now; document SAML/SCIM roadmap in the security-review packet |
| Chat over raw staged sources ("just let me ask the Slack dump") | Immediate perceived value | Raw sources are untrusted/unnormalized/ACL-unmapped; grounding AI on staging bypasses the whole trust model | Ask Memory grounds only on Knowledge Assets + approved Capability Assets that passed the staging boundary |
| God-mode MCP service account | Simplest integration | Any agent connected gets everything; violates least-privilege scope-per-tool consensus; un-auditable | Service identity + per-user delegation; MCP tools run through the same permission filter as the UI |

## Feature Dependencies

```
OIDC SSO + roles
    └──requires nothing; everything below requires it

Identity mapping (source principals → OrgMemory users)
    └──requires──> OIDC SSO (real user identities to map to)

Source ACL snapshot at ingestion
    └──requires──> Staging boundary (raw_source tables)
    └──requires──> Identity mapping design

Permission filtering before retrieval
    └──requires──> Source ACL snapshot + identity mapping
    └──requires──> Data classification labels (filter = ACL AND classification)

Ask Memory citations (permitted-only)          ──requires──> Permission filtering
Permission-aware knowledge graph               ──requires──> Permission filtering
Permission-aware MCP tools                     ──requires──> Permission filtering + OIDC (delegation) + Audit log
Export controls                                ──requires──> Permission filtering + Audit log

Real import path (n8n JSON / file upload)
    └──requires──> Staging boundary + Worker pipeline semantics

Capability Candidate detection
    └──requires──> Knowledge Assets existing (staging boundary + import path)
    └──feeds────> existing review queue (already built)

Retention/deletion controls
    └──requires──> Staging boundary (know what derived data to cascade-delete)

Audit log
    └──requires──> OIDC (real actor identity)
    └──enhances──> everything (every table-stakes feature emits events)

Label-based deployment    ──enhances──> existing version history
Handover packs            ──enhances──> existing transfer views + ownership model
Ownership-risk analytics  ──enhances──> existing dashboard

Clean tenant deploy ──conflicts──> seed data in Flyway V4–V7 (known debt; must split before pilot install)
```

### Dependency Notes

- **Permission filtering requires ACL snapshot + identity mapping:** you cannot filter on principals you never captured or cannot resolve to the logged-in user. These two designs (PROJECT.md's "undesigned hard parts" #1 and #3) block the entire trust layer — schedule them first.
- **MCP tools require the filter path, not a copy of it:** the single highest-risk divergence is MCP reimplementing retrieval with its own (weaker) checks. One retrieval service in `core`, consumed by api/mcp/worker.
- **Candidate detection requires real ingestion:** the moat feature is downstream of the boring spine. This is why the pilot roadmap must front-load identity/ACL/staging even though the differentiators are more exciting.
- **Audit log should land early, not last:** every subsequent feature emits events; retrofitting audit into finished features is rework. Cheap to create the table + event API in month 1 (matches Enterprise Readiness month-1 plan).
- **Seed-data-in-migrations conflicts with pilot install:** V4–V7 demo data blocks a clean tenant deploy; split into dev-only seeds before any customer install.

## MVP Definition

### Launch With (v1 — the scoped design-partner pilot)

- [ ] OIDC + admin/reviewer/contributor/viewer roles — every other trust feature needs real identity
- [ ] Immutable audit event table + emission from all mutating/reading flows — security review gate
- [ ] Staging schema (raw_source_object → normalized_record → knowledge_asset → capability_candidate) — the trust boundary
- [ ] Source ACL snapshot + identity mapping (designed, then built) — prerequisite for the product's core promise
- [ ] Permission pre-filter in one shared retrieval service (keyword + vector + chat + graph + export) — the core promise
- [ ] One import path end-to-end (n8n JSON or file upload) with idempotent/retryable worker jobs — proves the spine
- [ ] Ask Memory grounded on permitted Knowledge/Capability Assets with resolvable citations — the visible trust demo
- [ ] Admin surface: roles, import scope approval, job/source health, audit viewer — reviewer-facing proof
- [ ] Retention/deletion with cascade to derived data + embeddings — checklist gate
- [ ] Manual classification labels honored by retrieval — checklist gate (auto-PII detection deferred)
- [ ] Permission-aware MCP tools (search/get at minimum) with per-call audit — validates agent access safely
- [ ] Backup/restore drill + runbook + monitoring + ASVS/LLM-Top-10 self-review — on-prem ops gate
- [ ] Seed data split out of Flyway V4–V7 — clean tenant deploy

### Add After Validation (v1.x)

- [ ] Capability Candidate auto-detection from Knowledge Assets — once real knowledge assets exist in volume
- [ ] Label-based deployment (production/staging labels, protected labels, fetch-by-label API) — when a team wants programmatic consumption
- [ ] Handover pack generation (UI export + `generate_handover_pack` MCP tool) — when pilot hits its first real offboarding
- [ ] Ownership-risk + reuse-evidence analytics — when 4+ weeks of pilot usage data exists
- [ ] Answer provenance UI for end users — when employee-trust feedback warrants it
- [ ] One Airbyte-staged document source (SharePoint/Drive) — when the cheap importer has proven the spine
- [ ] PII auto-detection/redaction at normalization — when customer data scope requires it

### Future Consideration (v2+)

- [ ] SAML + SCIM — when a customer contract requires it (already Out of Scope)
- [ ] Broad connector catalog via Airbyte — post-pilot, per-source ACL mapping is the bottleneck, not the connector
- [ ] Asset marketplace / cross-tenant sharing — needs multi-tenant maturity
- [ ] Agent orchestration on top of capability assets — needs stable MCP + eval story
- [ ] Prompt evaluation/experiments (Langfuse-style) on asset versions — valuable but a different buyer motion

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| OIDC + roles | HIGH | MEDIUM | P1 |
| Permission pre-filter retrieval service | HIGH | HIGH | P1 |
| Source ACL snapshot + identity mapping | HIGH | HIGH | P1 |
| Immutable audit log | HIGH | MEDIUM | P1 |
| Staging boundary schema | HIGH | HIGH | P1 |
| One real import path + worker semantics | HIGH | MEDIUM | P1 |
| Citations (permitted-only) in Ask Memory | HIGH | MEDIUM | P1 |
| Admin surface (roles/scope/health/audit) | MEDIUM | MEDIUM | P1 |
| Retention/deletion cascade | MEDIUM | MEDIUM | P1 |
| Classification labels in retrieval | MEDIUM | MEDIUM | P1 |
| Permission-aware MCP tools | MEDIUM | HIGH | P1 (search/get) / P2 (rest) |
| Ops baseline (backup, monitoring, runbook) | MEDIUM | MEDIUM | P1 |
| Candidate detection | HIGH | HIGH | P2 |
| Handover packs | HIGH | MEDIUM | P2 |
| Label-based deployment | MEDIUM | MEDIUM | P2 |
| Ownership-risk analytics | MEDIUM | LOW | P2 |
| Permission-aware graph rework | MEDIUM | MEDIUM | P2 |
| Answer provenance UI | MEDIUM | MEDIUM | P3 |
| Airbyte document source | MEDIUM | HIGH | P3 |
| PII auto-detection | MEDIUM | HIGH | P3 |

**Priority key:** P1 = pilot fails security review or thesis without it · P2 = differentiator, add during pilot · P3 = post-validation

## Competitor Feature Analysis

| Feature | Glean | Onyx (Danswer) | Langfuse/PromptLayer | Collibra/DataHub | Purview + M365 Copilot | OrgMemory Approach |
|---------|-------|----------------|----------------------|------------------|------------------------|--------------------|
| Permission-aware retrieval | Core promise; connectors sync source ACL maps, pre-filter search | EE only: document permissions mirror source access | N/A (project-level RBAC only) | Access policies on catalog assets | Sensitivity labels enforced at grounding; DLP excludes labeled files | ACL snapshot at ingestion + mandatory pre-filter predicate in one shared retrieval service used by web/chat/MCP/export |
| Identity | Enterprise SSO, per-user results | OIDC/SAML/OAuth2 (EE) | Basic SSO | Enterprise IAM integration | Entra-native | OIDC first; SAML/SCIM post-pilot; documented lifecycle |
| Audit | Full observability: every query/answer/action | Query history + audit logs (EE) | Change history | Audit-ready workflow trails | Unified audit log incl. prompts/responses/referenced files | Immutable event table covering login→import→approval→use→export→MCP→AI answer; SIEM-exportable |
| Citations | Grounded answers cite internal docs | Cites source documents | N/A | N/A | Audits referenced files | Citations resolve only to permitted assets; citation click re-checks permission |
| Asset lifecycle (review→approve→certify→deprecate) | None (search product) | None | Version+label, light approval | Certification/stewardship workflows for DATA assets | Label/retain, not lifecycle | **The moat**: full lifecycle for AI capability assets with owner/backup-owner/handover — already prototyped |
| Versioning + deployment labels | N/A | N/A | Immutable versions + protected labels, fetch-by-label | Version history | N/A | Adopt Langfuse label pattern on top of governed approval statuses |
| Connectors | 275+ (moat) | 40+ | N/A | Many (metadata) | M365-native | Deliberately narrow: 1 importer → 1 Airbyte source; ACL mapping quality over breadth |
| Offboarding/handover | None | None | None | Steward reassignment only | None | Handover packs + backup-owner enforcement + ownership-risk analytics — unique |
| Agent/MCP access | Admin-gated agent tools (allow/approve) | MCP-compatible | SDK access | API | Copilot-native | Permission-scoped MCP tools with per-call audit, same filter path as UI |
| Admin governance surface | Connector + tool gating | Admin roles, config | Project settings | Policy hub, workflows | DSPM for AI posture dashboard | Import scope approval + source health + audit viewer + role management |

## Sources

- Glean: [glean.com](https://www.glean.com/), [connectors docs](https://docs.glean.com/connectors/about), [permissions structure blog](https://www.glean.com/blog/secure-generative-ai-for-the-enterprise-requires-the-right-permissions-structure), [permissions-aware AI security](https://www.glean.com/perspectives/security-permissions-aware-ai) — MEDIUM (official vendor docs via web search)
- Onyx/Danswer: [access controls docs](https://docs.onyx.app/security/architecture/access_controls), [onyx GitHub](https://github.com/onyx-dot-app/onyx) — MEDIUM
- Prompt management: [Langfuse version control](https://langfuse.com/docs/prompt-management/features/prompt-version-control), [Langfuse overview](https://langfuse.com/docs/prompt-management/overview), [PromptLayer comparison](https://www.promptlayer.com/glossary/langfuse-vs-promptlayer/) — MEDIUM
- Data catalogs: [Collibra data catalog](https://www.collibra.com/products/data-catalog), [Collibra governance overview](https://murdio.com/insights/collibra-data-governance/), [2026 catalog comparison](https://www.stackfyi.com/guides/data-catalog-tools-atlan-collibra-datahub-openmetadata-2026) — MEDIUM
- Purview/Copilot governance: [Purview for M365 Copilot](https://learn.microsoft.com/en-us/purview/ai-m365-copilot), [DLP for Copilot](https://learn.microsoft.com/en-us/purview/dlp-microsoft365-copilot-location-learn-about), [Copilot data protection architecture](https://learn.microsoft.com/en-us/microsoft-365/copilot/microsoft-365-copilot-architecture-data-protection-auditing) — MEDIUM (official Microsoft Learn)
- Security review checklists: [Drata SOC 2 checklist](https://drata.com/learn/soc-2/checklist), [SaaS vendor security checklist](https://www.alchemer.com/resources/blog/the-data-security-checklist-for-evaluating-saas-vendors/), [enterprise AI platform RFP](https://www.truefoundry.com/blog/enterprise-ai-platform-rfp-questions-vendor-evaluation) — MEDIUM (cross-checked)
- Permission-aware RAG: [RheinInsights permission-based RAG](https://www.rheininsights.com/blog/en/Permission-Based+Retrieval+Augmented+Generation+RAG.php), [Databricks ACL + metadata filtering](https://community.databricks.com/t5/technical-blog/mastering-rag-chatbot-security-acl-and-metadata-filtering-with/ba-p/101946), [permission-aware retrieval in the vector layer](https://tianpan.co/blog/2026-05-04-permission-aware-retrieval-enterprise-rag-access-control), [Paragon RAG permissions guide](https://www.useparagon.com/learn/permissions-access-control-for-production-rag-apps/) — MEDIUM (strong cross-source consensus)
- MCP authorization: [MCP authorization tutorial](https://modelcontextprotocol.io/docs/tutorials/security/authorization), [Enterprise-Managed Authorization](https://blog.modelcontextprotocol.io/posts/enterprise-managed-auth/), [Red Hat MCP security](https://www.redhat.com/en/blog/mcp-security-implementing-robust-authentication-and-authorization) — MEDIUM (official spec docs)
- Internal: `D:\OrgMemory\docs\PRODUCT_BRIEF.md`, `D:\OrgMemory\docs\ENTERPRISE_READINESS.md`, `D:\OrgMemory\.planning\PROJECT.md` — HIGH (product source of truth)

---
*Feature research for: Enterprise AI Capability Registry pilot*
*Researched: 2026-07-06*
