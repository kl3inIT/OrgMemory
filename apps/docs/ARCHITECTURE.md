# Documentation Application Architecture

`apps/docs` is an independent Next.js 16 application using Fumadocs UI 16 and
Fumadocs MDX 15. It owns the public reader experience and has no runtime
dependency on the product API, worker, MCP server, CLI, or Vite web application.
Local authoring therefore starts only this package and uses Next.js hot reload;
no product environment file, database, identity provider, authorization
service, or Compose stack is required.

## Current Boundaries

- `content/docs`: curated Markdown/MDX publication input. English section roots
  use `index.mdx`, named pages use `<slug>.mdx`, and reviewed Vietnamese
  translations use the adjacent `index.vi.mdx` or `<slug>.vi.mdx`.
- `source.config.ts`: typed Fumadocs MDX collection and frontmatter contract.
- `public-content.manifest.json`: explicit route and review allowlist.
- `scripts/check-docs.mjs`: content, manifest, source-reference, and publication
  policy gates.
- `src/lib/i18n.ts`: English/Vietnamese locale contract, UI translations, and
  stable URL helpers. English keeps `/docs/...`; Vietnamese uses `/vi/docs/...`.
- `src/lib/source.ts`: locale-aware Fumadocs loader, English fallback, and draft
  filtering.
- `src/proxy.ts`: one-pass locale routing plus Markdown content negotiation.
  It is colocated with `src/app` so Next.js loads the proxy.
- `src/app`: locale-scoped reader, Open Graph, and machine-readable routes plus
  global health, search, sitemap, and robots handlers.
- `public/images/architecture`: reviewed architecture visuals with adjacent
  captions and useful alternative text in the owning MDX pages.
- `next.config.mjs`: standalone output and application-owned response security
  headers. The reverse proxy owns TLS and routing and caches only immutable
  `/_next/static/` assets.

The root pnpm workspace owns dependency installation and the lockfile.
Turbopack is Next.js 16's default bundler; no explicit flag or Turborepo layer is
required.

The current root folders are the reader's high-level mode switcher: Getting
Started, Product Guides, Architecture & Security, and Reference.
Administration, deployment/operations, and integration sections are not
published until their first replacement pages are co-authored and reviewed.
Vietnamese labels come from adjacent `meta.vi.json` files. A Vietnamese route
may inherit reviewed English content until its matching `.vi.mdx` is authored;
the reader shows that fallback state explicitly.

Each root also has a stable visual identity shared by English and Vietnamese.
Localized metadata owns its label, description, and Lucide icon;
`src/lib/docs-category.ts` maps the stable folder ID to a body class;
`DocsLayout.tabs.transform` colors the root icon; and `global.css` maps that
class to an accessible light/dark `--color-fd-primary`. Page backgrounds and
content remain neutral, so category color communicates location without turning
the reader into a marketing surface.

CI Playwright starts the generated standalone server with the same public and
static-asset layout used by the container. Response-header and
representation-routing checks therefore cover the deployed Linux runtime
rather than the development server. Windows uses `next start` because traced
pnpm symlinks in the Linux-oriented standalone output are not executable there.

Published document URLs have one representation: `/docs/...` and
`/vi/docs/...` always return HTML, while their explicit `.md` siblings return
Markdown. The reverse proxy therefore never has to cache-vary one document URL
by `Accept`.

## Publication Invariant

Internal `docs/`, increments, research, raw runbooks, demo material, secrets, and
private infrastructure values are denied by default. A fact becomes public only
after it is audience-authored under `content/docs`, allowlisted, source-traced,
reviewed, and verified by the package gates.
