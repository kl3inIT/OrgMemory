# Assistant Private Files Plan

## Status

Shipped in OrgMemory v0.4.0. The independent one-round Fable 5 challenge
returned `REVISE` and selected reusable retention-bounded private files. The
project owner waived malware/DLP for this increment after direct verification
that pinned Onyx does not put either gate in its user-file upload path.

## 1. Challenge the boundary

- [x] Inspect current Assistant governed evidence, Source upload, parser/worker,
  storage, retrieval, UI, specs, safety rules, and the accepted ADR.
- [x] Re-verify pinned Onyx and Northstar attachment implementations from source.
- [x] Write the proposal, strongest counterargument, reference study, and
  challenge brief.
- [x] Obtain one independent Fable 5 response and incorporate its committed
  `REVISE` verdict and must-fix conditions into the design.
- [x] Record the project-owner malware/DLP waiver after verifying the pinned
  Onyx upload path; keep size/resource admission and parser isolation in scope.

## 2. Private file foundation

- [x] Add forward-only persistence for immutable actor-private files, pinned
  processing profiles, expiry/deletion state, processing jobs, private chunks,
  and ordered message bindings.
- [x] Add Assistant-owned admission, registration, recent/status/download/delete,
  turn-claim, and retention-cleanup use cases with opaque cross-owner outcomes.
- [x] Reuse the worker-only parser/chunker engine through a separate private-file
  processor; keep Source lifecycle and Knowledge publication out of the lane.
- [x] Add exact private-file retrieval and require usable evidence from every
  selected live file before model generation.
- [x] Add a distinct private citation/hydration/download path, prohibit mixed
  private/governed turns, and pin fixed non-renewing expiry semantics.

## 3. Composer product flow

- [x] Replace the paperclip's Source dialog with an Assistant file picker that
  offers `Upload files` and `Recent files`, exposes preparation state, and keeps
  at most three ordered selections across retry.
- [x] Keep the current governed Source upload under an explicit `Publish to
  Knowledge` action with unchanged Space/classification disclosure.
- [x] Keep existing governed bindings replayable and do not migrate them.

## 4. Verification and consolidation

- [x] Cover organization/owner probing, another conversation, status and
  download authorization, expiry/delete races, idempotent cleanup, retry,
  immutable profile replay, exact selection, empty/failed evidence, and prompt
  injection.
- [x] Refresh OpenAPI and the generated web client, then cover upload/recent,
  polling, chip removal, retry, delete/expiry, and explicit publication UX.
- [x] Reconcile the Assistant spec/test matrix, Knowledge ingestion test matrix,
  architecture, roadmap, and a superseding decision; add a Tegami entry.
- [x] Run diff-derived backend, migration, API, frontend, browser, release, and
  full-context gates; capture an immutable UI candidate for owner approval.
  Automated gates are green. The project owner approved the production-build
  Assistant attachment candidate on 2026-08-12; authentication UI was unchanged.
- [x] Complete the PR/CI/review/merge/deploy loop and remove this worktree only
  after exact-SHA production proof.

## Scope limits

No `Add from Knowledge`, promotion API, sharing/project association, legal hold,
images/OCR, archives, email recursion, provider-native files, or migration of
existing governed bindings belongs to this increment.
