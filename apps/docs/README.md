# OrgMemory Documentation

Independent public documentation application built with Next.js, Fumadocs UI,
and Fumadocs MDX. Repository `docs/` remains the internal engineering system of
record; only reviewed content under `apps/docs/content/docs/` is publishable.

Run commands from the repository root:

```powershell
corepack pnpm install --frozen-lockfile
corepack pnpm --filter @orgmemory/docs dev
corepack pnpm --filter @orgmemory/docs check
corepack pnpm --filter @orgmemory/docs build
```

Set `DOCS_INCLUDE_DRAFTS=true` only for a local or explicitly controlled preview.
Production uses the public entries in `public-content.manifest.json`.
