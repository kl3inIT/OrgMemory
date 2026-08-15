# Business Access Explanation Plan

- [x] Pull fresh `origin/main`, create a feature branch, and verify a clean worktree.
- [x] Reproduce the DOC037 Allowed derivation in production and resolve its
  department and Knowledge Space names from tenant-owned records.
- [x] Compare OpenFGA/SpiceDB developer visualizers with Azure IAM Check access
  and Google Cloud Policy Troubleshooter administrator patterns.
- [x] Produce and review business-first prototypes without changing production UI.
- [x] Add a failing backend integration test for tenant-checked resolved path labels.
- [x] Implement the smallest protected API change that adds resolved business labels
  without changing the existing authorization result or raw path contract.
- [x] Regenerate OpenAPI and the web client.
- [x] Add failing frontend tests for the approved current-access and assignment presentation.
- [x] Replace the split technical inspector with the polished business-first surface;
  remove Technical details completely.
- [x] Reconcile permission-evidence and identity spec/test pairs plus architecture facts.
- [x] Run backend integration, web lint, typecheck, unit, generated-contract, and
  production-build gates.
- [ ] Deploy through the repository loop and verify U007 Allowed and U003 Denied
  against DOC037 in a real browser.
- [ ] Capture the approved production surface for Slide 8 and close this increment
  with immutable verification evidence.
