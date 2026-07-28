# Polyglot Apps Workspace Design

## Decision

Normalize all independently runnable product surfaces under `apps/`, while
keeping the existing Gradle multi-project build at the repository root:

```text
apps/
├── api/       Spring Boot REST application
├── worker/    Spring Boot ingestion and projection worker
├── mcp/       Spring AI MCP server
├── cli/       Node.js command-line client
├── web/       Vite/React product application
└── docs/      Next.js/Fumadocs public documentation application
```

Add a root `pnpm-workspace.yaml`, root `package.json`, and one shared
`pnpm-lock.yaml` for the three JavaScript packages. Java applications remain
Gradle subprojects and are not converted into Node packages.

This repository is already a monorepo because one Git repository owns several
deployables, shared domain code, integrations, contracts, infrastructure, and
documentation. The change makes the deployable boundary visually consistent;
it does not introduce the monorepo property.

## Context

The current repository already follows a production-oriented Java multi-project
shape: root Gradle configuration owns thin deployables in `apps/api`,
`apps/worker`, and `apps/mcp`, reusable behavior in `core` and `components`, and
external adapters in `integrations`. The Node CLI is already under `apps/`, but
the product web application still lives at root `web/` with its own pnpm
workspace and lockfile.

The public docs design selected an independent Fumadocs application at
`apps/docs`. Creating it before resolving package ownership would add a third
isolated Node install and duplicate CI/cache conventions.

Current official guidance supports the selected baseline:

- pnpm 11 requires `pnpm-workspace.yaml` at the workspace root and uses one
  shared root lockfile by default;
- Fumadocs MDX supports a Next.js application with `source.config.ts`,
  `content/docs`, and a generated type-safe collection;
- Next.js 16 uses Turbopack by default for both `next dev` and `next build`.

Turbopack is only the Next.js bundler for `apps/docs`. It is not a workspace
manager, does not replace pnpm or Gradle, and is not added to the Vite product
application. No Turborepo task runner is introduced: three small Node packages
and existing path-aware CI do not yet justify another orchestration layer.

## Scope

### Included

- move `web/` to `apps/web/` without changing product behavior;
- introduce the root pnpm workspace and one shared lockfile;
- preserve package-local commands through root filtered scripts;
- scaffold the independent Next.js/Fumadocs application at `apps/docs`;
- establish typed MDX content, a minimal public manifest, draft-safe
  publication validation, health route, and production build;
- add thin `CLAUDE.md` and `ARCHITECTURE.md` maps for `apps/web` and
  `apps/docs`;
- update CI, image builds, Docker paths, deployment tests, generated-client
  paths, ignore rules, and living documentation;
- keep the docs container independent from the product runtime.

### Excluded

- substantive authoring of the fifteen public-release pages;
- search, generated OpenAPI reference, `llms.txt`, or public deployment;
- DNS, TLS, Nginx Proxy Manager, or production docs credentials;
- refactoring Java packages or Gradle project boundaries;
- changing the product web UI, API contract, or runtime behavior;
- adding shared frontend component packages before a real cross-app reuse case
  exists.

## Package And Command Model

The root package is private and owns only workspace-wide convenience scripts.
Each application continues to declare its own runtime and build dependencies.

```text
pnpm install --frozen-lockfile
pnpm --filter @orgmemory/cli test
pnpm --filter @orgmemory/web build
pnpm --filter @orgmemory/docs build
```

CI installs once from the repository root and then runs package-filtered gates.
The workspace contains only explicit Node package paths:

```yaml
packages:
  - apps/cli
  - apps/web
  - apps/docs
```

The Java directories under `apps/` are therefore not accidental pnpm packages.
No workspace package currently depends on another; if that changes, local
dependencies must use `workspace:` ranges.

## Path Migration Contract

The move is complete only when current operational references use `apps/web`:

- CI filters, working directories, and pnpm cache dependency paths;
- production image path filters and Dockerfile selection;
- Docker `COPY` inputs and generated API client output;
- forwarded-port deployment contract test;
- `.gitignore`, `.dockerignore`, CodeRabbit path rules, root commands, specs,
  test matrices, and current architecture.

Completed increment documents remain immutable historical evidence and retain
the paths that were true at their completion time.

## Fumadocs Foundation

`apps/docs` is a separate Next.js application, not a page inside the Vite
product frontend. The foundation includes:

- App Router and Fumadocs UI layouts;
- Fumadocs MDX collection rooted at `content/docs`;
- explicit `meta.json` ordering;
- frontmatter fields `title`, `description`, `audience`, `status`,
  `sourceRefs`, and `lastReviewed`;
- a committed `public-content.manifest.json` allowlist;
- checks for manifest/content agreement and existing `sourceRefs`;
- a placeholder overview page that explains the portal is under construction;
- `/healthz`;
- standalone Next.js output and a non-root container build.

The placeholder is foundation evidence, not one of the fifteen reviewed
first-release pages. Public content authoring remains in the existing
`2026-07-28-public-docs-portal` increment.

## Architecture Challenge

### Strongest counterargument

Moving `web/` and creating `apps/docs` together increases the changed-file
surface and can make failures harder to attribute. Keeping separate lockfiles
would also reduce immediate CI and Docker edits.

### Repository evidence

Both changes alter the same package-manager ownership, root lockfile, pnpm cache,
CI filter, and `apps/` documentation. Creating docs first with an isolated
lockfile would intentionally produce a transient layout that the next increment
must undo. The product web move itself is mechanical and its current full CI,
browser, Docker, and forwarded-port gates already provide regression coverage.

### Final choice

Perform one structural increment containing the mechanical web move, root
workspace, and non-substantive docs foundation. Keep public information
architecture, content, search, API generation, and deployment in the existing
separately reviewable portal program.

### Rejected alternatives

- Keep `web/` at root: preserves an inconsistent deployable layout and a second
  workspace root.
- Put public content directly in repository `docs/`: mixes internal engineering
  records with the publication boundary.
- Add Turborepo now: adds cache/task semantics without a measured bottleneck or
  shared package graph.
- Convert Gradle projects into pnpm packages: conflates independent build
  ecosystems and provides no operational benefit.

## Exit Criteria

- the exact tree above exists and root pnpm install is reproducible;
- CLI, product web, and docs package gates pass from the root workspace;
- the existing web browser suite, Docker build, and forwarded-port test pass
  after the move;
- the full Gradle test suite remains green;
- docs manifest/content/source-reference checks and production build pass;
- current documentation and automation contain no live `web/` path;
- completed increments are untouched except for links required to remain valid;
- no public production deployment is attempted.
