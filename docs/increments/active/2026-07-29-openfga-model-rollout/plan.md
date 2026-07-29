# OpenFGA Model Rollout Repair Plan

- [x] Trace the UI denial through the API guard and production deployment model
  pin.
- [x] Verify OpenFGA model-write and immutable-version behavior against current
  official documentation.
- [x] Add a model-write operation to production Compose.
- [x] Persist the model digest during first-store bootstrap.
- [x] Write and atomically pin a changed model before API/worker recreation.
- [x] Preserve the previous model ID and digest across failed deployment
  rollback.
- [x] Add deterministic upgrade, no-op, and rollback tests to deployment CI.
- [x] Reconcile architecture, deployment runbook, authorization spec/coverage,
  and roadmap.
- [ ] Run OpenFGA, deployment, documentation, and repository hygiene gates.
- [ ] Open the PR, resolve actionable review/CI findings, merge, deploy, and
  verify the two administrator screens.
