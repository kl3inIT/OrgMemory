# Asset catalog list and grid

## Outcome

Make the employee Asset catalog scan efficiently at enterprise scale without
losing the more visual browsing mode.

## Product decisions

- The catalog opens in a compact list/table view.
- A grid toggle is available for visual browsing and is persisted in the URL.
- Search, type filtering, release sorting, and pagination remain server-driven.
- The catalog exposes total results and the current server range even when all
  results fit on one page.
- Asset types use the shared Lucide icon set. Work Instructions use
  `ListChecks`; Prompts use `Sparkles`; Capability Packs use `Boxes`; Skills use
  `FileArchive`.
- Each row or card has one clear, type-specific action and links to the exact
  release.

## Contract change

`AssetRecommendation` exposes the immutable release timestamp as `releasedAt`.
The value already exists on `AssetRelease`; this change only carries it through
the catalog projection so the list can communicate release recency.

## Accessibility and responsive behavior

- The view switch is a labelled button group with `aria-pressed`.
- The list remains horizontally scrollable on narrow viewports; lower-priority
  columns hide progressively.
- Grid cards use a single visible navigation action and preserve keyboard focus
  styles.
- Empty and loading states remain shared product patterns.

## Out of scope

- Asset authoring, review, publishing, installation, or marketplace behavior.
- New catalog ranking or recommendation logic.
- Client-side sorting of server-paginated results.
