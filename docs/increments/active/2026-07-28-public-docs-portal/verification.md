# Public Documentation Portal Verification

## PR 1 — Application Foundation

Delivered with the polyglot apps workspace in
[PR #103](https://github.com/kl3inIT/OrgMemory/pull/103), merged as
`3d02828ffddff32d243d1f2cc6f5c0820ae93966`. The aggregate CI run
`30386700211` passed, including the independent Public docs job and deployment
contracts.

## PR 2 — Onyx-Style Information Architecture

Branch: `feat/public-docs-ia`

Base: `origin/main@d7ca979937e95657aa0af0821f858128640ddd94`

Delivered in [PR #110](https://github.com/kl3inIT/OrgMemory/pull/110),
merged as `106036d99935a04a570892249cbbc52ce8930129`. Aggregate CI run
`30389231452` passed, including the Public docs Node 24 job and CI Gate.

Implementation evidence:

- fifteen first-release routes exist as typed, allowlisted draft shells;
- the production route boundary contains one public foundation route and
  excludes all fifteen drafts;
- root navigation, sidebar/root tabs, right table of contents, previous/next,
  search, theme, GitHub, and mobile navigation are wired through Fumadocs;
- reusable procedure, capability, diagram, API example, and verification
  patterns are available to authored MDX;
- the homepage routes adopters, administrators, developers, users, and
  evaluators to an appropriate first action;
- link and anchor validation uses the official Fumadocs-recommended
  `next-validate-link` integration with the Fumadocs Node loader;
- Playwright covers desktop and mobile navigation, keyboard focus, and
  WCAG-tagged axe smoke checks.

Passed local gates:

| Gate | Evidence |
| --- | --- |
| Clean install | `corepack pnpm install --frozen-lockfile` |
| Docs contracts | lint, Fumadocs generation, Next route types, TypeScript, content, manifest, publication, route boundary, and link checks |
| Route boundary | `1 public, 15 draft` |
| Links | `0 errored file, 0 errors` |
| Browser | Playwright Chromium and mobile Chromium, 8/8 tests |
| Accessibility | WCAG 2 A/AA, WCAG 2.1 A/AA, and WCAG 2.2 AA axe smoke checks passed |
| Production build | Next.js 16.2.11 Turbopack build; only `/docs` was generated from documentation content |

Context7 was attempted first and returned its quota-exhausted response. Current
official Fumadocs documentation and installed 16.13.0 type declarations were
used to verify root folders, layout tabs, page trees, the Node loader, and link
validation.

## PR 3 — Core Product And Architecture Content

Branch: `feat/public-docs-content`

Base: `origin/main@106036d99935a04a570892249cbbc52ce8930129`

Implementation evidence:

- all fifteen first-release pages are authored in English and marked public;
- Welcome, Quickstart, concepts, Asset lifecycle, identity, connectors,
  Assistant/MCP, self-hosting, architecture, security, coverage, traceability,
  and limitations are reconciled against current architecture, specs, tests,
  contracts, decisions, and selected sanitized runbook facts;
- the root `/docs` route redirects to `/docs/overview`;
- the System Description follows the accepted Onyx information pattern with a
  high-level accessible flow, layered component responsibilities, trust and
  data boundaries, replacement boundaries, and deep links;
- all technical flows are source-controlled HTML/MDX diagrams with adjacent
  explanations and accessible labels;
- the generated hero illustration is decorative product communication, not
  architecture evidence, and has descriptive alternative text;
- production navigation, source loading, and the committed manifest resolve to
  exactly fifteen public routes and zero drafts;
- the Windows path-separator bug in dynamic link population is covered by the
  portable `path.join` route input;
- the standalone Docker runtime now copies `public` assets explicitly.

Passed local gates:

| Gate | Evidence |
| --- | --- |
| Docs contracts | lint, Fumadocs generation, Next route types, TypeScript, 15-page content, manifest, publication, routes, and links |
| Route boundary | `15 public, 0 draft` |
| Links | `0 errored file, 0 errors` |
| Browser | Playwright Chromium and mobile Chromium, 12/12 tests |
| Accessibility | WCAG 2 A/AA, WCAG 2.1 A/AA, and WCAG 2.2 AA axe smoke checks passed |
| Production build | Next.js 16.2.11 Turbopack build generated all fifteen content and per-page Markdown routes |
| Repository docs | `python scripts/check_docs.py` passed for 277 Markdown files and seven mirrored domain pairs |
| Docker policy | `docker buildx build --check --file apps/docs/Dockerfile .` passed |
| Docker image | Full Node 24 image build passed |
| Runtime crawl | healthy as `nextjs`; 15/15 manifest routes and public assets returned 200; `/docs` returned 307; unknown docs route returned 404 |

Manual browser review covered the home page and System Description at 1440×1000
and 393×852. It found and fixed the missing favicon and confirmed that the
architecture flow stacks without horizontal overflow on mobile.

Image generation input:

- prompt intent: an abstract enterprise illustration of document evidence
  passing through a precise permission boundary into a knowledge constellation,
  using near-black navy, cobalt, muted cyan, and amber, with no text, logos,
  people, lock/shield imagery, or fake UI;
- repository asset:
  `apps/docs/public/images/governed-memory-hero.png`.

## PR 4 — Search, API, And Machine-Readable Docs

Branch: `feat/public-docs-discovery`

Base: `origin/main@a489a2db948c7ec9dd12919c7fefcaeb2173c467`

Delivered in [PR #112](https://github.com/kl3inIT/OrgMemory/pull/112),
merged as `eb870a905529a1cb7fc886dc1c189ecc21f5598d`. Aggregate CI run
`30394815922` passed, including Public docs, deployment contracts, and CI Gate.

Implementation evidence:

- the server-side Orama route returns useful results for Asset, OpenFGA,
  GraphRAG, MCP, and connector;
- two authored integration pages explain the deployment-scoped contract,
  session/CSRF flow, permission semantics, safe retries, and errors;
- Fumadocs OpenAPI generates seven domain pages from all 101 committed paths;
- the committed public OpenAPI input removes examples and private runtime
  servers, rejects private hosts, paths, and secret assignments, and uses only
  the reserved `api.example.invalid` origin;
- the API playground is disabled;
- generation drift is checked before validation and every production build;
- all 24 public routes have HTML, Open Graph, canonical, sitemap, search, and
  per-page Markdown coverage, plus aggregate `llms.txt` and `llms-full.txt`;
- runtime and client-output audits reject repository evidence metadata,
  private working paths, and Windows workspace paths;
- docs changes and `contracts/openapi.json` changes select the public-docs CI
  surface.

Fumadocs OpenAPI `11.2.2` is retained for compatibility with Fumadocs Core
`16.13.0`. Its published bundle contains four invalid inlined dependency
imports, and `@fumadocs/api-docs` contains one equivalent import. The repository
uses deterministic pnpm patches plus explicit exact dependencies until an
upstream fixed package is available.

Passed local gates:

| Gate | Evidence |
| --- | --- |
| Docs contracts | OpenAPI drift, lint, Fumadocs generation, Next route types, TypeScript, 24-page content, manifest, publication, routes, and links |
| Search | Asset, OpenFGA, GraphRAG, MCP, and connector return results |
| Route boundary | `24 public, 0 draft`; 17 authored and seven generated |
| OpenAPI | 101 sanitized paths grouped into seven generated pages; playground absent |
| Browser | Desktop and mobile navigation, generated API rendering, search, machine outputs, and accessibility passed |
| Production build | Next.js 16.2.11 Turbopack generated 82 static outputs, including 24 docs, Markdown, and OG routes |
| Client audit | `.next/static` contains no `sourceRefs` or repository evidence paths |
| Docker policy | `docker buildx build --check --file apps/docs/Dockerfile .` passed |
| Docker image | Full Node 24.14.0 image build passed with frozen lockfile and pnpm patches |
| Runtime crawl | healthy as `nextjs`; 24/24 manifest routes and all machine-readable/public assets returned 200 |

Context7 was attempted first and returned its quota-exhausted response. Current
official Fumadocs OpenAPI, Orama, and LLM-output documentation, official Next.js
metadata/sitemap documentation, installed package types, and production runtime
evidence were used instead.

## PR 5 — Production Delivery Readiness

Branch: `feat/public-docs-deployment`

Base: `origin/main@eb870a905529a1cb7fc886dc1c189ecc21f5598d`

Read-only preflight evidence:

- `/apps/orgmemory` exists on the ZM host;
- the existing `proxy-network` is available and the running Nginx Proxy Manager
  container is attached;
- Docker 29.5.1 and Compose 5.1.3 are available;
- approximately 10 GiB memory and 100 GiB disk were available;
- the existing product environment is protected with mode `0600`;
- the GitHub `production` environment has all five required SSH secret names;
- GHCR credentials are intentionally absent at rest and supplied temporarily by
  the deployment workflow;
- `docs.om.kl3in.tech` does not resolve, while DNS-provider and Nginx Proxy
  Manager configuration access remain unproven.

The final two facts are the explicit stop condition. No DNS, TLS, proxy,
production environment, container, or product-runtime mutation was performed.

Implementation evidence:

- `Build docs image` accepts only a full green `main` commit, publishes one
  immutable SHA tag with revision metadata, SBOM, provenance, the repository
  Trivy policy, and a digest manifest;
- `Deploy docs` is manual-only, requires an explicit confirmation, validates
  the green CI and docs-image run, uses verified SSH host keys, and has a
  separate production concurrency lock;
- the `orgmemory-docs` Compose project contains one service, exposes only port
  3000 to the existing proxy network, runs read-only/non-root without Linux
  capabilities, and enforces a 512 MiB memory limit;
- host state uses a mode-`0600` docs-only environment, separate runtime
  directory and lock, a previous-image record, and five retained release
  snapshots;
- failed canaries restore the last verified environment, start only
  `orgmemory-docs`, and rerun health/smoke checks;
- the publication verifier compares sitemap routes with both committed
  manifests and scans all 24 routes plus five public machine outputs.

Passed local gates:

| Gate | Evidence |
| --- | --- |
| Compose | docs-only interpolation passed with the committed example contract |
| Forced canary | previous immutable image restored; smoke ran twice; no product service command observed |
| Shell | ShellCheck 0.11.0 passed for all new Bash scripts |
| Workflows | actionlint 1.7.12 passed |
| Runtime | existing PR 4 image healthy as `nextjs`, read-only, 512 MiB limited; internal home, deep link, API, search, and LLM smoke passed |
| Publication | local proxy crawl matched all 24 allowlisted routes and five machine/public outputs |
| Python | publication verifier compiled under Python 3.13 |

Pending owner-controlled evidence:

- published GHCR docs release from the merged PR 5 commit;
- DNS, certificate, and Nginx Proxy Manager host;
- live health, mobile navigation, 24-route crawl, negative publication scan,
  product before/after health, and deployed-revision record.
