# Asset ownership navigation design QA

## Comparison target

- Source visual truth:
  `docs/increments/completed/2026-07-31-asset-ownership-navigation/onyx-agents-layout-reference.png`
- Browser-rendered implementation is produced by Playwright under
  `apps/output/design-qa/asset-catalog-grid.png`,
  `apps/output/design-qa/asset-catalog-mine.png`, and
  `apps/output/design-qa/asset-catalog-mobile.png`.
- Durable copies reviewed for this increment are archived under
  `docs/increments/completed/2026-07-31-asset-ownership-navigation/` with those
  same three filenames.
- Side-by-side comparison:
  `docs/increments/completed/2026-07-31-asset-ownership-navigation/asset-layout-comparison.png`
- Browser: Microsoft Edge through Playwright channel `msedge`
- Desktop viewport and CSS size: `1536 x 1024`
- Mobile viewport and CSS size: `390 x 844`
- Source pixels: `1638 x 646`
- Desktop implementation pixels: `1536 x 1024`
- Device scale factor: `1`
- Density normalization: none required
- States: authenticated employee, light theme, All Assets grid; My Assets with
  an owned Draft; responsive mobile All Assets grid

## Full-view comparison evidence

The source and implementation were compared at full-view and header-region
scale. Both use the selected hierarchy: page identity and primary creation CTA;
search beside a two-scope switch; secondary filters below; content after the
navigation controls. OrgMemory adds the compact Asset-type selector and layout
switch on the secondary row because the product supports multiple governed
profiles.

The implementation intentionally retains the shipped OrgMemory shell, light
theme tokens, page widths, Hanken Grotesk typography, cards, and navigation.
Onyx's dark shell and Agents-specific filters are reference content rather than
OrgMemory requirements.

## Focused-region evidence

The comparison composite focuses on the header and control hierarchy where the
reference applies. Full desktop captures additionally verify the All and My
content states. The mobile capture verifies that search and scope stack before
the secondary filters and that the page has no horizontal overflow.

## Findings

- No actionable P0, P1, or P2 differences.
- [P3] OrgMemory's real catalog is denser than the empty Onyx state. The page
  still preserves the reference hierarchy before its real Asset cards.
- [P3] Scope labels use `All Assets | My Assets`, matching the Asset domain and
  the user's ownership question instead of copying `All Agents | Your Agents`.

## Required fidelity surfaces

- Fonts and typography: shipped Hanken Grotesk, weights, hierarchy, monospaced
  coordinates/versions, line clamps, and truncation are consistent and readable.
- Spacing and layout rhythm: header/CTA alignment, primary search/scope row,
  secondary filter row, page gutters, and responsive stacking follow existing
  design-system tokens.
- Colors and visual tokens: semantic success/warning/info/muted tokens replace
  ad-hoc color values and retain contrast in the verified light-theme state.
- Image and asset fidelity: all visible type icons are vector glyphs from the
  existing Lucide dependency; there are no generated, inline handcrafted, CSS,
  emoji, or placeholder icons.
- Copy and content: `All Assets` communicates reusable released content;
  `My Assets` communicates accountable ownership and routes to Governance.

## Interactions and runtime checks

- All Assets is the clean-URL default; My Assets writes `scope=MINE`, and
  returning to All removes it.
- Search, type, sort, and pagination remain server-controlled in both scopes.
- All links remain exact-release consumption links; My links open Governance.
- Grid/list state continues to be URL-backed.
- Microsoft Edge desktop and mobile browser tests passed with no unexpected API
  requests, page errors, console errors, or page-level horizontal overflow.

## Comparison history

Pass 1 found no actionable P0/P1/P2 issues, so no visual-fix iteration was
required.

final result: passed
