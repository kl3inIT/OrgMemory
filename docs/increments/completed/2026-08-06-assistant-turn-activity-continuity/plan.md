# Assistant Turn Activity Continuity Plan

- [x] Reproduce the raw-Markdown handoff and split-render-site causes from the
  current browser implementation.
- [x] Re-verify the pinned Onyx Skill card, grouped work, and timeline expansion
  lifecycle at commit `618b5031bf21463f44e3bed9eb9d5073b806fec0`.
- [x] Add a renderable-output predicate that does not treat Markdown framing or
  invisible characters as painted answer content.
- [x] Compose thinking and Skill receipts in one stable current-turn activity
  surface anchored after the initiating user message.
- [x] Port Onyx's useful disclosure rule: no chevron for a receipt without
  useful detail; keep resource progress and failure details expandable.
- [x] Add focused component/state tests and a browser regression for ordering,
  continuity, and auto-collapse.
- [x] Run web lint, typecheck, unit tests, focused Playwright, and production
  build with Node 24.
- [x] Reconcile the Assistant spec/test matrix, record verification, move the
  increment to completed, and update the roadmap.
