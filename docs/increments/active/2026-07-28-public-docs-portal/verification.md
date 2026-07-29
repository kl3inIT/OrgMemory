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

Base: `origin/main@dfb4be455947bd9b0b2ead7e1b18a9a662f0253b`

Owner hostname update:

- the temporary public origin is `https://docs.kl3in.tech`;
- application metadata, sitemap, robots, SCIM discovery, deployment workflow,
  environment example, runbook, design, and plan use the same origin;
- Fumadocs metadata routes share one source constant, and SCIM discovery links
  to the published identity and permissions page rather than an absent route;
- the previous nested hostname has no remaining repository reference and is not
  an alias or secondary canonical.

Delivered in [PR #115](https://github.com/kl3inIT/OrgMemory/pull/115),
merged as `1178c5e19f5d6ad84107a8cb9a93f886b302c529`. PR CI run
`30396857472` and merge-commit CI run `30397138921` passed every selected job
and `CI Gate`.

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
- automatic product deployment run `30399267433` later timed out connecting
  from its GitHub-hosted runner before SSH authentication; its deploy step was
  skipped and no runtime mutation occurred;
- follow-up run `30400831397` reached the same host and successfully deployed
  the product commit `c5d4797939fb93b7c95ed6516b29bc03804179fc`; public
  product health returned `200 ok`, all product containers were healthy, the
  ephemeral GHCR config was removed, and the docs service remained absent;
- Cloudflare and Google public resolvers return the ZM ingress address for
  `docs.kl3in.tech`; HTTP reaches Nginx Proxy Manager's OpenResty edge but
  returns `404`, and HTTPS does not yet present a valid certificate.

The missing proxy host and TLS are the explicit stop condition. No TLS, proxy,
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

Published release evidence:

- `Build docs image` run `30397457422` completed successfully for the exact
  merge commit;
- image:
  `ghcr.io/kl3init/orgmemory-docs:sha-1178c5e19f5d6ad84107a8cb9a93f886b302c529`;
- digest:
  `sha256:7f5c7e894adca3b207d496c7cb536f7eb35f606964323b51a07ef1d87af0d5a5`;
- the published image was pulled by immutable tag and its digest and OCI
  revision matched the release manifest;
- the exact image ran locally as `nextjs` with a read-only root filesystem and
  512 MiB memory limit, became healthy, and passed the 24-route plus five-output
  publication verifier.

Delivery hardening evidence:

- [PR #116](https://github.com/kl3inIT/OrgMemory/pull/116), merged as
  `24ad7600df843dcd52eed5830852e77a6d7901cc`, restored the pnpm patch inputs to
  the web image build context. PR CI run `30398075432`, merge CI run
  `30398397065`, and production image run `30398668686` passed;
- [PR #117](https://github.com/kl3inIT/OrgMemory/pull/117), merged as
  `c5d4797939fb93b7c95ed6516b29bc03804179fc`, added bounded fail-fast SSH and
  run-scoped GHCR credentials with remote exit cleanup. PR CI run
  `30400189816`, merge CI run `30400496867`, production image run
  `30400748095`, and product deployment run `30400831397` passed;
- [PR #118](https://github.com/kl3inIT/OrgMemory/pull/118), merged as
  `d3acfac409ca393206a7574a13cc79e5648899d3`, made commits with no product image
  input changes release no-ops. PR CI run `30401379312`, merge CI run
  `30401660634`, the final workflow-change image run `30401899319`, and its
  exact product deployment run `30402079951` passed;
- the final product runtime reported commit
  `d3acfac409ca393206a7574a13cc79e5648899d3`; API, Keycloak, MCP, and web were
  healthy, worker was running, `https://om.kl3in.tech/healthz` returned
  `200 ok`, the default Docker config contained no GHCR credential, and no docs
  container existed;
- the docs-only commit containing this evidence is the release-isolation probe:
  production image carry-forward, immutable-set release, and product SSH
  deployment must all remain skipped.

Pending owner-controlled evidence:

- certificate and Nginx Proxy Manager host;
- live health, mobile navigation, 24-route crawl, negative publication scan,
  product before/after health, and deployed-revision record.
