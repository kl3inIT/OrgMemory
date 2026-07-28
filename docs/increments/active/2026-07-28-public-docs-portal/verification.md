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
