# Public docs hydration repair plan

- [x] Reproduce React error `#418` on two live documentation routes.
- [x] Compare static and hydrated Linux DOM, disprove the body-class hypothesis,
  and trace the first structural difference to Fumadocs' category trigger.
- [x] Add a production-browser regression test for React hydration diagnostics.
- [x] Upgrade Fumadocs Core and Base UI together to 16.14.0 and MDX to 15.2.1.
- [x] Verify the production Docker image across English, Vietnamese, Product
  Guides, and Architecture routes with correct category identity and zero
  browser console errors.
- [x] Run focused and full Node 24 docs gates plus release policy checks.
- [x] Reconcile the docs application architecture and test evidence.
- [x] Open a conventional PR, address review, pass CI, and merge without
  squashing commits.
- [x] Verify automatic docs image/deployment and zero hydration errors live.
- [x] Archive this increment, update the roadmap, and checkpoint Northstar.
