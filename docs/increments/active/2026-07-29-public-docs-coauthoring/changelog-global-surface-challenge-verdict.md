# Architecture challenge verdict: Changelog as a global surface

Date: 2026-08-01
Verdict: Revise, then accept

## Strongest counterargument

Removing the Changelog root from Fumadocs layout tabs also removes the selected
category control on Changelog routes. A custom placeholder switcher could keep
the four documentation categories one click away, but it would reproduce the
framework's desktop/mobile, accessibility, locale, and upgrade behavior for a
control that has no selected category on this standalone surface.

## Independent finding

The proposed boundary is sound because Fumadocs selects the nearest root for
the focused page tree independently from the layout-tab transform. However,
the existing global link used exact URL matching. It would be active on
`/docs/changelog` but not on `/docs/changelog/archive`, leaving the archive
without a global location cue after the category selector is removed.

The original Onyx citation also overstated the evidence and carried an
incorrect pinned SHA. The corrected evidence is narrower: Onyx links its
displayed version to a separate Changelog destination.

## Final choice

- Keep Changelog as an internal Fumadocs presentation root so Tegami-generated
  Latest, version anchors, and Older releases remain the focused local tree.
- Filter that root from `DocsLayout.tabs.transform`, leaving exactly four
  documentation categories.
- Intentionally show no category selector on Changelog and archive routes;
  Changelog is not a category and therefore has no valid selected category.
- Keep the localized global Changelog link visible and use nested URL matching
  so it remains active throughout the release-history subtree.
- Enforce the boundary in desktop/mobile E2E coverage for both locales.

## Rejected alternative

Do not build a parallel Changelog layout or copy Fumadocs' selector with an
unselected placeholder. The extra implementation and upgrade surface does not
improve the information architecture. Do not make Changelog a visible fifth
tab merely to preserve the selector, because that recreates the category
confusion this decision removes.
