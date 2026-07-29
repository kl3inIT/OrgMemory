# OrgMemory Public Docs Authoring Program

This is the durable page register and working agreement for the long-running
OrgMemory documentation program. The project owner co-authors one page at a
time so that improving the docs also improves their architectural
understanding.

## Working Agreement

- One content increment owns one conceptual page and its adjacent reviewed
  English/Vietnamese pair.
- Do not bulk-rewrite, bulk-translate, or create placeholder pages.
- Before prose, inspect the current repository and runtime evidence and agree
  on the reader question and outline.
- English establishes the approved meaning. Vietnamese follows after English
  review and preserves that meaning naturally.
- The owner context, outline, and teach-back checkpoints require owner
  participation. Branch, CI, merge, build, deploy, and live verification may
  continue autonomously after approval.
- Public product docs do not publish raw SRS, SDD, ADR, increment, test,
  runbook, infrastructure, or thesis material.

## Local-First Review Loop

The docs application has no runtime dependency on the OrgMemory product
services. After the one-time frozen install, start only the docs reader:

```powershell
corepack pnpm install --frozen-lockfile
corepack pnpm docs:dev
```

Review at `http://localhost:3000`. Editing MDX, metadata, styles, or navigation
uses Next.js hot reload; do not start the API, worker, MCP server, database,
Keycloak, OpenFGA, product web application, or Docker Compose for ordinary
content review.

For a draft-only page, use a local preview session:

```powershell
$env:DOCS_INCLUDE_DRAFTS = 'true'
corepack pnpm docs:dev
# Press Ctrl+C to stop the preview server before cleaning the parent shell.
Remove-Item Env:DOCS_INCLUDE_DRAFTS
```

The owner should use this fast local loop for the context, outline, English,
teach-back, and Vietnamese checkpoints. Production publication still requires
the full repository gates and immutable release loop.

## Target Navigation

```text
Getting Started
├── What is OrgMemory?
├── Quickstart
├── Core concepts
└── Terminology

Guides
├── Using OrgMemory
│   ├── Browse governed assets
│   ├── Search organizational knowledge
│   ├── Ask with Assistant
│   ├── Verify citations
│   └── Explore the knowledge graph
├── Administration
│   ├── Connect and synchronize a source
│   ├── Manage users and identities
│   ├── Configure roles and permissions
│   └── Audit effective access
├── Deployment & Operations
│   ├── Self-host OrgMemory
│   ├── Configure secrets and environment
│   ├── Monitor system health
│   ├── Back up and restore
│   ├── Upgrade and roll back
│   └── Troubleshooting
└── Integrations
    ├── Connect an MCP client
    ├── Integrate the Assistant
    └── Ingest documents through the API

Architecture & Security
├── System context
├── Runtime components
├── Domain and data model
├── Governed Asset lifecycle
├── Ingestion and indexing
├── Identity and authorization
├── Secure retrieval and GraphRAG
├── Trust boundaries and threat model
└── Deployment topology

Reference
├── API overview
├── Authentication and errors
├── API endpoint groups
├── Configuration variables
├── Connector capability matrix
├── Roles and permissions matrix
├── MCP tools and resources
├── Error and status codes
└── Known limitations
```

Changelog is a global navigation link. Deployment & Operations becomes a root
only after reviewed content and reader demand justify promotion.

## Authoring Queue

Navigation order serves readers; authoring order stabilizes the system model
before task procedures depend on it.

| Order | Page | Target location | Current evidence | State |
| ---: | --- | --- | --- | --- |
| 1 | What is OrgMemory? | Getting Started | `getting-started/index.mdx` | next |
| 2 | Core concepts | Getting Started | `getting-started/core-concepts.mdx` | review |
| 3 | System context | Architecture & Security | `architecture-security/system-description.mdx` | review |
| 4 | Domain and data model | Architecture & Security | domain specs and schema | missing |
| 5 | Governed Asset lifecycle | Architecture & Security | `architecture-security/asset-lifecycle.mdx` | review |
| 6 | Quickstart | Getting Started | `getting-started/quickstart.mdx` | review |
| 7 | Ingestion and indexing | Architecture & Security | `architecture-security/ingestion-lifecycle.mdx` | review |
| 8 | Identity and authorization | Architecture & Security | `architecture-security/authorization.mdx` | review |
| 9 | Secure retrieval and GraphRAG | Architecture & Security | `architecture-security/secure-retrieval-graphrag.mdx` | review |
| 10 | Runtime components | Architecture & Security | system description and `ARCHITECTURE.md` | missing |
| 11 | Trust boundaries and threat model | Architecture & Security | security decisions/specs | missing |
| 12 | Browse governed assets | Guides / Using OrgMemory | product behavior and tests | missing |
| 13 | Search organizational knowledge | Guides / Using OrgMemory | search contracts and tests | missing |
| 14 | Ask with Assistant and verify citations | Guides / Using OrgMemory | Assistant/MCP specs and tests | missing |
| 15 | Explore the knowledge graph | Guides / Using OrgMemory | graph viewer behavior and tests | missing |
| 16 | Connect and synchronize a source | Guides / Administration | `guides/administration/sources-connections.mdx` | review |
| 17 | Manage identities and permissions | Guides / Administration | `guides/administration/identity-permissions.mdx` | split and review |
| 18 | Audit effective access | Guides / Administration | permission evidence specs/tests | missing |
| 19 | Self-host OrgMemory | Guides / Deployment & Operations | `guides/deployment-operations/self-hosting.mdx` | review |
| 20 | Configuration and secrets | Guides / Deployment & Operations | environment contracts/runbooks | missing |
| 21 | Observability and health | Guides / Deployment & Operations | deployment and telemetry evidence | missing |
| 22 | Backup, restore, upgrade, and rollback | Guides / Deployment & Operations | runbooks and release workflows | split later |
| 23 | Connect an MCP client | Guides / Integrations | `guides/integrations/assistant-mcp.mdx` | split and review |
| 24 | Ingest through the API | Guides / Integrations | ingestion API contract | missing |
| 25 | Terminology | Getting Started | approved concepts | write after core model |
| 26 | API overview, auth, and errors | Reference | authored API overview/auth pages | review |
| 27 | API endpoint groups | Reference | generated OpenAPI pages | generated |
| 28 | Configuration reference | Reference | committed environment contracts | missing |
| 29 | Connector capability matrix | Reference | connector specs/tests | missing |
| 30 | Roles and permissions matrix | Reference | OpenFGA model and domain specs | missing |
| 31 | MCP tools and resources | Reference | MCP contracts/tests | missing |
| 32 | Known limitations | Reference | current evidence only | rewrite from evaluation |

Functional coverage and requirement traceability leave public navigation through
a dedicated later increment. Their canonical evidence remains private in
domain tests/specs and university deliverables.

## One-Page Checklist

1. Collect code/spec/test/runtime evidence.
2. Ask the owner focused architecture and audience questions.
3. Agree on an outline and exclusions.
4. Draft and review English.
5. Complete owner teach-back.
6. Test realistic reader questions and ambiguity.
7. Draft and review Vietnamese.
8. Run docs checks, browser/accessibility tests, and publication scans.
9. PR, merge, immutable build, deploy, and live verification.
10. Mark exactly one queue item complete and record the next item.
