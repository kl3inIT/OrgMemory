# OrgMemory Roadmap

This file tracks delivery status, execution order, and future backlog. Product
intent and increment descriptions belong in [vision](vision.md); current
behavior belongs in [architecture](../ARCHITECTURE.md) and domain specs.
Subtasks belong only in the linked active plan.

Statuses are `shipped`, `active`, `next`, or `later`.

## Shipped

| Increment or program | Status | Historical evidence |
| --- | --- | --- |
| Repository operating-model refresh | shipped | [completed verification](increments/completed/2026-07-29-repository-operating-model-refresh/verification.md) |
| Repository harness and secure knowledge foundation | shipped | [completed increments](increments/completed/README.md) |
| Browser authentication, AI gateway, Assistant, secure hybrid retrieval, and MCP | shipped | [completed increments](increments/completed/README.md) |
| Generic source contract plus Slack, Google Drive, and GitHub adapters | shipped | [completed increments](increments/completed/README.md) |
| Source authorization core V2 and GitHub authorization synchronization | shipped | [source core](increments/completed/2026-07-28-source-authorization-core-v2/plan.md), [sync correctness](increments/completed/2026-07-28-source-authorization-sync-correctness/plan.md), [GitHub connector](increments/completed/2026-07-28-github-authorization-connector/plan.md) |
| Secure Java LightRAG semantic port and replaceable storage adapters | shipped | [LightRAG parity history](increments/completed/2026-07-23-full-lightrag-semantic-port/plan.md) |
| Governed Asset Registry, shared page system, and catalog UX | shipped | [Asset Registry](increments/completed/2026-07-25-unified-asset-registry-definition/plan.md), [catalog UX](increments/completed/2026-07-27-asset-catalog-ux/plan.md) |
| Governed Skill packaging, distribution, authoring, publication, and handoff | shipped | [package foundation](increments/completed/2026-07-27-skill-registry-package-foundation/plan.md), [distribution](increments/completed/2026-07-27-skill-registry-distribution/plan.md), [publication](increments/completed/2026-07-27-skill-authoring-publication/plan.md), [handoff](increments/completed/2026-07-27-skill-governance-handoff/plan.md) |
| Identity tenant hardening | shipped | [completed plan](increments/completed/2026-07-27-identity-tenant-hardening/plan.md) |

The table is a delivery index, not a second description of current behavior.

## Active

| Increment | Status | Remaining gate |
| --- | --- | --- |
| [Production CI/CD and ZM runtime](increments/active/2026-07-25-production-cicd-zm/plan.md) | active | shared-PostgreSQL cutover, restore proof, end-to-end runtime and rollback gates |
| [Reproducible demo bootstrap](increments/active/2026-07-22-reproducible-demo-bootstrap/plan.md) | active | public ingestion and permission-evaluation run |
| [Slack connector live proof](increments/active/2026-07-23-slack-connector-live/plan.md) | active | live workspace crawl and next-crawl revocation |
| [Asset projection generation repair](increments/active/2026-07-25-asset-projection-generation/plan.md) | active | production Assistant/citation/permission verification |
| [LightRAG multi-space query latency](increments/active/2026-07-28-lightrag-query-latency/plan.md) | active | deploy merged repair and capture production before/after timings |
| [MCP search reliability](increments/active/2026-07-28-mcp-search-reliability/plan.md) | active | deploy merged timeout repair and prove the production MCP call |
| [SCIM provisioning foundation](increments/active/2026-07-27-scim-provisioning-foundation/plan.md) | active | previous-binary/restore rehearsals and two-organization negative evidence |
| [Public documentation portal](increments/active/2026-07-28-public-docs-portal/plan.md) | active | implement the independent Fumadocs application and publication boundary |

The other SCIM directories under `increments/active/` are dependency-ordered
future designs inside the active native identity program. They do not become
implementation-active until their predecessor exit gates pass.

## Next — Execution Order

1. Run a separate repository-layout increment that creates the root pnpm
   workspace, moves `web` to `apps/web`, and establishes `apps/docs` without
   mixing the move with public-content authoring.
2. Continue the Fumadocs portal plan against the normalized `apps/docs` path.
3. Complete the guarded ZM database cutover, runtime health, browser login,
   upload, GraphRAG, Assistant/citation, restore, rollback, and resource gates.
4. Complete the reproducible demo's real ingestion and permission-evaluation
   path.
5. Run the Slack live proof with credentials outside the repository and retain
   only redacted evidence.
6. Close the production proofs for asset projection generation, LightRAG
   latency, and MCP search reliability.
7. Continue the dependency-ordered native identity program: provisioning
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

- Finish the admin permission surface: reachable containers with ACL authority,
  generation, and capture time; a permission audit event per role mutation;
  resolved names and a resource picker instead of pasted UUIDs; and relabel
  `app_users.role`, which is a business/classification attribute rather than an
  OpenFGA grant.
- Give a Knowledge Space a lifecycle. It can be created and granted at runtime
  but not retired, and asset movement needs an explicit retention and
  authorization contract.
- Audit outbound adapters that call stable remote APIs through direct Spring
  `RestClient`, and migrate selectively to typed `@HttpExchange` HTTP Service
  interfaces backed by the existing `RestClient` configuration. Retain direct
  `RestClient` for streaming/uploads, runtime-dynamic requests, and
  provider-specific retry/error flows.
- Refactor oversized Spring Modulith packages into cohesive internal
  subpackages while preserving each logical module and public named interface.
  Choose boundaries from actual responsibilities; do not create one Gradle
  module or top-level Modulith module per class or Asset profile.
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
