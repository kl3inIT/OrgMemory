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
- Author English and Vietnamese together in the same increment. Neither
  language is a prerequisite or approval gate for the other; both preserve the
  same verified product meaning naturally.
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

The owner should use this fast local loop to review the bilingual page and its
visuals. Production publication still requires the full repository gates and
immutable release loop.

## Target Navigation

```text
Getting Started
├── What is Organizational AI Memory?
├── Core concepts
└── Your first governed journey

Product Guides
├── Work with governed Assets
├── Search organizational knowledge
├── Ask with Assistant and verify citations
└── Explore the knowledge graph

Administration
├── Connect and synchronize a source
├── Manage users and identities
├── Configure roles and permissions
└── Audit effective access

Deployment & Operations
├── Self-host Organizational AI Memory
├── Configure secrets and environment
├── Monitor system health
├── Back up and restore
├── Upgrade and roll back
└── Troubleshooting

Developers & Integrations
├── Connect an MCP client
├── Integrate the Assistant
└── Ingest documents through the API

Architecture & Security
├── System context
├── Runtime components
├── Domain and data model
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
only after reviewed content and reader demand justify promotion. During the
incremental migration, no temporary **Guides** root or placeholder page is
published. Administration, Deployment, and Integration categories return only
when their first replacement page is co-authored and reviewed.

## Authoring Queue

Navigation order serves readers. Authoring now follows the same outside-in
journey: product tasks first, then administration and operations, with
architecture available for readers who need the underlying model.

| Order | Page | Target location | Current evidence | State |
| ---: | --- | --- | --- | --- |
| 1 | What is Organizational AI Memory? | Getting Started | `getting-started/index.mdx` | owner review |
| 2 | Core concepts | Getting Started | `getting-started/core-concepts.mdx` | review |
| 3 | Your first governed journey | Getting Started | `getting-started/first-governed-journey.mdx` | owner review |
| 4 | Work with governed Assets | Product Guides | `product-guides/work-with-governed-assets.mdx` | owner review |
| 5 | Search organizational knowledge | Product Guides | search contracts, UI, and tests | missing |
| 6 | Ask with Assistant and verify citations | Product Guides | Assistant and citation contracts/tests | missing |
| 7 | Explore the knowledge graph | Product Guides | graph viewer behavior and tests | missing |
| 8 | Connect and synchronize a source | Administration | connector contracts, ingestion code, and tests | rewrite from evidence |
| 9 | Manage identities and permissions | Administration | identity, SCIM, and authorization specs/tests | rewrite from evidence |
| 10 | Audit effective access | Administration | permission evidence specs/tests | missing |
| 11 | Self-host Organizational AI Memory | Deployment & Operations | deployment configuration and runbooks | rewrite from evidence |
| 12 | Configuration and secrets | Deployment & Operations | environment contracts/runbooks | missing |
| 13 | Observability and health | Deployment & Operations | deployment and telemetry evidence | missing |
| 14 | Backup, restore, upgrade, and rollback | Deployment & Operations | runbooks and release workflows | split later |
| 15 | Connect an MCP client | Developers & Integrations | MCP contracts and integration tests | rewrite from evidence |
| 16 | Ingest through the API | Developers & Integrations | ingestion API contract | missing |
| 17 | System context | Architecture & Security | `architecture-security/system-description.mdx` | review |
| 18 | Domain and data model | Architecture & Security | domain specs and schema | missing |
| 19 | Ingestion and indexing | Architecture & Security | `architecture-security/ingestion-lifecycle.mdx` | review |
| 20 | Identity and authorization | Architecture & Security | `architecture-security/authorization.mdx` | review |
| 21 | Secure retrieval and GraphRAG | Architecture & Security | `architecture-security/secure-retrieval-graphrag.mdx` | review |
| 22 | Runtime components | Architecture & Security | system description and `ARCHITECTURE.md` | missing |
| 23 | Trust boundaries and threat model | Architecture & Security | security decisions/specs | missing |
| 24 | API overview, auth, and errors | Reference | authored API overview/auth pages | review |
| 25 | API endpoint groups | Reference | generated OpenAPI pages | generated |
| 26 | Configuration reference | Reference | committed environment contracts | missing |
| 28 | Connector capability matrix | Reference | connector specs/tests | missing |
| 29 | Roles and permissions matrix | Reference | OpenFGA model and domain specs | missing |
| 30 | MCP tools and resources | Reference | MCP contracts/tests | missing |
| 31 | Known limitations | Reference | current evidence only | rewrite from evaluation |

Functional coverage and requirement traceability leave public navigation through
a dedicated later increment. Their canonical evidence remains private in
domain tests/specs and university deliverables.

## Visual Selection

Choose the smallest visual form that still explains the relationship:

1. Use a responsive MDX/DOM component for a small conceptual map with up to
   four nodes. Keep definitions as searchable text below the visual.
2. Use Fumadocs `Steps` for a procedure the reader performs in order.
3. Use Mermaid for source-controlled system flows, domain relationships,
   lifecycles, trust boundaries, and deployment topology that will change with
   the architecture.
4. Use a product screenshot only when the page teaches a real interface state
   or action. Capture the current UI with synthetic documentation data.
5. Use a generated illustration only as explanatory or decorative context.
   Never make generated raster text the sole source of a requirement,
   permission rule, identifier, or architectural fact.

Every informative visual needs a useful accessible label or caption, must work
in light and dark themes, and must remain legible at the documentation content
width. Add a Mermaid renderer only when the first reviewed Mermaid diagram is
ready; Fumadocs does not bundle one by default.

## One-Page Checklist

1. Collect code/spec/test/runtime evidence.
2. Ask the owner focused architecture and audience questions.
3. Agree on an outline and exclusions.
4. Draft and review English and Vietnamese together.
5. Complete owner teach-back.
6. Test realistic reader questions and ambiguity in both languages.
7. Run docs checks, browser/accessibility tests, and publication scans.
8. PR, merge, immutable build, deploy, and live verification.
9. Mark exactly one queue item complete and record the next item.
