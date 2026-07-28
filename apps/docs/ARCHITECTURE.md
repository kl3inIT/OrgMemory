# Documentation Application Architecture

`apps/docs` is an independent Next.js 16 application using Fumadocs UI 16 and
Fumadocs MDX 15. It owns the public reader experience and has no runtime
dependency on the product API, worker, MCP server, CLI, or Vite web application.

## Current Boundaries

- `content/docs`: curated Markdown/MDX publication input.
- `source.config.ts`: typed Fumadocs MDX collection and frontmatter contract.
- `public-content.manifest.json`: explicit route and review allowlist.
- `scripts/check-docs.mjs`: content, manifest, source-reference, and publication
  policy gates.
- `src/lib/source.ts`: Fumadocs loader and draft filtering.
- `src/app`: home, docs, health, search, Open Graph, and machine-readable routes
  provided by the current Fumadocs foundation.

The root pnpm workspace owns dependency installation and the lockfile.
Turbopack is Next.js 16's default bundler; no explicit flag or Turborepo layer is
required.

## Publication Invariant

Internal `docs/`, increments, research, raw runbooks, demo material, secrets, and
private infrastructure values are denied by default. A fact becomes public only
after it is audience-authored under `content/docs`, allowlisted, source-traced,
reviewed, and verified by the package gates.
