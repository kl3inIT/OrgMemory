# CLAUDE.md

OrgMemory is a governed organizational memory layer for enterprise AI work.
This file is a thin map for coding agents.

## Read First

- [ARCHITECTURE.md](ARCHITECTURE.md): current repository-proven system facts.
- [docs/vision.md](docs/vision.md): product and intended architecture.
- [docs/roadmap.md](docs/roadmap.md): shipped, active, next, and deferred work.
- [docs/conventions.md](docs/conventions.md): repository conventions.
- [docs/guidelines](docs/guidelines): cross-cutting mechanics and safety.
- [docs/decisions](docs/decisions): accepted rationale.
- [docs/specs](docs/specs): current behavior by domain.
- [docs/tests](docs/tests): evidence and gaps mirroring the specs.
- [docs/increments/active](docs/increments/active): active designs and plans.

Current behavior belongs in architecture/specs only after it exists in code.
Intent stays in vision, roadmap, or an active increment. Do not duplicate state.

## Increment Workflow

Create `docs/increments/active/YYYY-MM-DD-slug/design.md`, then `plan.md`.
Execute one coherent slice, consolidate current facts/specs/tests/decisions, and
move the increment to `completed`. Completed increments are historical evidence.

Before unfamiliar Spring Boot 4, Spring Modulith 2, Spring AI 2, Gradle, React,
Vite, Tailwind, or TypeScript APIs, use Context7/current official docs and the
project `orgmemory-*` verification skills.

## Safety And Verification

Read [agent safety](docs/guidelines/agent-safety.md) before retrieval, AI, MCP,
permission, upload, graph, or export work. Never commit `.env`, provider keys,
tokens, or customer data. Keep `ddl-auto=validate`; pair JPA changes with Flyway.

Run the relevant gates in [testing harness](docs/guidelines/testing-harness.md).
Use a terminating clean test as the context gate; `bootRun` is not verification.
JetBrains IDE inspection is a Java-backend gate only. For frontend files, use
Oxlint, TypeScript typecheck, the production build, and browser tests when the UI
flow matters; do not run IDE inspection on TypeScript, TSX, or web configuration.
