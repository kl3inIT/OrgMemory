# Sidebar-owned application chrome

## Problem

The shared desktop header repeats the active sidebar label and exists primarily
to hold theme and account controls. It consumes vertical space without adding a
page-specific action. Administration repeats the route title a third time in
the page heading.

## Design

- Desktop application chrome is owned by the sidebar.
- The sidebar header contains the product identity and its collapse control.
- The sidebar footer contains authorized administration navigation and the
  current user's account menu.
- Theme selection lives in the account menu.
- Pages own their headings and contextual actions.
- The shared top bar remains on narrow viewports because an off-canvas sidebar
  needs a persistent, keyboard-accessible reopen control.
- The account menu uses the existing Radix/shadcn primitives and the existing
  authenticated session. It does not introduce a second identity store.

This follows the useful part of Onyx's shell: stable navigation and identity in
the sidebar, with top chrome reserved for contextual actions. It does not copy
Onyx's chat-specific header before OrgMemory has equivalent actions.

## Permission administration

Permission screens use one consistent information hierarchy:

- a page heading and decision-relevant summary;
- a searchable, filterable collection;
- bounded pages with a human-readable range such as `1–10 of 42`;
- controls adjacent to the collection they affect;
- explicit empty results that distinguish "no data" from "no filter match".

Users and observed principals are client-filtered and paginated because the
current administration contract returns organization-scoped snapshots. This
matches the current Onyx users table behavior without pretending that
client-side pagination is the future large-tenant API contract. Server-side
cursor pagination remains a backend contract change for when the dataset
requires it.

## Accessibility

- Preserve the skip link and single `main` landmark.
- Preserve route-aware document titles.
- Keep a visible mobile sidebar trigger.
- Keep account, theme, administration, and sign-out actions keyboard reachable.
- Keep tooltips for collapsed sidebar controls.
- Give search, filters, pagination, and group disclosure controls explicit
  accessible names.
