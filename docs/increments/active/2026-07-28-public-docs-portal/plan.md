# Public Documentation Portal Plan

Implement the accepted
[public documentation portal design](design.md) as five reviewable pull
requests. Each pull request starts from the latest `origin/main` in an isolated
worktree and preserves unrelated work.

No phase publishes internal repository documentation by default.

## Preconditions

- PRs start from the latest `origin/main` in isolated worktrees.
- PR 1 records a compatibility decision for the exact Node, pnpm, Next.js,
  Fumadocs MDX, Fumadocs OpenAPI, Orama, and React versions before scaffolding.
- The implementation uses the current official Fumadocs and underlying Next.js
  documentation; remembered APIs are not accepted as verification.
- PRs 1-4 need only repository and CI access. PR 5 additionally needs the
  existing GitHub `production` environment, GHCR publication, SSH access to the
  ZM host, DNS-provider access, and Nginx Proxy Manager access.
- No task stores a secret value. Plans may name only the managed location and
  retrieval procedure.
- Repository execution priority is Current Queue position 5 in
  `docs/increments/active/README.md`. PRs 1-4 may proceed independently when
  engineering capacity is available; PR 5 waits for its external-access
  preflight.

## PR 1 — Application Foundation

Purpose: create an independent, reproducible Fumadocs application without
publishing substantive content.

- [x] Create `apps/docs` from the current official Fumadocs Next.js template.
- [x] Pin Node, pnpm, Next.js, Fumadocs, and related dependencies in the lockfile.
- [x] Configure Fumadocs MDX and a typed frontmatter schema.
- [x] Add the base home layout, docs layout, theme, OrgMemory branding, and
  mobile navigation.
- [x] Add explicit `meta.json` navigation rather than alphabetical discovery.
- [x] Add `public-content.manifest.json` as the release allowlist and create the
  validator that compares manifest, frontmatter, page files, and page tree.
- [x] Add draft filtering; `DOCS_INCLUDE_DRAFTS=true` is preview-only and cannot
  be set in production.
- [x] Add `Dockerfile.dockerignore` with deny-by-default build inputs.
- [x] Add a minimal health endpoint.
- [x] Add TypeScript, production-build, MDX-schema, manifest, publication-policy,
  and `sourceRefs` scripts.
- [x] Add a multi-stage, non-root standalone Node Dockerfile with OCI labels.
- [x] Add a local Compose profile for the docs container without coupling it to
  product startup.

Exit gate:

- clean install from the lockfile;
- typecheck, frontmatter, manifest, publication-policy, and production build
  pass;
- container starts and its health endpoint passes;
- the existing API, worker, MCP, and web services are unchanged.

## PR 2 — Onyx-Style Information Architecture

Purpose: establish the complete reader journey before writing many pages.

- [x] Implement root navigation for Overview, Deployment, Admins, Developers,
  Architecture and Security, and Changelog.
- [x] Implement the left page tree, right table of contents, previous/next
  navigation, search trigger, GitHub link, and theme control.
- [x] Build the audience-oriented home page with Quickstart and Demo as the
  primary actions.
- [x] Add all fifteen first-release entries to the release manifest.
- [x] Create draft page shells with real titles, descriptions, audience, status,
  source references, and review metadata; keep them out of production routes,
  search, sitemap, and LLM outputs.
- [x] Add reusable MDX patterns for procedure steps, callouts, capability cards,
  diagrams, API examples, and verification blocks.
- [x] Add link/anchor validation and the first Playwright +
  `@axe-core/playwright` navigation tests.
- [x] Verify keyboard and mobile navigation before substantive content makes
  structural changes expensive.

Exit gate:

- every primary audience reaches its first useful page in at most two choices;
- active navigation remains clear on desktop and mobile;
- draft shells are absent from the production-derived URL set;
- accessibility smoke tests cover header, sidebar, mobile navigation, and table
  of contents; search is added and tested in PR 4.

## PR 3 — Core Product And Architecture Content

Purpose: ship the smallest credible public corpus, grounded only in current
repository facts.

- [x] Author Welcome, Quickstart and POC Demo, and Core Concepts.
- [x] Author the governed Asset lifecycle from current Asset Registry behavior.
- [x] Author System Description using the Onyx presentation pattern.
- [x] Add an accessible high-level architecture diagram.
- [x] Author ingestion lifecycle, authorization architecture, and secure
  retrieval/GraphRAG.
- [x] Author identity/permission administration and source/connection guides.
- [x] Author Assistant and MCP integration.
- [x] Author the self-hosting overview without exposing private production
  values or internal operational evidence.
- [x] Author functional and quality coverage.
- [x] Author the requirement-to-implementation-to-test traceability page.
- [x] Author limitations and future work with current behavior separated from
  planned work.
- [x] Add `sourceRefs` to `docs/vision.md`, `ARCHITECTURE.md`, current domain
  specs, verification docs, accepted decisions, the OpenAPI contract, and
  selected sanitized runbook facts.
- [x] Perform a fact review against code, schema, contracts, and tests before
  calling any behavior current.

Exit gate:

- all fifteen pages are complete and contain no academic-template filler;
- architecture and security claims match current repository evidence;
- each diagram has explanatory text and alt text;
- the thesis evidence matrix covers problem, functional and quality
  requirements, design, verification, and limitations;
- internal research, active increments, and raw runbooks are absent from the
  public content tree.

## PR 4 — Search, API, And Machine-Readable Docs

Purpose: make the corpus useful to humans, integrations, search engines, and AI
agents without adding a new AI answer boundary.

- [x] Add the server-side Orama search route from the Fumadocs source.
- [x] Tune searchable titles, descriptions, headings, and structured content.
- [x] Add generated OpenAPI pages from `contracts/openapi.json`.
- [x] Generate a sanitized docs OpenAPI input and reject unapproved servers,
  internal hostnames, secret-bearing examples, and private operational text.
- [x] Add authored authentication, error, permission, and integration guidance
  before the generated reference.
- [x] Disable live API execution unless a safe public test origin exists.
- [x] Add `/llms.txt`, `/llms-full.txt`, and per-page Markdown output for all
  fifteen authored first-release pages.
- [x] Add canonical metadata, Open Graph metadata, sitemap, and robots behavior.
- [x] Add link and heading-anchor validation.
- [x] Add OpenAPI generation drift and `sourceRefs` existence checks.
- [x] Crawl generated routes, HTML, sitemap, search records, API pages, and LLM
  outputs; require their route set to match the public manifest and run secret,
  private-host, and forbidden-path scans.

Exit gate:

- searches for Asset, OpenFGA, GraphRAG, MCP, and connector return useful pages;
- generated API pages match the committed contract;
- machine-readable outputs contain only public content;
- broken links, duplicate page URLs, and missing source references fail CI.
- `sourceRefs` is absent from HTML, search, sitemap, LLM outputs, and client
  JavaScript.

## PR 5 — Production Delivery And Verification

Purpose: deploy the independent portal and prove both availability and the
publication boundary.

- [x] Run a read-only production preflight for the `/apps/orgmemory` paths,
  external `proxy-network`, GHCR pull access, available memory, GitHub
  production-environment secrets, DNS control, and Nginx Proxy Manager access.
  Stop before mutation if any required capability is absent.
  The repository, network, Docker/Compose, resources, Nginx Proxy Manager
  attachment, and SSH secret names are present. GHCR authentication is
  intentionally deployment-scoped. One GitHub-hosted production runner timed
  out before SSH authentication and later bounded, run-scoped deployments
  completed successfully. Docs-only commits are product release no-ops.
  DNS does not resolve and DNS/Nginx Proxy Manager configuration access is not
  proven, so the docs live mutation remains stopped.
- [x] Add docs path filtering to CI so docs-only changes run the correct gates.
- [x] Add `.github/workflows/build-docs.yml` and publish
  `ghcr.io/kl3init/orgmemory-docs:sha-<commit>` with revision metadata, SBOM,
  provenance, and the repository vulnerability-scan policy.
- [x] Add `.github/workflows/deploy-docs.yml` with its own production concurrency
  lock, exact green commit input, SSH host verification, and docs-only rollback.
- [x] Add `infrastructure/deployment/compose.docs.yaml` with only
  `orgmemory-docs`, the existing external `proxy-network`, container port 3000,
  `/healthz`, restart policy, non-root hardening, and an initial 512 MiB memory
  limit.
- [x] Add `/apps/orgmemory/.env.docs.production` as the host-managed image
  reference file with mode `0600`; commit only an example contract.
- [x] Add `deploy-docs.sh` and `smoke-docs.sh`. Poll health for at most 60
  seconds; use 5-second connection and 15-second request timeouts.
- [x] Configure DNS for `docs.kl3in.tech`; Cloudflare and Google public
  resolvers return the ZM ingress address.
- [ ] Issue and verify TLS for `docs.kl3in.tech`.
- [ ] Configure Nginx Proxy Manager to forward
  `docs.kl3in.tech -> orgmemory-docs:3000`, with TLS, compression, immutable
  asset caching, revalidatable document outputs, HSTS after TLS verification,
  `nosniff`, strict referrer policy, restrictive permissions policy, and framing
  denial.
- [ ] Verify home, one deep link, search, API reference, `llms.txt`, mobile
  navigation, and container health.
- [ ] Crawl every public route and compare it with the committed allowlist; scan
  all reachable outputs for internal paths, secrets, and private hosts.
- [x] Force one failed canary deployment and prove `deploy-docs.sh` restores the
  previous image, reruns health/smoke checks, and does not recreate product
  services.
- [x] Retain at least the two most recent verified docs images and record the
  previous reference before every deployment.
- [ ] Record the deployed revision and public verification evidence.

Exit gate:

- production URL and TLS are healthy;
- the docs deployment is independently rollbackable;
- product runtime health is unchanged;
- the public-content negative checks pass;
- repository documentation and Northstar are updated with the verified outcome.

## Content Follow-Ups

After the first release has real readers:

- [ ] Add focused admin and operator procedures driven by support questions.
- [ ] Expand the first-release evaluation pages with measured retrieval,
  performance, usability, and security results as evidence becomes available.
- [ ] Generate the university-specific thesis appendix from the canonical
  traceability evidence without changing public navigation into an SRS/SDD.
- [ ] Add Vietnamese translations for the highest-value pages after the English
  information architecture stabilizes.
- [ ] Add versioned API/deployment documentation only after a supported
  compatibility policy exists.
- [ ] Evaluate an OrgMemory-powered Ask AI experience only after search queries
  and reader feedback identify a measured need.

## Executable Gate Matrix

PR 1 defines stable script names; implementations may refine commands but CI and
local development use the same scripts.

| Gate | PR 1 | PR 2 | PR 3 | PR 4 | PR 5 |
| --- | --- | --- | --- | --- | --- |
| `pnpm install --frozen-lockfile` | required | required | required | required | required |
| `pnpm typecheck` | required | required | required | required | required |
| `pnpm check:content` | required | required | required | required | required |
| `pnpm check:manifest` | required | required | required | required | required |
| `pnpm check:publication` | required | required | required | required | required |
| `pnpm check:links` | — | required | required | required | required |
| `pnpm check:api` | — | — | — | required | required |
| `pnpm test:e2e` | health smoke | navigation/a11y | core journeys/a11y | search/API/LLM | full public smoke |
| `pnpm build` | required | required | required | required | required |
| Docker build/health | required | required | required | required | required |
| generated-output crawl | — | draft exclusion | public content | full output audit | live crawl |
| forced rollback | — | — | — | — | required |
| `git diff --check` | required | required | required | required | required |

`CI Gate` remains the authoritative aggregate result. A docs-only change may
skip unrelated backend work, but it must never skip the docs gate.
