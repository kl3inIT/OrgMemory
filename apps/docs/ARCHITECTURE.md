# Documentation Application Architecture

`apps/docs` is an independent Next.js 16 application using Fumadocs UI 16 and
Fumadocs MDX 15. It owns the public reader experience and has no runtime
dependency on the product API, worker, MCP server, CLI, or Vite web application.

## Current Boundaries

- `content/docs`: curated Markdown/MDX publication input. English pages use
  `page.mdx`; reviewed Vietnamese translations use `page.vi.mdx`.
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
- `next.config.mjs`: standalone output plus application-owned response security
  headers. The reverse proxy owns TLS and routing, not duplicate header policy.

The root pnpm workspace owns dependency installation and the lockfile.
Turbopack is Next.js 16's default bundler; no explicit flag or Turborepo layer is
required.

The five root folders are the reader's high-level mode switcher: Start Here,
System Design, Deploy & Operate, Govern & Administer, and Build & Integrate.
Their Vietnamese labels come from adjacent `meta.vi.json` files. A Vietnamese
route may inherit reviewed English content until its matching `.vi.mdx` is
authored; the reader shows that fallback state explicitly.

## Publication Invariant

Internal `docs/`, increments, research, raw runbooks, demo material, secrets, and
private infrastructure values are denied by default. A fact becomes public only
after it is audience-authored under `content/docs`, allowlisted, source-traced,
reviewed, and verified by the package gates.
