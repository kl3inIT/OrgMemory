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

## Retained Building Blocks

Keep shadcn/ui registry primitives and the AI Elements foundation even while a
screen is not implemented. They are local product building blocks, not evidence
that the corresponding product feature already exists. Add product routes one
vertical slice at a time; do not restore the deleted prototype pages.
