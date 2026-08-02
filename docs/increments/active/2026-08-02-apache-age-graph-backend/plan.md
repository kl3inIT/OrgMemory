# Explicit Apache AGE graph backend plan

## 0. Architecture gate

- [x] Inspect current GraphRAG publication, traversal, PostgreSQL, AGE, runtime,
  test-image, and configuration evidence.
- [x] Inspect pinned LightRAG v1.5.4 backend selection and failure semantics.
- [x] Attempt an independent Codex `gpt-5.6-sol ultra` challenge; record both
  unavailable review turns, the owner's explicit direction, and the binding
  conditions inherited from decisions 0027 and 0028 before implementation.

## 1. Characterization before production code

- [x] Characterize that `REQUIRED` and `DISABLED` currently construct the same
  relational `GraphStore`, and that the AGE projection has no runtime bean.
- [x] Run `:integrations:graph-rag-postgres:test` and commit the tests separately
  (`86ca18cd`).

## 2. Explicit backend and fail-closed startup

- [ ] Replace the three-state AGE mode with exact `APACHE_AGE|RELATIONAL`
  topology selection; retain no optional fallback.
- [ ] Make `APACHE_AGE` the production default and fail context creation when
  extension/catalog/privileges are unavailable.
- [ ] Migrate intentional test configuration to `RELATIONAL`.
- [ ] Run the focused PostgreSQL gate and commit the selection change.

## 3. Snapshot-safe AGE runtime

- [ ] Stage fixed-size AGE topology under the exact publication batch and create
  its ready marker only after a complete transactional rebuild.
- [ ] Require the existing discard permit for exact AGE cleanup.
- [ ] Serve authorized, cursor-bounded incident-relation pages from AGE while
  leaving all evidence and final traversal authority relational/core-owned.
- [ ] Prove idempotent replay, unpublished invisibility, historical snapshots,
  authorization-negative behavior, missing/corrupt marker failure, and parity
  with the relational reference.
- [ ] Run the focused PostgreSQL and affected app gates and commit runtime work.

## 4. Verification and consolidation

- [ ] Run backend static analysis or record the documented fallback if the IDE
  transport is unavailable.
- [ ] Run a terminating `gradlew clean test`, docs check, `git diff --check`, and
  confirm the worktree is free of unrelated changes and secrets.
- [ ] Record the decision and rejected alternative; reconcile `ARCHITECTURE.md`,
  Secure GraphRAG spec/test matrix, and their `Source:`/`Reconciled:` lines.
- [ ] Move the increment to `completed/` and mark the roadmap entry shipped.

## 5. Delivery

- [ ] Sync current `origin/main`, preserve logical commits, push the branch, and
  open a conventional PR.
- [ ] Resolve all actionable review findings, pass required CI, merge, and verify
  post-merge delivery impact.
- [ ] Record the verified decision, evidence, risks, and next step in Northstar.
