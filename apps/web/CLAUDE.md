# OrgMemory Web Guidance

Read the repository root `CLAUDE.md`, then this file,
`apps/web/ARCHITECTURE.md`, and the relevant domain spec/test pair.

- Extend the established product shell, tokens, shadcn/Radix primitives, and
  layout patterns; do not introduce a separate visual system.
- Generate ordinary REST clients from `contracts/openapi.json` with Hey API.
- TanStack Query owns server state, TanStack Router owns navigation, and Zustand
  is limited to durable or high-frequency UI state.
- Run package commands from the root pnpm workspace with
  `pnpm --filter @orgmemory/web ...`.
- Preserve keyboard access, light/dark themes, loading/error states, responsive
  behavior, and the existing browser suite.
