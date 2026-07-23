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

## Decision

This increment established the shared product shell, self-hosted interface
typography, responsive Documents presentation, and citation and upload
interactions. The durable contracts now live only in the
[frontend design system](../../../guidelines/frontend-design-system.md).

## Verification

Run Oxlint, TypeScript typecheck, the production build, and real-browser checks
for Assistant and Documents in light/dark themes and a narrow viewport. Preserve
the user's untracked POC Word document.
