# Asset Ownership Navigation

## Problem

The current `/assets` page is only a consumption catalog. It lists the latest
non-withdrawn releases the actor may use, but it gives an author no route back
to owned Drafts or governed Assets. Its search, sort, layout, and type controls
also compete in one toolbar, weakening the page hierarchy as more Asset types
are added.

Onyx revision `618b5031bf21463f44e3bed9eb9d5073b806fec0` keeps one Agents
surface and places `All Agents | Your Agents` beside the primary search. The
`Your Agents` tab is backed by server-computed ownership rather than creator
text (`tmp/onyx/web/src/views/AgentsNavigationPage.tsx` and
`tmp/onyx/web/src/lib/agents/utils.ts`).

## Outcome

- Keep one `/assets` surface with `All Assets | My Assets` scope tabs.
- Keep the clean URL as the all-assets default; encode only `scope=MINE`.
- Preserve the existing released, `can_use`-authorized catalog for `All Assets`.
- Add a bounded owner workspace query for `My Assets` by intersecting the
  canonical active `OWNER` role assignment with the actor's live OpenFGA
  `can_view` set. It includes Draft-only and released Assets.
- Route an owned item to its Governance workspace, where authoring and release
  controls already live.
- Adopt the Onyx information hierarchy: page identity and CTA first; primary
  search plus scope second; type/sort/layout filters on a separate row.
- Retain OrgMemory page widths, color tokens, typography, cards, and shadcn/
  Radix primitives rather than copying Onyx styling.

## Constraints

- Ownership is derived from the authenticated actor and the canonical role
  ledger, then capped by live OpenFGA visibility. The browser never sends or
  infers an owner identity.
- `My Assets` means current direct ownership, not `createdBy`, `can_edit`, or
  general visibility. Contributor attribution remains a separate future
  concern.
- Every collection is server-paged and stably sorted before rendering.
- A failed authorization-set resolution fails closed and returns no partial
  owner workspace.
- Existing catalog URLs and default grid behavior remain compatible.

## Rejected Alternative

A separate Skill marketplace or personal-Assets application was rejected. It
would fragment the governed Asset model and repeat filters, navigation, and
authorization behavior that belong to the existing Assets surface.
