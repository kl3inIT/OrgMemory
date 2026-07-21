# 0001 — Repository Is The Engineering System Of Record

## Status

Accepted on 2026-07-20.

## Context

Product, status, architecture, permission, startup, and roadmap documents
repeated the same claims and drifted from code. Northstar's harness keeps a thin
map and separates current facts, intent, behavior, decisions, and active work.

## Decision

Use `CLAUDE.md` as a map. Current system facts live in `ARCHITECTURE.md`; current
behavior in specs; verification in tests; intent in vision/roadmap; rationale in
decisions; implementation detail in one active increment. Northstar notes retain
discovery continuity but do not override the observed repository.

## Consequences

Legacy summary documents are removed after consolidation. Contributors update
one source of truth at the natural checkpoint instead of appending another status
file.
