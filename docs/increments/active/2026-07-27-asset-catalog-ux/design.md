# Asset Catalog UX

## Problem

The current employee-facing Asset page is titled **For your role**, although
the underlying query only returns released Assets the actor may use; it does not
rank recommendations by role. The catalog also returns an unbounded-looking
list without total, sorting, or page state, while the backend silently caps the
candidate set at 100.

The detail page mixes consumption with governance. It exposes internal enum
labels, principal UUIDs, release digests, and a governance action alongside the
primary task.

## Outcome

- Present the employee-facing surface as **Assets**.
- Keep search, type, sort, and page in the URL.
- Page and sort authorized released Assets on the server with a bounded page
  size and stable order.
- Reuse one shared collection pagination pattern across Assets and
  Administration.
- Use an Asset-type-specific action label in the catalog.
- Make the detail header consumption-first; move provenance behind disclosure
  and show governance only when the current actor has an accountable role.
- Make Pack detail show its ordered pinned items before the actor starts it.
- Reduce feedback to a secondary progressive-disclosure action.

## Constraints

- Authorization remains authoritative before catalog rows are queried.
- Withdrawn releases never appear in a catalog page.
- A page contains only the latest usable release for each Asset.
- Page size is bounded by the server.
- Sorting is explicit and stable; it never relies on database default order.
- Existing immutable releases and Pack progress semantics do not change.

