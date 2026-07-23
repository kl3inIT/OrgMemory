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
- The authenticated shell owns the viewport and must not allow the document
  body to become a second vertical scroller. Each page declares exactly one
  content scroll owner.

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

## Typography And Dense Data

- Hanken Grotesk Variable is self-hosted through Fontsource and is the default
  interface face. The bundled Vietnamese subset is required; do not replace it
  with a CDN import or a Latin-only asset.
- Technical identifiers and immutable metadata use the platform monospace
  stack. Do not add another product font without a demonstrated readability
  need.
- Use the semantic typography roles exposed by `theme.css` for page titles,
  section titles, body, labels, supporting text, and metadata. Avoid inventing
  one-off sizes in feature screens.
- Status tabs omit zero-value badges while preserving the full accessible
  count. On narrow screens, use concise visible labels without changing their
  accessible names.
- Dense tables retain the decision-critical columns at narrow widths and reveal
  operational detail progressively at `md`, `lg`, and `xl`. Do not solve table
  density by shrinking all text or allowing the whole page to overflow.
- Assistant citations deep-link into a validated Documents query so a user can
  inspect the referenced source immediately. Shareable filters belong to
  TanStack Router search state; Zustand remains for local UI preferences.

## Retained Building Blocks

Keep shadcn/ui registry primitives and the AI Elements foundation even while a
screen is not implemented. They are local product building blocks, not evidence
that the corresponding product feature already exists. Add product routes one
vertical slice at a time; do not restore the deleted prototype pages.
