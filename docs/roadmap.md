# OrgMemory Roadmap

Statuses use `shipped`, `active`, `next`, or `later`. Implementation detail
belongs in one active increment.

## Shipped Foundation

- OIDC issuer/subject identity linking with server-derived current actor, and
  invitation-gated provisioning that writes the binding on a first sign-in
  without letting the address become the identity.
- An administration surface that reads effective permissions, explains a decision
  as its decisive derivation, distinguishes an unanswered check from a refusal,
  and confines administrative tuple writes to organizations and roles.
- Canonical source ledger with stable Knowledge Asset identities, immutable
  versions, append-only evidence links, and monotonically increasing source
  revisions.
- Sealed ACL evidence, rotating current head, fail-closed SQL prefilter, Java
  recheck, generic denied resource `404`, and append-only retrieval audit.
- Knowledge Space-targeted upload with OpenFGA `can_create_asset` pre-write
  authorization and durable Space/owner publication tuples.
- Deployable API and worker runtimes plus an authenticated stateless MCP server
  exposing the same permission-aware knowledge search boundary.
- Northstar-style repository harness and current dependency baseline.
- Exact OIDC provider logout, dev-only Swagger, production configuration
  fail-fast guards, and explicit issuer/subject identity binding.
- A versioned connector staging contract with a fixture-driven Slack crawl that
  converges membership through sealed generations.
- An administration surface over the identity ledger: users with their sign-in
  linkage, observed source principals with confirm/revoke, read-only sealed
  source-group membership, and a per-connection identity trust decision.
- Connections configured from the browser rather than from environment
  variables: an encrypted write-only credential, a source catalogue showing what
  this deployment can ingest, one endpoint per operation rather than per source,
  and a per-connection page reporting what each crawl actually did.
- Two source adapters — Slack and Google Drive — proving the connector shape
  holds: an adapter contributes a profile, a batch source and a credential probe,
  and nothing in `core`, the API or the schema learns its name.
- Drive crawl correctness found by review rather than by failure: shared-drive
  sharing resolved through `permissionIds` instead of sealing an ACL that grants
  nobody, a crawl cursor that names its grants rather than counting them, folder
  scope that means the subtree, Google's own incomplete-search flag honoured, and
  bounded retry and response size.
- A framework-neutral secure GraphRAG kernel/testkit and a versioned Spring AI
  structured extraction adapter with deterministic, network-free tests.
- A secure PostgreSQL GraphRAG projection with evidence-level ACL/provenance,
  pgvector entity/relation indexes, Apache AGE topology candidates, bounded
  recursive fallback, atomic revision replacement, and bounded batches.
- Independent publication transactions plus worker reconciliation for retry,
  obsolete OpenFGA model repair, and managed orphan-tuple cleanup.
- The twelve-PR LightRAG `v1.5.4` semantic-port program: parsing, chunking,
  multimodal extraction, indexing, lifecycle, every query mode, PostgreSQL,
  OpenSearch and Neo4j adapters, secure Assistant/MCP delivery, server-declared
  citations, the permission-aware graph explorer, evaluation harness, and
  OpenTelemetry-compatible events. Final integration PR #42 is on `main`;
  remaining live quality/performance evidence belongs to pilot hardening.
- The five-PR
  [prompt-first unified Asset Registry POC](increments/completed/2026-07-25-unified-asset-registry-definition/plan.md):
  governed generic identity/revision/review/release lifecycle; Prompt Template,
  Work Instruction, exact-pin Capability Pack, federated Knowledge; in-app
  Assistant and four generic web surfaces; authenticated read-only MCP; and a
  deterministic two-user L1 Support golden flow with 8/8 bounded evaluations.
- A shared web page system derived from the local Onyx layout contracts:
  centralized width/scroll variants, responsive split and canvas layouts,
  conditional breadcrumbs, common filter/empty/content patterns, and a
  TanStack Table-backed semantic table wrapper.

## Active Delivery

- [Reproducible demo bootstrap](increments/active/2026-07-22-reproducible-demo-bootstrap/plan.md):
  import the synthetic document manifest through the public ingestion API,
  derive its declared access relationships, and run the permission evaluation
  suite against the indexed documents.
- [Slack connector live proof](increments/active/2026-07-23-slack-connector-live/plan.md):
  run the already-tested adapter against a real workspace and prove that the
  next crawl closes access after membership removal.
- [Production CI/CD and ZM runtime](increments/active/2026-07-25-production-cicd-zm/plan.md):
  PR #44 is merged and its repository/CI scope is shipped; the increment remains
  active until the guarded shared-PostgreSQL cutover and live runtime gates pass.

## Next — Execution Order

1. Complete the guarded ZM database cutover, runtime health, browser login,
   upload, GraphRAG, Assistant/citation, backup/rollback, and
   resource-observation gates. This first requires administrator access to
   disable the obsolete Zero Mail runner and a bounded writer-stop window.
2. Complete the reproducible demo's real ingestion and permission-evaluation
   path. This creates the repeatable dataset needed for later quality and load
   comparisons.
3. Run the Slack live proof. Keep live credentials outside the repository and
   capture only the reusable runbook and redacted evidence.
4. Finish what the admin permission surface left open: reachable containers
   with their ACL authority, generation and capture time; a permission audit
   event per role mutation; resolved names and a resource picker instead of
   pasted UUIDs; and relabelling `app_users.role`, which still reads as a grant
   while granting nothing. The gaps are listed in
   [the completed increment](increments/completed/2026-07-24-admin-permission-surface/plan.md).
5. Give a Knowledge Space a lifecycle. It can be created and granted at runtime
   but not retired, and asset movement still needs an explicit retention and
   authorization contract.
## Pilot Hardening

- S3-compatible production blobs, malware/DLP integration, retention/deletion.
- Backup/restore drill, monitoring, tracing, alerts, and incident runbook.
- Threat model, ASVS/LLM review, load and tenant-isolation tests.
- First gate: one tenant, one role Pack, two users with different permissions,
  one realistic task, explicit data-retention policy, and rollback plan. Expand
  to 20-100 users only after this gate passes.

## Engineering Backlog

- Refactor oversized Spring Modulith packages into cohesive internal
  subpackages while preserving each logical module and its public named
  interface. Choose package boundaries from actual responsibilities
  (application use cases, domain model, inbound API, outbound infrastructure),
  document allowed dependencies, and keep Modulith verification in CI; do not
  create one Gradle module or one top-level Modulith module per class or asset
  profile.
- Add Storybook only when the reusable component catalog is large enough to
  justify a second preview/build surface. Vitest and Testing Library are now
  installed for focused shared-component contracts; grow that suite only where
  a stable behavior has meaningful fan-out or regression risk.
- Add authoring interaction libraries only with their owning workflows:
  `dnd-kit` for ordered Pack/Workflow/Skill composition, `react-dropzone` for a
  measured multi-file ingestion queue, and `date-fns`/`react-day-picker` for SOP
  effectivity, release validity, ownership expiry, or review scheduling.
- Keep simple UI transitions in CSS. Introduce Motion only for a concrete
  presence, interruptible layout, or gesture requirement; load features lazily
  and honor the user's reduced-motion preference from the application root.
- Decide frontend telemetry as a governed product capability before adding
  Sentry or product analytics: define PII redaction, prompt/content exclusion,
  tenant boundaries, data residency, retention, and administrator controls.
- Add document preview and arbitrary rich-content rendering only behind an
  explicit untrusted-content contract. Evaluate `docx-preview` for source
  inspection and use a maintained HTML sanitizer when rendered content can
  contain HTML; never treat preview output as trusted application markup.

## Later, Only With Evidence

Screenpipe capture, controlled SOP effectivity, Skill installation, executable
Workflow/Agent/Tool packages, Airflow, Kafka, SCIM, more providers/connectors,
mutation MCP tools, and multi-agent orchestration require measured need or the
completed browser-native Asset POC. OpenSearch and Neo4j adapters are already in
the full LightRAG port program; production backend selection is still
evidence-driven. Search and graph remain rebuildable projections behind stable
ledger/permission contracts.
