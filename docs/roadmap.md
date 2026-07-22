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

## Active — Secure Hybrid Retrieval

See [active plan](increments/active/2026-07-22-secure-hybrid-retrieval/plan.md).

Outcome: one permission-aware search path uses a pinned OpenFGA model,
tenant/ACL/lifecycle SQL prefiltering, PostgreSQL FTS + pgvector ranking,
BatchCheck, citation-time canonical recheck, and append-only audit evidence.

## Next — Shared Agent Tools And Secure Graph

- Converge persistent Capability Asset tuples and remove its list-path N+1 with
  ListObjects/BatchCheck, pagination, and aggregate usage counts.
- Publish proven read-only in-app tools through MCP with service identity/audit.
- Add graph core/testkit and PostgreSQL/Spring AI adapters with evidence-level
  contributions and permission-scoped local/global/mix retrieval.
- Detect Capability Candidates from approved evidence and connect the existing
  review/publish/reuse lifecycle.
- Add one approved connector staging contract and reconciliation loop.

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
