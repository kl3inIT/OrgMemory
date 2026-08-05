# Knowledge Graph Responsive Header

## Problem

At the production desktop viewport captured on 2026-08-05, the Knowledge Graph
workspace gives its five explorer controls non-shrinking priority while the page
title remains breakable. Once the app sidebar reduces the available content
width, flexbox compresses `Knowledge graph` to its minimum content width and
renders one character per line.

This is a layout failure, not a graph query, authorization, or data problem.

## Outcome

- Keep the page title readable as one stable workspace identity.
- Let the action region consume only the remaining width and wrap its controls
  before it can compress the title.
- Preserve the current narrow-screen vertical stack, control order, accessible
  labels, graph query state, and permission-scoped data contract.
- Protect the production-width failure with a focused browser regression and
  the shared page-layout contract with a unit assertion.

## Approach

Fix the shared `PageLayout.Header` flex contract instead of adding graph-only
pixel offsets. The title group is intrinsic and non-shrinking from the first
side-by-side breakpoint; the action group becomes the flexible, minimum-width
region. The existing graph form already permits wrapping, so it can reflow
inside that region without changing interaction behavior.

## Constraints

- Continue using the existing React, Tailwind, shadcn/Radix, and page-layout
  system; add no dependency or parallel visual system.
- Do not change graph APIs, retrieval, curation, authorization, or persistence.
- Do not use fixed viewport-specific widths to hide the failure.
- Preserve keyboard access, light/dark themes, and browser-error-free rendering.

## Verification

- Unit coverage pins the shared title/action shrink contract.
- A Playwright journey mocks the authenticated permission-scoped graph APIs,
  renders `/sources?view=graph` at the reported desktop class of viewport, and
  proves the title remains horizontal without page overflow.
- Web lint, typecheck, unit tests, focused browser test, and production build
  run on Node 24.
