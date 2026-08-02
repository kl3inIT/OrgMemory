# Typed Knowledge Space Audience Modes — Plan

## Status

Active.

## Steps

- [x] Reuse the recorded independent challenge and consolidate it into ADR
  0029.
- [x] Add failing characterization tests for typed creation, immutable built-in
  audiences, restricted-custom grants, and independent operational/read
  permissions.
- [x] Add the versioned persisted mode with deterministic backfill and database
  constraints.
- [x] Enforce create/grant/revoke invariants and built-in audience projection.
- [x] Enforce mode validity in visible Space discovery and canonical evidence
  retrieval.
- [x] Separate OpenFGA operational permissions from `can_view`, validate and
  test the versioned model.
- [ ] Expose the contract and implement the enterprise admin UX without raw
  organization or department ids.
- [ ] Reconcile domain specs/test matrices, generate contracts, and complete
  backend, frontend, browser, and migration gates.

## Exit gates

- PostgreSQL migration from the current baseline validates with existing rows.
- Focused core/API tests and OpenFGA model tests pass.
- Backend compile, core tests, and terminating clean test pass.
- Web lint, typecheck, unit tests, production build, and the changed browser flow
  pass.
- API and public OpenAPI artifacts are regenerated and clean.
- The active increment is consolidated and moved to completed only after all
  verification evidence is recorded.
