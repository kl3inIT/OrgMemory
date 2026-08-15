# Google Drive Ingestion Hardening Plan

## Status

Implementation active on `feat/google-drive-ingestion-hardening`.

## Architecture

- [x] Inspect the existing Drive adapter, connector reconciliation, authorization,
  polling, and vertical retrieval contracts.
- [x] Compare the bounded pilot proposal with pinned Onyx ingestion and Google
  Drive permission-sync mechanics.
- [x] Define the service-account, direct-user-grant, aggregate-budget boundary and
  explicit Directory/OAuth/change-token exclusions.
- [x] Run the independent Fable 5 architecture challenge and record the
  `ACCEPT WITH BINDING CORRECTIONS` verdict.
- [x] Apply every binding correction to the design and this plan before
  implementation.

## Implementation

- [ ] Add `maxBatchBytes` to `GoogleDriveCrawlSettings`: 64 MiB default,
  non-positive values use the default, and smaller positive values clamp to the
  25 MiB response cap.
- [ ] Read one body under the existing response cap; stop retaining bodies when
  aggregate UTF-8 bytes would be exceeded, but continue every remaining file
  through permission observation.
- [ ] Emit CONTENT incomplete with
  `GOOGLE_DRIVE_CONTENT_BUDGET_EXHAUSTED`, whole-crawl incomplete, and
  PERMISSION complete when all sharing was established; do not count skipped
  bodies as provider failures.
- [ ] Make permission-only reconciliation of an object without a materialized
  source head return benign `UNCHANGED`.
- [ ] Expose the advanced setting in the existing descriptor-driven connector
  form without adding a Drive-specific component.
- [ ] Preserve existing source replay, retirement, golden cursor, and
  mostly-failed semantics; deliberately change CONTENT checkpoint completeness
  on an aggregate-budget hit.

## Proof

- [ ] Add exact-boundary, native-export overshoot, permissions-only continuation,
  admission isolation, cadence, incomplete-reason, and golden-cursor adapter
  tests.
- [ ] Add a real PostgreSQL budget-hit test proving no false retirement and
  benign permission reconciliation for unmaterialized tail content.
- [ ] Add a recorded-response Google Drive vertical integration test using a
  generated service-account key, real connector reconciliation/PostgreSQL, and
  permission-aware retrieval.
- [ ] Prove a direct-user grant allows only the mapped user.
- [ ] Prove a permission-only recrawl revokes that user without changing the
  source revision or rematerializing content.
- [ ] Prove the fixture uses no real credential and performs no network calls.
- [ ] Prove the existing admin activity/checkpoint surface exposes the stable
  aggregate-budget incomplete reason.

## Verification and consolidation

- [ ] Run Jmix/IDE static inspection for every edited backend Java file.
- [ ] Run the narrow connector and worker integration tests.
- [ ] Run the terminating backend `clean test` context gate.
- [ ] Run frontend typecheck/unit/build and browser verification if the
  descriptor UI changes.
- [ ] Reconcile `ARCHITECTURE.md`, the knowledge-ingestion spec/test pair, and
  roadmap status with the exact verified behavior.
- [ ] Record exact commands, results, known gaps, branch, and commit in
  `verification.md`; move the increment to completed only after all gates pass.

## Stop conditions

Stop and obtain a new decision if implementation requires a Google-specific core
contract, a new persistence owner, Admin SDK/Directory authorization expansion,
OAuth refresh-token storage, or a source-side change-token lifecycle.
