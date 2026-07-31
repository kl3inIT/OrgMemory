# Asset Catalog Layout Balance — Design QA

- Source visual truth:
  `docs/increments/completed/2026-07-31-asset-catalog-layout-balance/asset-layout-balance-target.png`
- Browser implementation:
  `docs/increments/completed/2026-07-31-asset-catalog-layout-balance/asset-layout-balance-implementation.png`
- Combined comparison:
  `docs/increments/completed/2026-07-31-asset-catalog-layout-balance/asset-layout-balance-comparison.png`
- State: dark theme, `My Assets`, grid layout, no open menus or visible
  focus ring.
- Browser viewport: `1257 x 723` CSS px with `deviceScaleFactor: 1.5`.
- Compared content: the `PageLayout` surface at approximately `991 x 705` CSS
  px. Both source and implementation evidence are `1487 x 1058` PNGs, so no
  post-capture rescaling was used.

## Full-view comparison

The final comparison places the selected prototype on the left and the browser
implementation on the right at identical pixel dimensions. Page identity,
primary action, search/scope relationship, filter grouping, trailing result and
layout controls, dark hierarchy, and selected states are visibly aligned.

The prototype contains six illustrative cards and fictional metadata. The
implementation intentionally retains the governed Asset card, real type labels,
and browser-test fixture returned by the product contract; card content was not
part of this layout change.

The full-view comparison keeps all affected controls readable at original
density, so an additional focused crop was not required.

## Required fidelity surfaces

- Fonts and typography: the implementation retains the self-hosted Hanken
  Grotesk Variable family and existing semantic page, body, label, and metadata
  roles. The hierarchy matches after normalizing the 1.5x reference density.
- Spacing and layout rhythm: the scope track is capped at `20rem`; search uses
  the remaining width; filter controls lead while result context and layout
  actions terminate the row. Narrow-screen stacking remains unchanged.
- Colors and tokens: selected tabs and layout controls use
  `action-secondary-hover`, `border-strong`, and existing content tokens. No
  hard-coded mode color or gradient was added.
- Image and icon fidelity: the screen has no custom raster artwork. Existing
  Lucide product icons remain sharp and consistent with the application.
- Copy and content: page, search, scope, filter, sort, result, and action labels
  retain the production wording. Result count and card population differ only
  because the prototype uses illustrative data.

## Comparison history

1. The first capture used a 1x browser screenshot against a reference produced
   at the same effective density as the user's 1.5x desktop capture. This made
   the implementation appear incorrectly small. The capture was repeated at
   `deviceScaleFactor: 1.5`; no product typography was changed for a density-only
   mismatch.
2. The normalized comparison found the scope selector wider and its active fill
   brighter than the selected target. The desktop track changed from `26rem` to
   `20rem`, and selected controls changed from `action-secondary-active` to the
   quieter `action-secondary-hover` token.
3. The final comparison has no actionable P0, P1, or P2 mismatch in the scoped
   header and collection controls.

## Browser verification

- Scope switching, URL pagination reset, search, type filtering, list/grid
  switching, Add Asset navigation, and mobile reflow were exercised by the
  Assets Playwright flow.
- The desktop test asserts the `20rem` scope bound, trailing-edge alignment,
  and pressed grid state.
- The harness reported no unexpected API requests or browser console errors.

## Follow-up polish

- P3: the production filter controls are slightly denser vertically than the
  generated image. They intentionally retain the shared shadcn control height
  rather than introducing an Assets-only size.

final result: passed
