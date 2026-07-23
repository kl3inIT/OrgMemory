# OrgMemory Roadmap

Statuses use `shipped`, `active`, `next`, or `later`. Implementation detail
belongs in one active increment.

## Shipped Foundation

- Capability Asset registry lifecycle, ownership, versions, approval, and usage.
- OIDC issuer/subject identity linking with server-derived current actor.
- Service/test-backed one-leaf knowledge ingestion persistence.
- Sealed ACL evidence, rotating current head, fail-closed SQL prefilter, Java
  recheck, generic denied resource `404`, and append-only retrieval audit.
- Knowledge Space-targeted upload with OpenFGA `can_create_asset` pre-write
  authorization and atomic Space/owner publication tuples.
- API, worker, and MCP deployable scaffolds.
- Northstar-style repository harness and current dependency baseline.
- Exact OIDC provider logout, dev-only Swagger, production configuration
  fail-fast guards, and set-based Capability Asset list authorization/counts.
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

## Active — Secure Hybrid Retrieval

See [active plan](increments/active/2026-07-22-secure-hybrid-retrieval/plan.md).

Outcome: one permission-aware search path uses a pinned OpenFGA model,
tenant/ACL/lifecycle SQL prefiltering, PostgreSQL FTS + pgvector ranking,
BatchCheck, citation-time canonical recheck, and append-only audit evidence.

## Next — Shared Agent Tools And Secure Graph

- Converge persistent Capability Asset tuples and add server-side pagination to
  the now set-authorized Capability Asset registry.
- Publish proven read-only in-app tools through MCP with service identity/audit.
- Add graph core/testkit and PostgreSQL/Spring AI adapters with evidence-level
  contributions and permission-scoped local/global/mix retrieval.
- Detect Capability Candidates from approved evidence and connect the existing
  review/publish/reuse lifecycle.
- Run the Slack adapter against a real workspace. The adapter, its administration
  and its reporting are built and proved against recorded responses; nothing has
  yet crawled a workspace that exists.

## Pilot Hardening

- S3-compatible production blobs, malware/DLP integration, retention/deletion.
- Backup/restore drill, monitoring, tracing, alerts, and incident runbook.
- Threat model, ASVS/LLM review, load and tenant-isolation tests.
- First gate: one or two test machines, one repeated workflow, explicit privacy
  filters, and rollback plan. Expand to 20-100 users only after this gate passes.

## Later, Only With Evidence

Neo4j, OpenSearch, Airflow, Kafka, SCIM, more providers/connectors, mutation MCP
tools, and multi-agent orchestration require measured need. Search and graph
remain rebuildable projections behind stable ledger/permission contracts.
