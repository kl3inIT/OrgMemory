# Asset Catalog Layout Balance

## Problem

The ownership-navigation increment established the correct `/assets`
information hierarchy, but its desktop tracks scale proportionally without a
useful upper bound. On a wide viewport the two-item `All Assets | My Assets`
control grows past 600 px, while the result count and layout toggle remain
attached to the filter cluster and leave the right side of the toolbar empty.

## Visual Target

[`asset-layout-balance-target.png`](asset-layout-balance-target.png) is the
selected product-design prototype. It keeps the existing OrgMemory visual
language while tightening the two desktop relationships that need correction:

- search consumes the remaining width while the scope selector is capped;
- type and sort remain left-aligned while result context and layout actions
  align to the trailing edge.

## Outcome

- Keep the current page identity, copy, route state, scope semantics, cards,
  table, and responsive stacking behavior.
- Cap the desktop scope selector at 20 rem rather than one third of every wide
  viewport.
- Make the shared `FilterBar` treat result context and actions as one trailing
  group on wider screens.
- Strengthen the selected list/grid state using existing semantic action
  tokens, without adding mode-specific colors.

## Constraints

- Continue using the existing shadcn/Radix primitives, Lucide icon set already
  selected by the product, Hanken Grotesk, and semantic theme tokens.
- Do not change Asset APIs, authorization, ownership rules, URL search state,
  or catalog data.
- Preserve narrow-screen reflow and accessible names, pressed states, focus
  rings, and the result live region.
