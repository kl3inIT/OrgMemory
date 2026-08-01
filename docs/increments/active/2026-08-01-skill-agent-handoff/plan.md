# Skill agent handoff plan

## 0. Decision gate

- [x] Audit the shipped browser authoring and exact-install flows.
- [x] Run a two-round Codex/Fable 5 debate through Orca.
- [x] Obtain an independent record-only Fable 5 verdict.
- [x] Record the selected design, rejected alternative, and non-goals.

## 1. Product implementation

- [x] Add the feature-scoped `AgentHandoff` descriptor and Skill builders.
- [x] Add the shared copy-only agent/CLI panel with a required boundary.
- [x] Compact the three browser-authoring cards.
- [x] Add Draft upload handoff to `/assets/new/skill`.
- [x] Replace duplicated exact-install command UI on released Skill detail.

## 2. Verification and reconciliation

- [x] Add focused builder and component tests.
- [x] Update the golden browser path for creation and exact installation.
- [x] Reconcile the Asset Registry spec and mirrored test matrix.
- [x] Pass web lint, typecheck, unit tests, and production build.
- [x] Verify light, dark, desktop, and narrow layouts in a real browser.

## 3. Delivery

- [x] Merge current `origin/main` into the branch before PR creation.
- [ ] Open one conventional PR below 100 changed files.
- [ ] Resolve CI and CodeRabbit findings with reply-linked fixes.
- [ ] Merge with a merge commit; do not squash commits.
- [ ] Verify main CI, deployment, and live behavior; then archive the increment.
