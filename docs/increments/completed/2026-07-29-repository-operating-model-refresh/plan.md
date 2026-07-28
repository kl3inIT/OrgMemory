# Repository Operating Model Refresh Plan

## 1. Adopt The Durable Harness

- [x] Expand `CLAUDE.md` into a thin documentation map with deterministic
  reading order, increment workflow, consolidation, and hygiene rules.
- [x] Keep code/contribution conventions in `docs/conventions.md`; move process
  policy out of it.
- [x] Clarify active versus completed increment authority in their indexes.

## 2. Reconcile Durable Documentation

- [x] Refresh current module, runtime, dependency, and version facts in
  `ARCHITECTURE.md`.
- [x] Add an explicit intent/current-state boundary to `docs/vision.md` and
  remove stale current-state claims without deleting product intent.
- [x] Reduce roadmap duplication while preserving delivery status, execution
  order, and the future backlog.
- [x] Consolidate overlapping graph and browser-auth documents.
- [x] Make `docs/specs/domains` and `docs/tests/domains` mirror one-to-one.
- [x] Add `Source:` and `Reconciled:` provenance to every durable pair.
- [x] Reconcile active-plan status from merged repository evidence; keep live
  gates open where current evidence is absent.

## 3. Make Drift Detectable

- [x] Add a dependency-free documentation checker for links, increment shape,
  spec/test symmetry, provenance, and merge-conflict markers.
- [x] Add a path-aware docs CI job and include it in the aggregate gate.
- [x] Run the checker and `git diff --check`.

## 4. Consolidate And Close

- [x] Record verification evidence beside this plan.
- [x] Move the increment directory to `docs/increments/completed/`.
- [x] Update the roadmap and increment indexes without leaving a second current
  status source.
- [x] Commit only the operating-model/docs audit scope plus the pre-existing
  verified documentation status changes; preserve unrelated untracked files.
