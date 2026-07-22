# Frontend Design System Guideline

The replacement UI is an agent-first enterprise workspace, not a dashboard
template. Use shadcn/ui local components and maintained libraries for generic
primitives; custom code composes OrgMemory-specific evidence, permission, source,
candidate, and approval workflows.

Required qualities:

- light and dark themes using semantic tokens;
- visible pending, streaming, indexing, denied, stale, failed, and retry states;
- keyboard navigation and accessible names/focus;
- responsive behavior from narrow laptop windows through large admin screens;
- citations and evidence provenance adjacent to generated claims;
- privacy/source status understandable without exposing restricted metadata.

Do not copy old page layouts merely to preserve route parity. Reuse old code only
when it is generic, tested, and compatible with the new information architecture.

## Foundation Boundary

- `main.tsx` owns process-level providers and the final React crash boundary.
- TanStack Router owns route pending, route error, not-found, typed search, and
  route-level code splitting.
- TanStack Query owns server state. Initial failures render in context; only
  failed background refreshes produce a global toast.
- The browser session is the authenticated-shell gate. Product routes must not
  render before the session is verified.
- Ordinary REST clients are generated from root `contracts/openapi.json` with
  Hey API. AI streaming remains a separate transport boundary.
- Raw exception text is development-only. Production states use safe messages
  and an explicit retry path.

## Semantic Theme Contract

- `web/src/styles/tokens.css` owns primitive values and light/dark semantic
  roles. Product components consume semantic roles, never primitive palette
  values or per-component mode colors.
- `web/src/styles/theme.css` only exposes those roles to Tailwind. Existing
  shadcn names remain compatibility aliases while product-specific surfaces use
  explicit names such as `assistant-composer`, `citation`, and `permission`.
- `AppToaster` is the only Sonner host. Features may call Sonner's public
  `toast` API, but must not mount another toaster or create a second global
  notification store.
- Authenticated routes declare their shell title through TanStack Router
  `staticData`; the shell must not infer product identity from pathname string
  comparisons.
- Chart theme selectors may remain mode-specific because they map chart data,
  not component presentation. Other manual color `dark:` variants require an
  explicit design-system exception.

## Retained Building Blocks

Keep shadcn/ui registry primitives and the AI Elements foundation even while a
screen is not implemented. They are local product building blocks, not evidence
that the corresponding product feature already exists. Add product routes one
vertical slice at a time; do not restore the deleted prototype pages.
