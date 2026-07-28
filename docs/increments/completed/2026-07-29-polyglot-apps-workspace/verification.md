# Polyglot Apps Workspace Verification

## Structural Evidence

- Worktree: `D:\OrgMemory-worktrees\apps-workspace-docs`
- Branch: `feat/apps-workspace-docs`
- Base: `origin/main@9c3e5c96e737542193071e3ad772588fda784cab`
- Foundation commit: `7acda3a`
- Root workspace: pnpm 11.9.0 with `apps/cli`, `apps/web`, and `apps/docs`
- Docs baseline: Node 24, Next.js 16.2.11, Fumadocs UI 16.13.0,
  Fumadocs MDX 15.2.0, React 19.2.8

Context7 was called first but returned its quota-exhausted response. Current
official Fumadocs, Next.js, and pnpm documentation plus the installed dependency
types were used instead. Verified unfamiliar symbols included `pageSchema`,
`defineDocs`, `toFumadocsSource`, Fumadocs layout link options, standalone
Next.js output, and the current template routes.

## Passed Gates

| Surface | Evidence |
| --- | --- |
| Root install | `corepack pnpm install --frozen-lockfile` |
| CLI | typecheck, 4 files / 24 tests, build |
| Product web | generated API drift check, typecheck, 6 files / 20 unit tests, production Vite build |
| Product browser | Playwright Chromium, 11/11 journeys passed |
| Public docs | Oxlint, Fumadocs generation, Next route types, TypeScript, content, manifest, publication policy, Turbopack production build |
| JVM | `.\gradlew.bat --no-daemon clean test`, 102 tasks, successful |
| Repository docs | `python scripts/check_docs.py`, 275 Markdown files and 7 mirrored domain pairs |
| Workflows | actionlint 1.7.12 passed |
| Docker static | Buildx checks passed for `apps/web/Dockerfile` and `apps/docs/Dockerfile` |
| Product web image | full Node 24 build and unprivileged Nginx image build passed |
| Public docs image | full Node 24 standalone image build passed after excluding host dependency/build output from the Docker context |
| Docs runtime | `/healthz`, `/`, and `/docs` returned 200; runtime user was `nextjs` |
| Deployment contract | Git Bash forwarded-port regression passed; Compose docs profile interpolated successfully |
| Mechanical floor | zero missing Java package declarations, zero empty source/config/migration files, zero misnamed Flyway migrations |
| Diff hygiene | `git diff --check` passed |

JetBrains MCP inspection was unavailable. The only changed Java file contains a
documentation-command correction in a test comment; the full Gradle suite and
mechanical floor passed.

## Observations

- The workstation Node runtime is 23.11.1, so local pnpm commands emit the
  intentional `node >=24` engine warning. Both Docker builds used the pinned
  Node 24 image; CI also uses Node 24.
- The existing Vite build still reports large lazy chunks. The structural move
  did not introduce that condition, and bundle optimization remains a separate
  measured frontend concern.
- Fumadocs' current official scaffold includes self-hosted search, Open Graph,
  `llms.txt`, full-corpus, and per-page Markdown routes. The foundation keeps
  these routes bound to the draft-filtered source; substantive search tuning,
  API reference generation, and full output auditing remain in the public docs
  portal plan.

## Delivery Evidence

- Pull request: [#103](https://github.com/kl3inIT/OrgMemory/pull/103)
- CI run: `30386700211`
- CI result: all required jobs passed, including `Public docs · Node 24`,
  `Web · Node 24`, `Deployment contracts`, and the aggregate `CI Gate`.
- Merge commit: `3d02828ffddff32d243d1f2cc6f5c0820ae93966`
- Remote verification: `origin/main` resolved to the merge commit after fetch.
- Northstar checkpoint: `orgmemory-repository-operating-model-checkpoint-2026-07-29`
  (note id `92e42df8-7319-4190-9aec-30abc2e0434b`).

The public documentation portal remains active. Its next gate is the reviewed,
Onyx-style information architecture and public corpus; API reference
generation, search/output auditing, and production deployment follow that
content boundary.
