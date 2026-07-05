# OrgMemory Enterprise Readiness

Status date: 2026-07-05.

OrgMemory should be treated as an enterprise product, not a classroom demo. The
target context is on-prem deployment for real organizations, potentially across
large employee populations. That raises the bar: the current repo is a product
prototype and registry foundation, not production-ready software for broad real
data ingestion.

## Readiness Verdict

Current state:

- good enough to present the product thesis
- good enough to run a controlled walkthrough with seeded data
- good enough to start design-partner discovery
- not ready to ingest sensitive enterprise data at scale
- not ready for a 50,000-person rollout

The first real deployment should be a scoped pilot: one tenant, one to three
departments, limited sources, limited users, explicit data scope, and a written
rollback plan.

## Non-Negotiable Gates Before Real Data

Identity and access:

- OIDC integration first; SAML and SCIM when the customer requires them
- mapped organization, department, group, and role claims
- admin, reviewer, contributor, and viewer roles
- service identity for MCP/API access

Permission-aware memory:

- source ACL snapshots stored at ingestion time
- permission filtering before keyword search, vector search, AI answer
  generation, MCP tool responses, and export
- citations that point back to the allowed source or approved asset
- no cross-department leakage through summaries, graph edges, embeddings, or
  analytics

Data governance:

- data classification: public, internal, confidential, restricted
- PII and sensitive-data detection/redaction
- retention and deletion policy
- import scope approval before each connector is enabled
- clear distinction between Raw Source, Knowledge Asset, Capability Candidate,
  and Capability Asset

Security baseline:

- threat model for web, API, worker, MCP, AI provider, database, and connector
  flows
- OWASP ASVS review for web/API controls
- OWASP LLM Top 10 review for prompt injection, sensitive information
  disclosure, tool misuse, supply chain, and excessive agency
- secrets management outside the repo and outside customer-visible logs
- TLS, secure cookies, CSRF/CORS rules, rate limits, request size limits, and
  upload scanning

Operations:

- on-prem install runbook
- backup and restore drill
- database migration and rollback procedure
- monitoring, logs, traces, alerts, and health checks
- incident response path
- load test for the expected pilot usage pattern

Audit and accountability:

- immutable audit events for login, source import, permission change, approval,
  asset use, export, MCP tool call, and AI answer generation
- owner and backup-owner enforcement for approved Capability Assets
- reviewer notes and approval evidence
- usage analytics that can be explained to employees without feeling like
  surveillance

## Four-Month Pilot Reality

A four-month target is realistic only if the first deployment is a scoped
enterprise pilot, not a company-wide rollout.

Month 1 should build the enterprise foundation:

- OIDC path
- tenant/org/user/group model
- audit event table
- source/knowledge/candidate schema
- threat model and data classification policy
- CI gates for backend, frontend, and database migrations

Month 2 should ingest controlled sources:

- manual/file upload
- one document source such as SharePoint, OneDrive, or Google Drive
- one workflow source such as n8n JSON export
- source ACL snapshot
- Knowledge Asset creation and review

Month 3 should make Ask Memory safe:

- hybrid search with citations
- permission filtering before retrieval
- AI answer generation that cites allowed assets
- stored chat and usage events
- MCP tools limited to approved, permissioned assets

Month 4 should harden the pilot:

- backup/restore drill
- monitoring and alerting
- security review against ASVS and LLM Top 10
- anonymized seed data and customer-specific import scripts
- admin runbook and user training
- pilot rollout to one to three departments

## Recommended First Pilot Scope

Do:

- start with one enterprise tenant
- pick one or two business processes with measurable reuse value
- use explicit customer-approved source scopes
- start with 20-100 active pilot users
- expand only after permissions, audit, and employee trust are proven

Do not:

- ingest all Slack/Teams/Drive data first
- market the system as fully production-ready
- treat embeddings as permission boundaries
- auto-publish AI-detected assets without human review
- silently capture private employee AI sessions

## Reference Standards

Use these as the external baseline for investor, advisor, and enterprise
conversations:

- OWASP Application Security Verification Standard:
  https://owasp.org/www-project-application-security-verification-standard/
- OWASP Top 10 for Large Language Model Applications:
  https://owasp.org/www-project-top-10-for-large-language-model-applications/
- NIST AI Risk Management Framework Generative AI Profile:
  https://www.nist.gov/publications/artificial-intelligence-risk-management-framework-generative-artificial-intelligence
- NIST Cybersecurity Framework 2.0:
  https://www.nist.gov/cyberframework
