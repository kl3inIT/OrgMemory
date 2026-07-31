# OrgMemory — Repository Guidance

OrgMemory is a governed organizational memory layer for enterprise AI work.
This file is a table of contents and a set of durable workflow rules, not an
encyclopedia. Follow links for detail instead of duplicating it here.

## Documentation Map

- [ARCHITECTURE.md](ARCHITECTURE.md) — current stack, repository layout,
  cross-subproject contracts, build/run/test commands, and architectural
  conventions.
- [docs/vision.md](docs/vision.md) — product intent, scope, rationale, target
  architecture, and increment descriptions. Intent remains here after it is
  realized; architecture is authoritative for what currently exists.
- [docs/roadmap.md](docs/roadmap.md) — increment status, execution order, and
  future backlog.
- [docs/conventions.md](docs/conventions.md) — how code is written and committed.
- [docs/guidelines](docs/guidelines) — reusable framework, platform, security,
  delivery, and testing mechanics.
- [product release guideline](docs/guidelines/product-releases.md) — Tegami
  entries, semantic product releases, public-content safety, and recovery.
- [docs/decisions](docs/decisions) — append-only rationale for significant
  choices. A decision explains why; it is not a current-state source.
- [docs/specs/domains](docs/specs/domains) — current behavior and contracts by
  long-lived product domain.
- [docs/tests/domains](docs/tests/domains) — per-domain coverage and gaps,
  mirroring the domain specs one-to-one.
- [docs/increments/active](docs/increments/active) — authoritative design and
  execution plans for work in progress.
- [docs/increments/completed](docs/increments/completed) — immutable
  point-in-time history, never a source for current behavior.

## Reading Order

- Always read this map and the relevant sections of `ARCHITECTURE.md`.
- Before changing a domain, read its spec and test-coverage document, then scan
  decision filenames for rules binding that area.
- Before fixing a design, read `docs/vision.md` and the relevant guidelines.
- For active work, read `plan.md` first and open its `design.md` only when
  rationale or scope is needed.
- Do not use completed increment documents to answer what the system does now.
  Use them only for history or archaeology.
- In a subproject with its own `CLAUDE.md`, `ARCHITECTURE.md`, or `docs/`, read
  the root guidance first and then the subproject guidance. Subproject detail
  must not be restated at root.

## Increment Workflow

One increment is one coherent delivery cycle:

1. Brainstorm intent, constraints, and approach.
2. Write `docs/increments/active/YYYY-MM-DD-slug/design.md`, then `plan.md`.
   Mark the increment active in the roadmap.
3. Execute the plan and verify the narrowest useful gates while iterating.
4. Consolidate current behavior into the affected specs and test matrices,
   project-wide facts or commands into `ARCHITECTURE.md`, reusable mechanics
   into guidelines, and significant rationale into a decision.
5. Refresh every affected spec/test pair's `Source:` and `Reconciled:` lines.
6. Move the increment directory to `completed/` and update roadmap status.

Work smaller than an increment may skip design and plan, but never skips
consolidation when durable behavior changes.

High-impact, hard-to-reverse decisions about canonical domain boundaries,
authorization or trust boundaries, persistence ownership, publication
semantics, cache isolation, parity scope, or production topology require an
independent architecture challenge before implementation when the alternatives
remain materially contested. Routine implementation choices, established-pattern
extensions, documentation workflow, configuration, and reversible
non-production development tooling do not. Record the proposal, strongest
counterargument, repository evidence, final choice, and rejected alternative in
the active design or a decision. If the configured reviewer is unavailable,
follow the reviewer fallback in the architecture-challenge skill.

## Documentation Hygiene

- The repository, not chat or Northstar, is the engineering system of record.
  Northstar is the continuity layer; current repository and runtime evidence win.
- Every fact has one home. Link to it instead of restating it.
- `ARCHITECTURE.md` and specs contain implemented facts only. Intended or
  selected-but-unimplemented behavior remains in vision, roadmap, or an active
  increment.
- Vision records intent, not a second current-state answer. Roadmap records
  status and backlog, not plan subtasks or architecture.
- A spec states what a domain is. Cross-cutting framework or platform mechanics
  belong in guidelines. When uncertain, leave the statement in the spec until a
  second use proves it is a reusable mechanic.
- Test matrices state what is covered, how, and what remains unverified.
  Testing strategy and harness mechanics belong in the testing guideline.
- Decisions are immutable except for status. Supersede an old decision with a
  new entry; never rewrite its body to match the present.
- Active design/plan files stop being authoritative when moved to completed.
- Every domain spec and mirrored test document starts with source paths and a
  reconciliation commit. If
  `git log <sha>..HEAD -- <source paths>` is non-empty, code has moved past the
  document; the code wins and reconciliation is part of the current change.

## Framework Verification And Safety

Before unfamiliar Spring Boot 4, Spring Modulith 2, Spring AI 2, Gradle, React,
Vite, Tailwind, TypeScript, Next.js, or Fumadocs APIs, use Context7 or current
official documentation and the relevant project verification skill.

Project-owned skills are canonical under `.agents/skills`; `.claude` and
`.codex` contain discovery wrappers only.

Read [agent safety](docs/guidelines/agent-safety.md) before retrieval, AI, MCP,
permission, upload, graph, or export work. Never commit secrets or customer
data. Keep `ddl-auto=validate` and pair persisted-model changes with Flyway.

Use [the testing harness](docs/guidelines/testing-harness.md). A terminating
clean test is the JVM context gate; `bootRun` is not verification. IDE
inspection applies only to edited backend Java. Frontend gates are lint,
typecheck, tests, production build, and browser verification where the flow
matters.
