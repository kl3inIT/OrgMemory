# Organizational AI Memory Documentation

Independent public documentation application built with Next.js, Fumadocs UI,
and Fumadocs MDX. Repository `docs/` remains the internal engineering system of
record; only reviewed content under `apps/docs/content/docs/` is publishable.

Run commands from the repository root:

```powershell
corepack pnpm install --frozen-lockfile
```

Start the local reader in its own terminal:

```powershell
corepack pnpm docs:dev
```

Run verification in another terminal:

```powershell
corepack pnpm --filter @orgmemory/docs check
corepack pnpm --filter @orgmemory/docs build
```

Open `http://localhost:3000`. The docs application is intentionally
local-first and independent: it does not require the product API, worker, MCP
server, database, Keycloak, OpenFGA, or product Compose stack. Dependency
installation is the only first-run setup; Next.js hot reload handles normal
page-by-page review.

Set `DOCS_INCLUDE_DRAFTS=true` only for a local or explicitly controlled preview.
Production uses the public entries in `public-content.manifest.json`.

PowerShell draft preview:

```powershell
$env:DOCS_INCLUDE_DRAFTS = 'true'
corepack pnpm docs:dev
# Press Ctrl+C to stop the preview server before cleaning the parent shell.
Remove-Item Env:DOCS_INCLUDE_DRAFTS
```
