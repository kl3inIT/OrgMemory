# Frontend Design System Foundation Plan

## 1 — Token Foundation

- [x] Split tokens, Tailwind theme mappings, and base rules out of `index.css`.
- [x] Define semantic values for light and dark themes.
- [x] Preserve shadcn compatibility aliases and add product-domain tokens.

## 2 — Component And Shell Adoption

- [x] Replace manual component dark-mode colors with semantic interaction and
  control tokens.
- [x] Add one `AppToaster` composition around Sonner.
- [x] Add typed route metadata and derive the shell title from active matches.

## 3 — Verification And Delivery

- [x] Run web lint, typecheck, and production build.
- [x] Verify login, Assistant, Documents, the notification host, narrow layout, light, and dark
  modes in a real browser.
- [ ] Consolidate docs, move the increment to completed, commit, push, open a PR,
  and resolve actionable CI or CodeRabbit findings.
