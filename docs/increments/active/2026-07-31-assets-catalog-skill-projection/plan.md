# Plan

- [ ] Replace the compact type select with a prominent, controlled Asset type
  projection backed by the existing URL state.
- [ ] Keep search, sort, layout, pagination, loading, error, and empty states
  intact.
- [ ] Add focused component coverage for selection, accessible state, and type
  metadata.
- [ ] Run web lint, typecheck, unit tests, production build, and browser
  verification.
- [ ] Reconcile the Asset Registry spec and coverage matrix.
- [ ] Merge the reviewed pull request and move this increment to completed.

## Verification

- `corepack pnpm --filter @orgmemory/web lint`
- `corepack pnpm --filter @orgmemory/web typecheck`
- `corepack pnpm --filter @orgmemory/web test:unit`
- `corepack pnpm --filter @orgmemory/web build`
- real-browser Assets catalog type selection and URL-state verification
- `git diff --check`
