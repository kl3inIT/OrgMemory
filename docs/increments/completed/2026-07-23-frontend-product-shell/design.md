# Frontend Product Shell Design

## Outcome

OrgMemory presents Assistant and Documents as one calm, enterprise AI workspace
instead of a collection of default component-library screens. The shell gains a
clear information hierarchy, consistent typography, and compact status counts
without changing authentication, retrieval, ingestion, or AI delivery behavior.

## Boundaries

- Keep Vite, TanStack Router/Query, shadcn/Radix, AI Elements, Sonner, and the
  existing semantic light/dark token contract.
- Learn the product-shell and interaction-system lessons from Onyx, but do not
  port Opal, Next.js, SWR, its packet protocol, or its global stores.
- Keep server state in TanStack Query and route identity in TanStack Router.
- Do not change backend contracts, OpenFGA policy, source ingestion, or the
  Assistant streaming protocol in this increment.

## Typography

Use self-hosted Hanken Grotesk Variable as the primary interface typeface. Load
it from the application bundle through Fontsource so enterprise deployments do
not depend on a third-party font CDN. Retain the platform monospace stack for
technical identifiers; a second product font is unnecessary.

Expose typography through semantic roles for page titles, section titles, body,
supporting text, and metadata. Feature components consume those roles rather
than inventing per-screen sizes and tracking.

## Product Chrome

- The authenticated shell owns the sidebar, route title, compact global
  actions, responsive collapse behavior, and main-content boundary.
- The sidebar distinguishes product identity from workspace navigation and
  gives selected, hover, focus, and collapsed states equal attention.
- Assistant remains the primary workspace. Documents retains its nested
  Documents/Knowledge graph navigation and permission-aware ingestion states.
- Counts are compact badges when non-zero. Zero counts are omitted from status
  tabs; result summaries use human language rather than bare fractions.
- The shell has one content scroll owner; inset margins must not make the body
  taller than the viewport.
- Assistant source chips deep-link to a validated Documents query, while the
  upload flow asks for classification before the dependent Knowledge Space.

## Verification

Run Oxlint, TypeScript typecheck, the production build, and real-browser checks
for Assistant and Documents in light/dark themes and a narrow viewport. Preserve
the user's untracked POC Word document.
