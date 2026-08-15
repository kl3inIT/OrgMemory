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
- [ ] Run the independent Fable 5 architecture challenge and record the verdict.
- [ ] Apply every binding correction before implementation.

## Implementation

- [ ] Add `maxBatchBytes` to `GoogleDriveCrawlSettings` with a bounded default.
- [ ] Stop retaining content when the aggregate UTF-8 budget would be exceeded;
  mark content and whole-crawl completeness false without discarding established
  permission evidence.
- [ ] Expose the advanced setting in the existing descriptor-driven connector
  form without adding a Drive-specific component.
- [ ] Keep source replay, checkpoint, and retirement semantics unchanged.

## Proof

- [ ] Add exact-boundary and over-boundary adapter tests.
- [ ] Add a recorded-response Google Drive vertical integration test using a
  generated service-account key, real connector reconciliation/PostgreSQL, and
  permission-aware retrieval.
- [ ] Prove a direct-user grant allows only the mapped user.
- [ ] Prove a permission-only recrawl revokes that user without changing the
  source revision or rematerializing content.
- [ ] Prove the fixture uses no real credential and performs no network calls.

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
