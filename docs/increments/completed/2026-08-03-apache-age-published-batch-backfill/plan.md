# Apache AGE Published-Batch Backfill Plan

## 0. Architecture gate

- [x] Run the independent architecture challenge against the written brief.
- [x] Record the binding verdict and amend this design as required.

## 1. Characterization

- [x] Prove an already-published relational graph batch fails AGE reads when
  its organization graph or exact ready marker is absent.
- [x] Prove an exact ready marker is skipped without rebuilding.

## 2. Bounded reconciliation

- [x] Add stable keyset enumeration and preflight batch/entity/relation limits
  for published `GRAPH` snapshots.
- [x] Add exact marker inspection and idempotent batch reconciliation under the
  existing organization lock.
- [x] Add an explicitly invoked API one-shot runner that closes the application
  after success.
- [x] Add the operations-profile Compose service and quiesced deployment order.

## 3. Verification and consolidation

- [x] Run focused unit and PostgreSQL/AGE integration tests.
- [x] Run edited-Java static-analysis fallback and the terminating context gate.
- [x] Reconcile architecture, secure GraphRAG spec/test matrix, deployment
  guidance, and roadmap.

## 4. Delivery

- [x] Commit, publish a PR, and wait for required CI.
- [x] Deploy the green immutable image set.
- [x] Verify AGE markers, Graph explorer, Assistant citation, and production
  telemetry with the authenticated browser flow.
