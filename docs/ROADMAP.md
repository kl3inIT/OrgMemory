# OrgMemory Roadmap

## Current Product Prototype State

The current repo demonstrates a real registry prototype:

- Spring Boot API and PostgreSQL-backed assets
- Flyway seeded enterprise sample catalog
- create asset flow
- AI enrichment through Spring AI with local fallback
- review actions and approval events
- usage tracking
- asset detail route
- registry filtering/search
- Ask Memory chat
- knowledge graph visualization
- onboarding/offboarding walkthrough views
- dashboard/analytics/settings shell
- MCP app scaffold

This is enough to demonstrate the product thesis and start design-partner
conversation. It is not enough for real enterprise-wide data ingestion or a
50,000-person production rollout.

## Next Engineering Priority

The next serious step is the ingestion, knowledge, and governance spine:

1. `raw_source_object`
2. `normalized_record`
3. `knowledge_asset`
4. `capability_candidate`
5. source ACL snapshot
6. permission-aware retrieval
7. review-to-approved asset promotion
8. audit event table
9. small permission-aware MCP tool surface

This moves OrgMemory from registry prototype to enterprise pilot foundation.

Read `docs/ENTERPRISE_READINESS.md` before planning any deployment that touches
real customer data.

## Enterprise Pilot Definition

The first enterprise pilot should be narrow:

- 1 enterprise tenant
- 1-3 departments
- 20-100 active pilot users, even if the enterprise has 50,000 employees
- 2-3 source connectors/importers
- permission-aware retrieval
- Knowledge Assets and Capability Assets
- AI Capability Asset registry
- review and approval workflow
- Ask Memory
- Use Capability
- MCP/API access for approved agents
- audit log
- monitoring, backup, and incident response

Do not position production v1 as a full replacement for Glean, Microsoft,
Airbyte, Workato, n8n, Dify, or ServiceNow.

## Recommended First Sources

Pick three:

1. Google Drive/Docs, or SharePoint/OneDrive for Microsoft-first companies
2. Slack, or Teams for Microsoft-first companies
3. n8n workflow JSON import

Also keep manual submit and file/Markdown upload. User-controlled capture from
ChatGPT, Claude, Codex, Cursor, and other AI workspaces can come before passive
capture.

## Ingestion Operating Model

Use the hybrid rule:

```text
Automatic ingestion for enterprise sources.
User-controlled capture for personal AI work.
Human-approved publishing for organizational assets.
```

Scheduled ingestion:

- admin configures source, scope, schedule, and retention
- system parses, cleans, and detects candidates

Event-driven ingestion:

- webhook for updated docs/workflows
- marked Slack/Teams messages
- updated n8n/Dify workflow files

User-controlled capture:

- employee saves a useful prompt/workflow/session
- system enriches it and submits it for review

Auto-detect may create candidates. It must not auto-publish official assets.

## Four-Month Pilot Plan

### Month 1: Foundation

- stabilize auth/user/department model
- add enterprise identity integration path: OIDC first, SAML/SCIM later
- add tenant/org isolation assumptions explicitly
- harden asset CRUD and detail
- complete versioning behavior
- improve review workflow
- create production audit event table
- draft threat model and data classification policy
- improve test coverage
- prepare pilot deployment baseline

Exit: registry can run reliably for a small internal group without ingesting
broad enterprise data.

### Month 2: Ingestion And Candidates

- implement raw source object table
- implement Knowledge Asset table and detail surface
- implement one document import path
- implement one conversation import path
- implement n8n workflow JSON import
- implement AI candidate extraction
- implement duplicate detection basic
- store source ACL snapshots with every ingested object

Exit: raw sources can be cleaned into Knowledge Assets, and selected knowledge
can become candidate Capability Assets.

### Month 3: Permission And Memory

- implement source ACL snapshot
- implement permission-aware search
- implement pgvector or hybrid retrieval
- add citations/source references to Ask Memory
- enforce permission filtering before vector retrieval and answer generation
- store richer audit events
- connect Use Capability to input schema and output logging

Exit: users only see Knowledge Assets, Capability Assets, and source citations
they are allowed to access.

### Month 4: Agent Access And Pilot Hardening

- implement MCP tools:
  - `search_capability_assets`
  - `get_capability_asset`
  - `create_asset_candidate`
  - `record_asset_usage`
  - `generate_handover_pack`
- add API keys/service auth
- add backup and monitoring
- write pilot admin runbook
- run OWASP ASVS and OWASP LLM Top 10 review
- prepare anonymized seed data and customer-specific import scripts
- run restore drill and basic load test
- prepare user training and support workflow

Exit: deployable design-partner pilot.

## Cut From V1

Cut until the core loop is proven:

- full Airbyte connector catalog
- full Airflow pipeline
- Neo4j or dedicated graph database
- full SCIM
- passive browser extension capture
- marketplace
- advanced maturity score
- complex multi-agent orchestration
- benchmark engine
- billing

## Airbyte Deployment Note

Use Airbyte only when connector breadth becomes worth the operational cost.
For an early pilot with 2-3 sources, custom importers may be faster and easier to
control.

If Airbyte is used, keep it separate from the OrgMemory core and write to
PostgreSQL staging. Do not write directly to the main memory/vector layer.

Current official Airbyte deployment notes verified on 2026-07-05:

- `abctl` is the recommended path for running Airbyte on a VM/bare metal machine
  with Docker and no existing Kubernetes cluster.
- `abctl` creates a local kind cluster and installs Airbyte with Helm.
- Suggested resources are 4+ CPUs and at least 8 GB RAM; low-resource mode can
  run with 2 CPUs and 8 GB RAM.
- Docker Compose deployments are no longer supported.
- Public access should be behind firewall/private networking and reverse proxy
  SSL.
