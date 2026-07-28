# Polyglot Apps Workspace Plan

Execute the accepted [design](design.md) from
`origin/main@9c3e5c96e737542193071e3ad772588fda784cab` in the isolated
`feat/apps-workspace-docs` worktree.

## Phase 1 — Record The Structural Baseline

- [x] Verify the worktree is clean and based on the intended `origin/main`.
- [x] Inventory package manifests, lockfiles, CI filters, Docker inputs,
  deployment scripts, documentation provenance, and current `web/` references.
- [x] Verify pnpm workspace, Fumadocs MDX, Next.js, and Turbopack behavior
  against current official documentation. Context7 was attempted first but its
  quota was exhausted; official project documentation is the fallback evidence.
- [x] Record scope, counterargument, final choice, and rejected alternatives.

Gate: design and plan exist before filesystem moves or scaffolding.

## Phase 2 — Normalize The Workspace

- [x] Move tracked `web/` to `apps/web/` with history-preserving Git changes.
- [x] Rename the package to `@orgmemory/web`.
- [x] Add the private root `package.json` and explicit
  `pnpm-workspace.yaml`.
- [x] Merge CLI and web dependency graphs into one root lockfile and remove
  package-local lockfiles/workspace configuration.
- [x] Add root filtered scripts for CLI, web, and docs gates.
- [x] Update ignore rules and package-local guidance.

Gate:

```powershell
corepack pnpm install --frozen-lockfile
corepack pnpm --filter @orgmemory/cli typecheck
corepack pnpm --filter @orgmemory/cli test
corepack pnpm --filter @orgmemory/cli build
corepack pnpm --filter @orgmemory/web check:api
corepack pnpm --filter @orgmemory/web typecheck
corepack pnpm --filter @orgmemory/web test:unit
corepack pnpm --filter @orgmemory/web build
```

## Phase 3 — Establish The Docs Application

- [x] Scaffold `apps/docs` from the current official Fumadocs Next.js shape.
- [x] Pin the supported Node/pnpm/Next.js/Fumadocs/React baseline.
- [x] Configure Fumadocs MDX, typed frontmatter, explicit navigation, home/docs
  layouts, and `/healthz`.
- [x] Add the minimal public manifest and placeholder content.
- [x] Add manifest/content/publication/source-reference validation.
- [x] Configure standalone output and the non-root multi-stage Dockerfile.
- [x] Add thin package guidance and architecture.

Gate:

```powershell
corepack pnpm --filter @orgmemory/docs typecheck
corepack pnpm --filter @orgmemory/docs check:content
corepack pnpm --filter @orgmemory/docs check:manifest
corepack pnpm --filter @orgmemory/docs check:publication
corepack pnpm --filter @orgmemory/docs build
docker build --file apps/docs/Dockerfile .
```

## Phase 4 — Rewire Automation And Operations

- [x] Update CI path filters and install Node dependencies once at workspace
  root.
- [x] Add an independent docs CI job to the aggregate `CI Gate`.
- [x] Update production web image filters and Dockerfile path.
- [x] Update the web Dockerfile and forwarded-port contract test.
- [x] Update CodeRabbit and all other current automation references.
- [x] Validate workflow syntax, Dockerfiles, Compose interpolation, and shell
  scripts.

Gate:

```powershell
python scripts/check_docs.py
go run github.com/rhysd/actionlint/cmd/actionlint@v1.7.12
docker buildx build --check --file apps/web/Dockerfile .
docker buildx build --check --file apps/docs/Dockerfile .
```

## Phase 5 — Consolidate And Close

- [x] Reconcile `ARCHITECTURE.md`, `README.md`, conventions, roadmap, and
  affected spec/test provenance to the new paths.
- [x] Confirm current files contain no stale live `web/` reference while
  preserving immutable completed history.
- [x] Run the full Gradle suite, all Node package gates, browser tests,
  documentation checks, Docker builds, and deployment contract tests.
- [x] Record exact verification evidence in `verification.md`.
- [x] Mark the corresponding foundation items in the public docs portal plan
  complete only where evidence exists.
- [ ] Move this increment to `completed/` and update roadmap status.
- [ ] Commit, push, open a pull request, require green CI, merge, and verify
  `origin/main`.
- [ ] Record the durable checkpoint in Northstar.

Completion gate: every checked item has command, commit, CI, or runtime evidence;
no docs production claim is made.
