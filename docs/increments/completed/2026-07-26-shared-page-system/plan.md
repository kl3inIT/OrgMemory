# Shared Page System Plan

- [x] Add centralized page width, gutter, panel, and stacking tokens.
- [x] Add `PageLayout`, `SplitLayout`, `Content`, `ContentAction`,
  `FilterBar`, and shared `EmptyState`.
- [x] Add the shadcn-compatible breadcrumb primitive.
- [x] Add TanStack Table and a headless `DataTable` pattern rendered with the
  existing semantic table primitives.
- [x] Migrate every feature data grid to the shared `DataTable`; replace the
  Connection detail key/value table with a semantic description list.
- [x] Add focused Vitest and Testing Library contracts for `PageLayout` and
  `DataTable` without duplicating page-level Playwright coverage.
- [x] Migrate Sources/Documents to the wide page contract and Knowledge graph
  to the canvas contract.
- [x] Migrate Asset catalog, detail, governance, and pack journey pages.
- [x] Remove the decorative Asset-result count from the catalog header.
- [x] Migrate MCP connection and the shared Administration page.
- [x] Run lint, typecheck, production build, focused Playwright/browser checks,
  and inspect the final diff.
- [x] Record deferred frontend-stack findings in the engineering backlog rather
  than expanding this PR with speculative dependencies.
- [x] Consolidate current facts and tests, then move this increment to
  `completed`.
