# Public docs hydration repair plan

- [x] Reproduce React error `#418` on two live documentation routes.
- [x] Trace the regression to route-dependent `<body>` markup introduced in
  `816a3438` and verify the current Next.js hydration guidance.
- [ ] Add a failing production-browser regression test.
- [ ] Make the initial server/client body class deterministic while preserving
  post-mount category identity.
- [ ] Run focused and full Node 24 docs gates plus release policy checks.
- [ ] Reconcile the docs application architecture and test evidence.
- [ ] Open a conventional PR, address review, pass CI, and merge without
  squashing commits.
- [ ] Verify automatic docs image/deployment and zero hydration errors live.
- [ ] Archive this increment, update the roadmap, and checkpoint Northstar.
