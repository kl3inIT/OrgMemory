# Public Documentation Portal Design

## Decision

Build an independent public documentation application at
`docs.kl3in.tech` using Fumadocs, Next.js, Fumadocs MDX, and a dedicated
container. Its information architecture follows the audience-oriented pattern
used by Onyx:

This is the owner-selected temporary public hostname. A later permanent-brand
domain is a controlled canonical-origin cutover across metadata, sitemap,
robots, SCIM discovery, deployment configuration, DNS, TLS, and proxy state;
the temporary host must not remain as a second canonical origin.

- Overview
- Deployment
- Admins
- Developers
- Architecture and Security
- Changelog

The site is a curated publication surface. It does not load or publish the
repository's internal `docs/` tree wholesale.

## Outcome

A new reader can understand what OrgMemory is, run or explore it, administer a
deployment, integrate through the API or MCP, and evaluate its architecture and
security boundaries without reading internal increments, research, or
operational evidence.

The same site should provide enough architecture, requirements, design, and
verification material to support the graduation-thesis track without presenting
the public navigation as an academic SRS or SDD.

## Audience

The portal serves four primary audiences:

1. **Evaluators and adopters** need the product thesis, supported use cases,
   security posture, and a short path to a working demonstration.
2. **Users** need task-oriented guides for Assets, knowledge ingestion, secure
   retrieval, Assistant, and MCP consumption.
3. **Administrators and operators** need identity, source, permission,
   deployment, configuration, backup, upgrade, and troubleshooting guidance.
4. **Developers and integrators** need core concepts, authentication, API and
   MCP contracts, examples, and extension boundaries.

Academic readers are a secondary audience. Requirement traceability,
evaluation methodology, limitations, and thesis-specific evidence may be
published under an Evaluation section or kept as a separate thesis appendix,
but they do not define the main navigation.

## Reference Pattern

The Onyx System Description page is the primary presentation reference:

- a clear page title and one-sentence purpose;
- one high-level architecture visual;
- components grouped into understandable layers;
- concise responsibility descriptions;
- explicit replacement or extension boundaries;
- links to deeper pages instead of placing the whole design in one document.

OrgMemory should adopt that pattern without copying Onyx branding or text.

Onyx uses Mintlify, but the selected implementation is Fumadocs because
OrgMemory needs an independently self-hosted container, repository-owned MDX,
custom React layouts, self-hosted search, generated OpenAPI pages, and
machine-readable outputs. Mintlify remains a valid hosted documentation
platform, Docusaurus remains a mature static-site option, and Nextra remains a
more opinionated Next.js option. Fumadocs is selected for composability and
self-hosting control, not because the Onyx site uses it.

The review gate compares information hierarchy and reader journeys, never
pixels. No Onyx wording, screenshots, diagrams, icons, or visual assets are
copied.

The initial OrgMemory System Description page contains:

1. a system-context statement;
2. a high-level architecture diagram;
3. delivery components: Web, API, Worker, and MCP;
4. governance components: identity, permission evidence, OpenFGA, and audit;
5. data components: PostgreSQL, graph/vector projections, and object storage;
6. AI components: provider adapters, extraction, embedding, and retrieval;
7. infrastructure components: reverse proxy, Keycloak, and deployment boundary;
8. replaceable and deliberately coupled components;
9. links to ingestion, authorization, retrieval, and deployment deep dives.

## Information Architecture

```text
Overview
├── Welcome to OrgMemory
├── Quickstart and demo
├── Core concepts
├── Capability map
├── Product thesis
├── Governed organizational memory
├── Knowledge and Asset lifecycle
├── Permission-aware retrieval
└── Assistant and MCP delivery

Deployment
├── Deployment options
├── Local Docker setup
├── Production topology
├── Configuration
├── Backup and recovery
├── Upgrade
└── Troubleshooting

Admins
├── Identity and organizations
├── Users and groups
├── Sources and connections
├── Knowledge Spaces
├── Permissions and audit
└── AI providers

Developers
├── Developer quickstart
├── Authentication
├── API overview
├── API reference
├── MCP integration
└── Connector model

Architecture and Security
├── System description
├── Data and Asset model
├── Ingestion lifecycle
├── Authorization architecture
├── Secure retrieval and GraphRAG
├── Trust boundaries
├── Security operations
├── Architecture decisions
└── Evaluation
    ├── Functional and quality coverage
    ├── Verification and requirement traceability
    └── Limitations and future work

Changelog -> GitHub Releases for the first release
```

Evaluation remains under Architecture and Security instead of becoming a
top-level academic navigation area. Thesis-only submissions and university
templates remain separate artifacts and link to the relevant public pages.

## Page Layout

The desktop layout uses:

- a top navigation for the root audience areas;
- a left sidebar for the active area's page tree;
- a readable center column;
- a right-side table of contents;
- search, theme, repository, and product links in the header;
- previous/next navigation, last-reviewed metadata, and edit feedback at the
  bottom.

Mobile preserves the same hierarchy through a compact navigation drawer and
does not rely on hover interactions.

The documentation host is a technical reader surface, not a product marketing
site. `/` redirects to `/docs/overview`; audience paths, Quickstart, product
links, and evaluation material remain discoverable through the Fumadocs
navigation and Overview page.

## Fumadocs Architecture

Create an independent application:

```text
apps/docs/
├── app/
│   ├── page.tsx              Redirect to /docs/overview
│   ├── docs/[[...slug]]/
│   ├── api/search/
│   ├── llms.txt/
│   └── llms-full.txt/
├── components/
│   ├── architecture/
│   ├── mdx/
│   └── navigation/
├── content/docs/
│   ├── overview/
│   ├── deployment/
│   ├── admins/
│   ├── developers/
│   ├── architecture-security/
│   │   └── evaluation/
│   └── changelog/
├── lib/
├── public/
├── scripts/
├── Dockerfile
├── next.config.mjs
├── package.json
├── pnpm-lock.yaml
├── source.config.ts
└── tsconfig.json
```

Use Fumadocs MDX as the type-safe content source. Define a custom frontmatter
schema with at least:

- `title`;
- `description`;
- `audience`;
- `status`;
- `sourceRefs`;
- `lastReviewed`;
- optional `icon` and `full`.

Use `meta.json` to make navigation order explicit. Mark the major audience
folders as Fumadocs root folders so `DocsLayout` can expose them as layout tabs
or a compact top-level switcher. Do not depend on alphabetical ordering.

Commit `apps/docs/public-content.manifest.json` as the release allowlist. Every
entry declares:

- route and content file;
- root navigation area and order;
- `public` or `draft` release status;
- canonical internal `sourceRefs`;
- required review owner.

CI derives the Fumadocs page tree and compares it with this manifest.
`meta.json`, frontmatter, routes, search, sitemap, OpenAPI pages, LLM outputs,
and the manifest must resolve to the same public URL set. Draft pages are
available only in local/preview builds with an explicit
`DOCS_INCLUDE_DRAFTS=true`; production loader, search, sitemap, and LLM outputs
exclude them.

## Search And AI Consumption

Use the built-in server-side Orama search endpoint for the first release.
Server-side search avoids shipping the full index to every browser and remains
self-hosted. Add audience or section tags only if search results become noisy.

Publish:

- `/llms.txt` as the curated page index;
- `/llms-full.txt` as the full public corpus;
- per-page Markdown output for all fifteen authored first-release pages.

Do not add an Ask AI widget in the first release. It introduces another model,
privacy, cost, and answer-quality boundary. OrgMemory can later implement it
through its own permission-aware Assistant when the public corpus and evaluation
set are stable.

## API Reference

Generate API reference pages from `contracts/openapi.json` with
`fumadocs-openapi`; do not hand-maintain endpoint signatures.

The generated reference is preceded by authored pages covering:

- authentication and browser versus bearer boundaries;
- error semantics;
- permission-aware `404` behavior;
- pagination and filtering conventions;
- representative integration flows.

CI fails when the committed OpenAPI contract changes without regenerating the
API pages or when generation produces an uncommitted diff.

The interactive API playground must default to a non-production example origin
and never embed credentials. If a safe public test environment does not exist,
render code examples but disable live execution.

## Content Ownership And Publication Boundary

The repository remains the engineering system of record:

| Public content | Canonical internal source |
| --- | --- |
| Product thesis and scope | `docs/vision.md` |
| Current system description | `ARCHITECTURE.md` |
| Product and domain behavior | `docs/specs/domains/` |
| Verification claims | `docs/tests/` and test evidence |
| Decision rationale | accepted `docs/decisions/` |
| API reference | `contracts/openapi.json` |
| Operations guidance | selected, sanitized `docs/runbooks/` facts |

Public MDX is deliberately authored for its audience. A `sourceRefs` frontmatter
field records the internal files that support each page. Build validation checks
that every source reference exists.

Internal research, active increments, private operations, raw evaluation data,
and runbooks are denied by default. No runtime loader scans the internal `docs/`
directory. Content becomes public only when copied or authored under
`apps/docs/content/docs/` and passes review.

The publication policy has four mechanically checked layers:

1. Fumadocs loads only files listed as `public` in
   `public-content.manifest.json`.
2. `apps/docs/Dockerfile.dockerignore` denies the repository by default and
   allows only `apps/docs`, `contracts/openapi.json`, and required root metadata
   into the image build context. Internal `docs/`, `demo/`, `.env*`, build
   output, and Git metadata do not enter the docs image.
3. A content audit scans authored MDX, the sanitized OpenAPI input, generated
   page routes, search records, sitemap, HTML, and LLM outputs for secrets,
   private hostnames, forbidden repository paths, and unexpected URLs.
4. A production crawl compares every published route with the committed
   allowlist; it is not limited to checking a few known negative URLs.

`sourceRefs` is build-only traceability. It is not rendered, copied into public
metadata, search records, sitemap, LLM output, or client-side JavaScript.

The OpenAPI publication step creates a sanitized docs input from
`contracts/openapi.json`. It rejects unapproved `servers`, secret-bearing
examples, internal hostnames, and descriptions that reference private
operations. The original committed contract remains unchanged.

## Language And Versioning

Use English as the canonical first-release language because the current
engineering sources and public API vocabulary are English. Add Vietnamese only
after the first information architecture and core pages stabilize. The
graduation-thesis deliverables may remain Vietnamese and link to canonical
English architecture pages.

Do not introduce product-version navigation before OrgMemory has a supported
compatibility policy. Maintain a current site and changelog first. When stable
major versions exist, use Fumadocs partial versioning for API or deployment
sections; reserve full-site version branches and subdomains for genuinely
incompatible product generations.

## Deployment

Build the site as a Next.js standalone Node container behind the existing
Nginx Proxy Manager and `proxy-network`:

```text
Internet
  -> DNS/TLS: docs.kl3in.tech
  -> reverse proxy
  -> orgmemory-docs:3000
  -> server-rendered/static documentation pages
  -> server-side Orama search
```

The container is independent from `web`, `api`, `worker`, and `mcp`. A docs
failure must not affect the product runtime.

The Docker build must include `source.config.ts` and `next.config.*` in the build
work directory so Fumadocs MDX can generate its content. Pin the Node base image
and application dependencies, run as a non-root user, expose a health endpoint,
and add OCI source/revision/build labels consistently with the product images.

Use a static export only if the server runtime becomes operationally
unnecessary. Static Orama makes every client download the search index, so the
standalone Node deployment is the better default for a growing corpus.

Production delivery is independent from the six-image application release:

- image: `ghcr.io/kl3init/orgmemory-docs:sha-<commit>`;
- build workflow: `.github/workflows/build-docs.yml`;
- deploy workflow: `.github/workflows/deploy-docs.yml`;
- Compose contract: `infrastructure/deployment/compose.docs.yaml`;
- host environment: `/apps/orgmemory/.env.docs.production`, mode `0600`;
- deploy script: `infrastructure/deployment/scripts/deploy-docs.sh`;
- smoke script: `infrastructure/deployment/scripts/smoke-docs.sh`;
- host service: `orgmemory-docs` on the existing `proxy-network`;
- container port: `3000`;
- health path: `/healthz`, plain `ok`;
- initial resource limit: 512 MiB memory, adjusted only from observation.

The protected GitHub `production` environment and existing GHCR/SSH managed
credentials are reused; no secret values are copied into repository files.
Initial DNS/TLS setup requires an operator with access to the DNS provider and
Nginx Proxy Manager. The implementation may complete PRs 1-4 without that
access, but PR 5 cannot be marked complete.

`deploy-docs.sh` acquires a docs-only deployment lock, validates the immutable
image, records the current image as the previous release, pulls before mutation,
starts only `orgmemory-docs`, and polls container health for at most 60 seconds.
It then runs public smokes with a 5-second connection timeout and a 15-second
request timeout. Any failure restores the previous immutable image, recreates
only the docs service, reruns health and public smokes, and exits non-zero.
At least the last two verified docs images remain available.

Nginx Proxy Manager forwards `docs.kl3in.tech` to
`orgmemory-docs:3000`, terminates TLS, and does not publish a host port. Expected
headers include HSTS after TLS is proven, `X-Content-Type-Options: nosniff`,
`Referrer-Policy: strict-origin-when-cross-origin`, a restrictive
`Permissions-Policy`, and framing denial. Immutable Next.js assets receive
long-lived caching; HTML, search, sitemap, and LLM outputs remain revalidatable.

### PR 5 Architecture Challenge

Proposal: publish and deploy the docs image through a separate workflow,
Compose project, lock, environment file, and rollback ledger while reusing only
the protected SSH secrets and external proxy network.

Strongest counterargument: add docs to the existing six-image build set and
production Compose project. That would reuse mature image carry-forward,
deployment, and rollback mechanics and would avoid another workflow and host
state directory.

Repository evidence: `apps/docs` has no product runtime dependency, while the
product deployment intentionally runs database bootstrap, backup, migrations,
Keycloak configuration, six-image pulls, and `--remove-orphans`. Coupling a
documentation release to that path would expand its failure and maintenance
surface and make a docs rollback capable of changing application services.

Final choice: keep the independent docs delivery boundary. Exact-commit and
known-host checks are mirrored from the production workflow, but every remote
Compose command names only `orgmemory-docs`. A failed-canary contract test
rejects product service operations. This adds a small amount of workflow code
in exchange for independently reviewable releases and rollback.

Rejected alternative: one combined product/docs release train. It remains
appropriate only if docs later acquires a hard compatibility dependency on the
same runtime commit.

An independent reviewer was unavailable under the active no-delegation
constraint. The project owner explicitly selected an independent `apps/docs`
application, authorized the delivery loop, and directed that work stop before
live deployment and DNS mutation.

## Content Style

Every task page follows:

1. what the reader will accomplish;
2. prerequisites;
3. numbered steps;
4. expected result;
5. verification;
6. common failure modes;
7. next steps.

Every concept or architecture page follows:

1. short definition;
2. why it exists;
3. diagram or model;
4. components and responsibilities;
5. invariants and trust boundaries;
6. trade-offs or replacement boundaries;
7. related guides and reference.

Avoid pages that merely restate code or database tables. Prefer one page per
reader goal, descriptive titles, short introductions, meaningful diagrams, and
progressive disclosure.

Architecture visuals require adjacent textual explanations and useful alt text.
Diagrams are stored as source-controlled Mermaid or SVG assets; screenshots are
used only for UI procedures and must be refreshed when the corresponding flow
changes.

## Quality Gates

The following are the eventual required pull-request gates. Their introduction
and application by PR follow the authoritative matrix in `plan.md`;
`check:content` owns frontmatter, MDX, spelling, and user-facing style checks.

- MDX/frontmatter schema validation;
- TypeScript typecheck;
- production Next.js build;
- generated OpenAPI reference drift check;
- internal-link and heading-anchor validation;
- public-content allowlist check;
- `sourceRefs` existence check;
- spelling/style checks for user-facing content;
- responsive browser smoke tests;
- accessibility smoke tests for keyboard navigation, landmarks, contrast, and
  image alt text;
- Docker image build and container health check;
- `git diff --check`.

The production gate additionally verifies DNS, TLS, the root-to-Overview
redirect, one deep
link, search, `llms.txt`, the API reference, mobile navigation, and that a known
internal document is not publicly reachable.

## First Release Content Manifest

The first release is limited to fifteen authored pages:

| Route | Page | Primary source |
| --- | --- | --- |
| `/docs/overview` | Welcome to OrgMemory | `docs/vision.md` |
| `/docs/overview/quickstart` | Quickstart and POC demo | verified demo flow |
| `/docs/overview/core-concepts` | Core concepts | vision and current specs |
| `/docs/overview/asset-lifecycle` | Governed Asset lifecycle | Asset Registry spec |
| `/docs/architecture-security/system-description` | System description | `ARCHITECTURE.md` |
| `/docs/architecture-security/ingestion-lifecycle` | Ingestion lifecycle | ingestion spec |
| `/docs/architecture-security/authorization` | Authorization architecture | permission specs and ADRs |
| `/docs/architecture-security/secure-retrieval-graphrag` | Secure retrieval and GraphRAG | retrieval and GraphRAG specs |
| `/docs/admins/identity-permissions` | Administer identity and permissions | identity and permission specs |
| `/docs/admins/sources-connections` | Configure sources and connections | connector contracts and specs |
| `/docs/developers/assistant-mcp` | Assistant and MCP integration | Assistant/MCP spec and runbook facts |
| `/docs/deployment/self-hosting` | Self-hosting overview | deployment guideline and sanitized runbook facts |
| `/docs/architecture-security/evaluation/coverage` | Functional and quality coverage | specs, tests, and quality gates |
| `/docs/architecture-security/evaluation/traceability` | Verification and requirement traceability | requirements-to-evidence matrix |
| `/docs/architecture-security/evaluation/limitations` | Limitations and future work | vision non-goals and roadmap |

API pages are generated separately under `/docs/developers/api-reference`.
The first-release Changelog top-level link points to GitHub Releases and is not
an empty MDX route.

The thesis evidence matrix is part of release scope:

| Academic need | Public presentation | Completion evidence |
| --- | --- | --- |
| Problem, scope, stakeholders | Welcome and product thesis | explicit scope and non-goals |
| Functional requirements | Capability and domain pages | each requirement links to implemented behavior |
| Quality requirements | Evaluation coverage | measurable security, reliability, performance, and usability criteria |
| Software design | System, data, ingestion, authorization, and retrieval pages | diagrams plus current component responsibilities |
| Verification | Evaluation traceability | requirement-to-implementation-to-test mapping |
| Limitations | Limitations and future work | current gaps separated from shipped behavior |

University templates and the final Vietnamese thesis remain separate documents,
but they reference this canonical evidence instead of duplicating architecture
facts.

The information-architecture tree is the target taxonomy. The fifteen-route
manifest is the authoritative first-release scope: unlisted future leaves do not
appear in the production sidebar, search, sitemap, or LLM outputs.

## Non-Goals

- publishing the entire internal `docs/` tree;
- building a general-purpose CMS;
- implementing docs authentication in the first public release;
- publishing secrets, customer data, internal hosts, or operational evidence;
- adding an AI answer widget before corpus quality is measured;
- translating every page before the canonical content stabilizes;
- presenting the public navigation as SRS, SDD, or a university report;
- copying Onyx branding, wording, or visual assets.

## Acceptance Criteria

The increment is complete when:

1. `docs.kl3in.tech` serves the independent docs container over valid TLS.
2. The site root enters Overview, where each primary audience reaches an
   appropriate first action through the docs navigation.
3. The top-level navigation and page layout provide the Onyx-like
   audience-oriented experience on desktop and mobile.
4. The fifteen first-release pages contain verified OrgMemory facts and the
   System Description page includes an accessible high-level architecture
   diagram.
5. Search returns useful results for `Asset`, `OpenFGA`, `GraphRAG`, `MCP`, and
   `connector`.
6. API reference pages are generated from `contracts/openapi.json`.
7. `llms.txt`, `llms-full.txt`, and page metadata are available.
8. CI passes the content, build, link, accessibility, API drift, allowlist,
   Docker, and repository gates.
9. The route/output audit proves the entire published URL set matches the
   allowlist and contains no internal research, increments, runbooks, secrets,
   or private hosts.
10. Current repository sources remain authoritative and each authored public
    page identifies its supporting `sourceRefs`.
11. The thesis evidence matrix maps problem, functional requirements, quality
    requirements, design, verification, and limitations to public evidence.
12. A forced failed deployment proves docs-only rollback restores the previous
    healthy image without restarting product services.

## Research References

- Onyx System Description:
  <https://docs.onyx.app/security/architecture/system_description>
- Fumadocs Quick Start: <https://www.fumadocs.dev/docs>
- Fumadocs Page Slugs and Page Tree:
  <https://www.fumadocs.dev/docs/headless/page-conventions>
- Fumadocs Docs Layout:
  <https://www.fumadocs.dev/docs/ui/layouts/docs>
- Fumadocs built-in Orama search:
  <https://www.fumadocs.dev/docs/headless/search/orama>
- Fumadocs OpenAPI:
  <https://www.fumadocs.dev/docs/integrations/openapi>
- Fumadocs AI and LLM outputs:
  <https://www.fumadocs.dev/docs/integrations/llms>
- Fumadocs link validation:
  <https://www.fumadocs.dev/docs/integrations/validate-links>
- Fumadocs deployment:
  <https://www.fumadocs.dev/docs/deploying>
