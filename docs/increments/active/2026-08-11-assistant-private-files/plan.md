# Assistant Private Files Plan

## Status

Active design. Implementation is blocked on the independent architecture
challenge.

## 1. Challenge the boundary

- [x] Inspect current Assistant governed evidence, Source upload, parser/worker,
  storage, retrieval, UI, specs, safety rules, and the accepted ADR.
- [x] Re-verify pinned Onyx and Northstar attachment implementations from source.
- [x] Write the proposal, strongest counterargument, reference study, and
  challenge brief.
- [ ] Obtain an independent cross-model verdict and incorporate every must-fix
  item into this design or explicitly escalate it to the project owner.

## 2. Private file foundation

- [ ] Add forward-only persistence for immutable actor-private files, pinned
  processing profiles, expiry/deletion state, processing jobs, private chunks,
  and ordered message bindings.
- [ ] Add Assistant-owned admission, registration, recent/status/download/delete,
  turn-claim, and retention-cleanup use cases with opaque cross-owner outcomes.
- [ ] Reuse the worker-only parser/chunker engine through a separate private-file
  processor; keep Source lifecycle and Knowledge publication out of the lane.
- [ ] Add exact private-file retrieval and require usable evidence from every
  selected live file before model generation.

## 3. Composer product flow

- [ ] Replace the paperclip's Source dialog with an Assistant file picker that
  offers `Upload files` and `Recent files`, exposes preparation state, and keeps
  at most three ordered selections across retry.
- [ ] Keep the current governed Source upload under an explicit `Publish to
  Knowledge` action with unchanged Space/classification disclosure.
- [ ] Keep existing governed bindings replayable and do not migrate them.

## 4. Verification and consolidation

- [ ] Cover organization/owner probing, another conversation, status and
  download authorization, expiry/delete races, idempotent cleanup, retry,
  immutable profile replay, exact selection, empty/failed evidence, and prompt
  injection.
- [ ] Refresh OpenAPI and the generated web client, then cover upload/recent,
  polling, chip removal, retry, delete/expiry, and explicit publication UX.
- [ ] Reconcile the Assistant spec/test matrix, Knowledge ingestion test matrix,
  architecture, roadmap, and a superseding decision; add a Tegami entry.
- [ ] Run diff-derived backend, migration, API, frontend, browser, release, and
  full-context gates; capture an immutable UI candidate for owner approval.
- [ ] Complete the PR/CI/review/merge/deploy loop and remove this worktree only
  after exact-SHA production proof.

## Scope limits

No `Add from Knowledge`, promotion API, sharing/project association, legal hold,
images/OCR, archives, email recursion, provider-native files, or migration of
existing governed bindings belongs to this increment.

