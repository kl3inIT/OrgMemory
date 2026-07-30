# Automatic Public Docs Delivery Plan

## Status

- [x] Reconcile the existing docs and product delivery boundaries.
- [x] Verify current GitHub Actions `workflow_run`, permissions, concurrency,
      and exact-SHA guidance.
- [x] Record the independent architecture challenge verdict.
- [x] Trigger docs image publication from successful trusted `main` CI.
- [x] Make unaffected CI runs explicit docs release no-ops.
- [x] Trigger docs deployment only from an automatic successful image
      publication.
- [x] Reject superseded automatic releases while preserving non-docs ancestry
      and manual rollback.
- [x] Update current architecture, delivery guidance, and operator runbook.
- [x] Pass local workflow, Node 24 public-docs, production build, and repository
      documentation checks.
- [x] Open the PR, address review, and pass all selected CI jobs.
- [x] Merge and observe automatic image publication and the docs-only
      failed-canary rollback.
- [x] Reconcile the first automatic rollback: the candidate was healthy, but
      the publication verifier ignored localized sitemap routes.
- [x] Merge the verifier repair and observe automatic image publication and
      successful docs-only deployment.
- [x] Verify live health, English, Vietnamese, and Product Guides routes.
- [x] Record verification, archive this increment, and update the roadmap.

## Exit Gate

The increment is complete only when the merge commit reaches live
`docs.kl3in.tech` through the automatic path and the live bilingual routes are
verified. A manually dispatched release does not satisfy this gate.
