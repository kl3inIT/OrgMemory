---
name: orgmemory-reference-study
description: Ground OrgMemory design, porting, and parity work in pinned local reference source (tmp/onyx, tmp/upstream-*, the Northstar codebase, Gradle-cache library sources) instead of asserting from memory. Use before designing a feature a comparable product already has, during any LightRAG parity work, or when framework internals are unclear. Do not use for simple API symbol checks (orgmemory-verify-api-symbol) or for current official docs lookups (Context7).
---

# OrgMemory Reference Study

"Read the source, don't assert" is a standing rule in this project. Design and
parity claims must cite a pinned local reference; the user repeatedly rejects
answers that guess at what Onyx, LightRAG, or Spring AI do.

## Purpose

Make design decisions and ports evidence-based by reading comparable systems
and library internals from pinned local checkouts, and record what was learned
with file-level citations.

## Trigger Conditions

- Designing a feature a comparable product has (connectors, assistant history,
  admin UI, docs site, model-provider gateway, SCIM, telemetry policy).
- Any LightRAG parity or port question.
- A framework behaves in a way its docs don't fully explain, or an
  architecture challenge needs a comparable-system evidence table.
- The user says "học bên onyx / tham khảo / bên khác làm thế nào" (learn from
  Onyx / check how others do it) or "judge" a design against real systems.

## Required Context

Reference layout (resolve the repository root dynamically and verify every
path before citing it; clone a pin under `<repo-root>/tmp` when missing):

- `<repo-root>/tmp/onyx` — Onyx (ex-Danswer): connectors, assistant pipeline,
  admin UI, SCIM, tracing.
- `<repo-root>/tmp/upstream-lightrag-v1.5.4` — pinned LightRAG parity oracle.
  Parity is against this pin, never against latest upstream.
- Other `<repo-root>/tmp/upstream-*` pins as created per topic (e.g. sentry-mcp).
- A verified Northstar checkout (commonly a sibling directory or `/apps/northstar`)
  — useful for Spring AI patterns already verified in production.
- `~/.gradle/caches/modules-2/files-2.1/...` — library jars and `-sources.jar`
  for Spring Boot / Spring AI internals.

## Procedure

1. **Pick the reference before writing the design.** For product behavior,
   Onyx first; for LightRAG semantics, the pinned upstream; for Spring AI
   usage, Northstar plus official docs; for policy questions, several systems.
2. **Clone missing references into `tmp/`** (never commit them; `tmp/` is
   reference space). Note the cloned revision.
3. **Read the actual implementation**, not the README: find the feature's
   entry points with `rg`, follow the data model, and record file paths for
   every claim.
4. **For framework internals, unzip the source jar** from the Gradle cache
   into the scratchpad and read it (e.g. auto-configuration classes to learn
   real defaults and opt-in/opt-out behavior). If no sources jar exists, use
   Context7 or fetch the upstream tag.
5. **For policy/architecture decisions, compare at least two systems** and
   build an evidence table (system, behavior, mechanism, file citation) — this
   feeds `orgmemory-architecture-challenge` briefs directly.
6. **Judge, don't mirror.** State where the reference is better, where
   OrgMemory's constraints (permission ceiling, fail-closed retrieval,
   tenancy) forbid copying it, and where the user's current design is
   over-engineered. The user explicitly asks to be judged, not praised.
7. **Re-verify stale research.** Existing `docs/research/*` notes are leads,
   not truth — verify against the current pin before reusing ("don't trust old
   research docs; take them as reference and verify").

## Verification

Every design claim sourced this way carries a concrete path into a reference
checkout (or a jar/source citation). Parity statements name the pinned
version. Learnings that changed a decision are consolidated per CLAUDE.md.

## Failure Handling

- **Reference missing or moved:** re-clone into `tmp/` and note the revision;
  do not fall back to memory of the codebase.
- **Reference contradicts the product's security model:** surface the
  conflict; OrgMemory's fail-closed rules win over reference fidelity.
- **Two references disagree:** present both mechanisms with citations and a
  recommendation instead of silently picking one.
