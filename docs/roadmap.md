# OrgMemory Roadmap

This file tracks delivery status, execution order, and future backlog. Product
intent and increment descriptions belong in [vision](vision.md); current
behavior belongs in [architecture](../ARCHITECTURE.md) and domain specs.
Subtasks belong only in the linked active plan.

Statuses are `shipped`, `active`, `next`, or `later`.

## Shipped

| Increment or program | Status | Historical evidence |
| --- | --- | --- |
| Polyglot apps workspace and Fumadocs foundation | shipped | [completed verification](increments/completed/2026-07-29-polyglot-apps-workspace/verification.md) |
| Repository operating-model refresh | shipped | [completed verification](increments/completed/2026-07-29-repository-operating-model-refresh/verification.md) |
| Repository harness and secure knowledge foundation | shipped | [completed increments](increments/completed/README.md) |
| Browser authentication, AI gateway, Assistant, secure hybrid retrieval, and MCP | shipped | [completed increments](increments/completed/README.md) |
| Generic source contract plus Slack, Google Drive, and GitHub adapters | shipped | [completed increments](increments/completed/README.md) |
| Source authorization core V2 and GitHub authorization synchronization | shipped | [source core](increments/completed/2026-07-28-source-authorization-core-v2/plan.md), [sync correctness](increments/completed/2026-07-28-source-authorization-sync-correctness/plan.md), [GitHub connector](increments/completed/2026-07-28-github-authorization-connector/plan.md) |
| Secure Java LightRAG semantic port and replaceable storage adapters | shipped | [LightRAG parity history](increments/completed/2026-07-23-full-lightrag-semantic-port/plan.md) |
| Governed Asset Registry, shared page system, and catalog UX | shipped | [Asset Registry](increments/completed/2026-07-25-unified-asset-registry-definition/plan.md), [catalog UX](increments/completed/2026-07-27-asset-catalog-ux/plan.md) |
| Assets catalog type projection | shipped | [completed verification](increments/completed/2026-07-31-assets-catalog-skill-projection/verification.md) |
| Asset ownership navigation | shipped | [completed verification](increments/completed/2026-07-31-asset-ownership-navigation/verification.md) |
| Asset catalog layout balance | shipped | [completed verification](increments/completed/2026-07-31-asset-catalog-layout-balance/verification.md) |
| Governed Skill packaging, distribution, authoring, publication, and handoff | shipped | [package foundation](increments/completed/2026-07-27-skill-registry-package-foundation/plan.md), [distribution](increments/completed/2026-07-27-skill-registry-distribution/plan.md), [publication](increments/completed/2026-07-27-skill-authoring-publication/plan.md), [handoff](increments/completed/2026-07-27-skill-governance-handoff/plan.md) |
| Direct Skill sharing | shipped | [completed verification](increments/completed/2026-07-31-skill-direct-sharing/verification.md) |
| Browser Skill authoring | shipped | [completed verification](increments/completed/2026-08-01-browser-skill-authoring/verification.md) |
| Agent-assisted Skill handoff | shipped | [completed verification](increments/completed/2026-08-01-skill-agent-handoff/verification.md) |
| Skill consumer compatibility | shipped | [completed verification](increments/completed/2026-08-01-skill-consumer-compatibility/verification.md) |
| Identity tenant hardening | shipped | [completed plan](increments/completed/2026-07-27-identity-tenant-hardening/plan.md) |
| Multi-provider model control plane | shipped | [completed verification](increments/completed/2026-07-29-multi-provider-model-control-plane/verification.md) |
| AI model gateway provider boundary | shipped | [completed verification](increments/completed/2026-07-29-ai-model-gateway-boundary/verification.md) |
| Independent bilingual public documentation portal | shipped | [completed verification](increments/completed/2026-07-28-public-docs-portal/verification.md) |
| Automatic public docs delivery | shipped | [completed verification](increments/completed/2026-07-30-automatic-docs-delivery/verification.md) |
| Public docs hydration stability | shipped | [completed verification](increments/completed/2026-08-01-docs-hydration/verification.md) |
| Product release management | shipped | [completed verification](increments/completed/2026-07-31-product-release-management/verification.md) |
| Observability platform: payload boundary, collector stack, and dashboards | shipped | [completed verification](increments/completed/2026-07-30-observability-platform/verification.md) |
| Observability pipeline and payload boundary | shipped | [completed verification](increments/completed/2026-07-29-observability-pipeline/verification.md) |
| Authorization consolidation: typed batch-recheck policy and served governance affordances | shipped | [completed plan](increments/completed/2026-08-01-authz-consolidation/plan.md), [decision 0023](decisions/0023-batch-recheck-policy-and-served-affordances.md) |
| Ingestion throughput: batched staging writes and bounded-burst schedulers | shipped | [completed plan](increments/completed/2026-08-01-ingestion-throughput/plan.md), [decision 0024](decisions/0024-bounded-burst-scheduling-over-drain.md) |
| Copy-forward coordination: durable ownership and bounded streaming | shipped | [completed plan](increments/completed/2026-08-01-copyforward-coordinator/plan.md), [decision 0025](decisions/0025-copyforward-durable-ownership.md) |
| Connector polling coordination: shared lifecycle, client rotation, and failure admission | shipped | [completed plan](increments/completed/2026-08-01-connector-polling-driver/plan.md), [decision 0026](decisions/0026-connector-polling-lifecycle.md) |
| Authorized graph traversal coordinator | shipped | [completed verification](increments/completed/2026-08-01-authorized-graph-traversal/verification.md), [decision 0027](decisions/0027-core-owned-authorized-graph-traversal.md) |
| Durable cross-store publication lifecycle | shipped | [completed plan](increments/completed/2026-08-02-publication-lifecycle-coordinator/plan.md), [decision 0028](decisions/0028-durable-cross-store-publication-permits.md) |
| Typed Knowledge Space audiences | shipped | [completed verification](increments/completed/2026-08-02-knowledge-space-audience-modes/verification.md), [decision 0029](decisions/0029-typed-knowledge-space-audiences.md) |
| Explicit Apache AGE graph backend | shipped | [completed verification](increments/completed/2026-08-02-apache-age-graph-backend/verification.md), [decision 0030](decisions/0030-explicit-apache-age-topology-backend.md) |
| MCP search reliability | shipped | [completed verification](increments/completed/2026-07-28-mcp-search-reliability/verification.md) |
| Document View and Delete | shipped | [completed plan](increments/completed/2026-08-02-document-view-delete/plan.md); no Reindex action |
| Role-specific RAG routing and evaluated production model split | shipped | [routing verification](increments/completed/2026-08-02-rag-workload-routing-luna/verification.md), [Graph route verification](increments/completed/2026-08-02-graph-extraction-model-route/verification.md) |

The table is a delivery index, not a second description of current behavior.

## Active

| Increment | Status | Remaining gate |
| --- | --- | --- |
| [Assistant Skill activity receipt](increments/active/2026-08-06-assistant-skill-activity-receipt/plan.md) | active | close the first-token handoff gap and expose only a sanitized successful Skill activation receipt |
| [Agentic Skill beta](increments/completed/2026-08-05-agentic-skill-beta/verification.md) | shipped | delivered actor-scoped progressive Skill disclosure, a bounded read-only Assistant tool loop, and truthful Skill activity without server-side package execution |
| [Knowledge workspace and document reader](increments/completed/2026-08-05-knowledge-workspace-document-reader/verification.md) | shipped | completed the governed right-side reader, safe Markdown presentation, truthful access copy, and cross-format browser coverage |
| [Knowledge operations and graph inspector](increments/completed/2026-08-05-knowledge-operations-graph-inspector/verification.md) | shipped | completed desktop document operations, safe terminal-failure remediation, and the graph entity/evidence inspector; retry remains fenced backlog work |
| [Unified governed document viewer](increments/completed/2026-08-05-unified-governed-document-viewer/verification.md) | shipped | converged Knowledge documents and Assistant citations on one centered, permission-verified reading surface |
| [Knowledge Graph responsive header](increments/completed/2026-08-05-knowledge-graph-responsive-layout/verification.md) | shipped | verified the shared title/action shrink contract at the reported production viewport with unit and browser regression coverage |
| [Assistant evidence continuity and live activity](increments/completed/2026-08-04-assistant-citation-evidence-continuity/plan.md) | shipped | delivered direct evidence, safe Markdown, bounded replay hydration, and truthful pre-token retrieval/generation activity |
| [Assistant answer behavior](increments/completed/2026-08-05-assistant-answer-behavior/results.md) | shipped | production sweep completed 50/50 turns, kept the documented P035 fixture mismatch explicit, removed pipeline voice from Deny answers, and met the 41/43 citation threshold |
| [Multi-snapshot query prototype and evaluation harness](increments/active/2026-08-05-multi-snapshot-query-prototype/plan.md) | active | prove or falsify ADR 0020 gates 2-3: compound-query p95 <= 500 ms at 1x/10x/100x with shadow equivalence, and bypass recall@40 parity |
| [Retrieval admission control and pool right-sizing](increments/active/2026-08-05-retrieval-admission-phase1/plan.md) | active | ADR 0020 Phase 1: fair 4-permit admission, pool 8/8, topK 40, TTFT attribution; production before/after window also closes the LightRAG-latency live proof |
| [Assistant composer and conversation model picker](increments/completed/2026-08-04-assistant-composer-model-picker/plan.md) | shipped | delivered administrator-bound model authority, conversation selection, and composer polish |
| [Assistant interaction foundation](increments/completed/2026-08-04-assistant-interaction-foundation/plan.md) | shipped | delivered server-owned starters, scoped drafts, answer feedback, fresh retry, and interaction recovery |
| [Apache AGE published-batch backfill](increments/completed/2026-08-03-apache-age-published-batch-backfill/verification.md) | shipped | challenged one-shot repair, 49/49 production reconciliation, Graph explorer, and cited Assistant proof |
| [Effective access inspector](increments/active/2026-08-02-effective-access-inspector/plan.md) | active | complete browser interaction and reference-image comparison |
| [Skill CLI distribution and local lifecycle](increments/active/2026-08-01-skill-cli-distribution-lifecycle/plan.md) | active | merge the verified lifecycle and publication boundary, bootstrap the first npm version under owner control, then activate the pinned `npx` handoff in a second PR |
| [Spring Modulith package refactor](increments/active/2026-07-31-spring-modulith-package-refactor/plan.md) | active | move coherent Knowledge and Asset Registry slices in code PRs below 100 files, repair their edges, then close every nested module |
| [Shared ZM team development](increments/active/2026-07-31-shared-zm-team-development/plan.md) | active | guard one shared non-production dataset with protected-change detection, exclusive worker/maintenance leases, loopback local development, and deployment verification |
| [OpenFGA model rollout repair](increments/active/2026-07-29-openfga-model-rollout/plan.md) | active | version and pin the repository model during deployment, then verify the affected admin screens |
| [Production CI/CD and ZM runtime](increments/active/2026-07-25-production-cicd-zm/plan.md) | active | shared-PostgreSQL cutover, restore proof, end-to-end runtime and rollback gates |
| [Reproducible demo bootstrap](increments/active/2026-07-22-reproducible-demo-bootstrap/plan.md) | active | public ingestion and permission-evaluation run |
| [Slack connector live proof](increments/active/2026-07-23-slack-connector-live/plan.md) | active | live workspace crawl and next-crawl revocation |
| [Asset projection generation repair](increments/active/2026-07-25-asset-projection-generation/plan.md) | active | production Assistant/citation/permission verification |
| [LightRAG multi-space query latency](increments/active/2026-07-28-lightrag-query-latency/plan.md) | active | deploy merged repair and capture production before/after timings |
| [SCIM provisioning foundation](increments/active/2026-07-27-scim-provisioning-foundation/plan.md) | active | previous-binary/restore rehearsals and two-organization negative evidence |
| [Public docs co-authoring and information architecture](increments/active/2026-07-29-public-docs-coauthoring/plan.md) | active | co-author What is OrgMemory? with the owner through context, outline, English review, teach-back, and Vietnamese review |

The other SCIM directories under `increments/active/` are dependency-ordered
future designs inside the active native identity program. They do not become
implementation-active until their predecessor exit gates pass.

## Next — Execution Order

1. Begin the owner-led public-docs queue with the context and outline
   checkpoints for What is OrgMemory?.
2. Complete the guarded ZM database cutover, runtime health, browser login,
   upload, GraphRAG, Assistant/citation, restore, rollback, and resource gates.
3. Complete the reproducible demo's real ingestion and permission-evaluation
   path.
4. Run the Slack live proof with credentials outside the repository and retain
   only redacted evidence.
5. Close the production proofs for asset projection generation and LightRAG
   latency.
6. Continue the dependency-ordered native identity program: provisioning
   foundation, Users private beta, inert Directory Groups, optional explicit
   authorization mapping, then vendor/operations certification.

## Pilot Hardening

- S3-compatible production blobs, malware/DLP integration, retention/deletion.
- Backup/restore drill, monitoring, tracing, alerts, and incident runbook.
- Threat model, ASVS/LLM review, load and tenant-isolation tests.
- First gate: one tenant, one role Pack, two users with different permissions,
  one realistic task, explicit data-retention policy, and rollback plan. Expand
  to 20-100 users only after this gate passes.

## Engineering Backlog

- Fence Source Ingestion with a never-reused claim epoch and an exact durable
  Asset-publication permit, then prove manifest-pinned recovery before exposing
  a manual FAILED retry. The rejected proposal and required test matrix are in
  the [Knowledge operations challenge verdict](increments/completed/2026-08-05-knowledge-operations-graph-inspector/challenge-verdict.md).
- Finish the remaining admin permission surface after the active effective
  access inspector: reachable containers with ACL authority, generation, and
  capture time; a permission audit event per role mutation; and a governed
  resource picker whose metadata visibility contract is explicit.
- Give a Knowledge Space a lifecycle. It can be created and granted at runtime
  but not retired, and asset movement needs an explicit retention and
  authorization contract.
- Audit outbound adapters that call stable remote APIs through direct Spring
  `RestClient`, and migrate selectively to typed `@HttpExchange` HTTP Service
  interfaces backed by the existing `RestClient` configuration. Retain direct
  `RestClient` for streaming/uploads, runtime-dynamic requests, and
  provider-specific retry/error flows.
- Add Storybook only when the reusable component catalog justifies a second
  preview/build surface.
- Add authoring interaction libraries only with their owning workflows:
  `dnd-kit` for ordered composition, `react-dropzone` for a measured multi-file
  queue, and `date-fns`/`react-day-picker` for lifecycle scheduling.
- Keep simple UI transitions in CSS. Introduce Motion only for a concrete
  presence, interruptible layout, or gesture requirement; load it lazily and
  honor reduced motion.
- Decide frontend telemetry as a governed product capability before adding
  Sentry or product analytics: PII redaction, prompt/content exclusion, tenant
  boundaries, residency, retention, and administrator controls are required.
- Add document preview and arbitrary rich-content rendering only behind an
  explicit untrusted-content contract and maintained HTML sanitization.

## Later, Only With Evidence

Screenpipe capture, controlled SOP effectivity, executable
Workflow/Agent/Tool packages, Airflow, Kafka, more providers/connectors,
mutation MCP tools, and multi-agent orchestration require measured need or a
completed browser-native Asset proof. SCIM Bulk/sort/ETag, nested directory
groups, multi-organization membership for one OIDC subject, and Keycloak preview
SCIM as a production adapter remain evidence-gated follow-ons. Production
OpenSearch/Neo4j selection remains evidence-driven; search and graph remain
rebuildable projections behind the canonical ledger and permission contracts.
