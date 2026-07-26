# Shared Page System

## Problem

Feature pages currently own their page width, gutters, scrolling, heading
composition, toolbars, and empty states. The repeated `mx-auto`, `max-w-*`,
`p-*`, and bespoke header markup has produced multiple layout dialects:

- Documents and Knowledge graph share a centered `max-w-7xl` wrapper even
  though the graph is a spatial workspace.
- Asset, MCP, Sources, and Administration pages use different header and
  section structures.
- Loading, empty, error, and no-result states are implemented independently.
- Tables use the same semantic primitive but repeat rendering and state
  mechanics feature by feature.

This inconsistency will compound when more asset profiles such as Skill and SOP
receive browser surfaces.

## Reference

The local Onyx checkout under `D:\OrgMemory\tmp\onyx` uses a layered OPAL
system:

1. tokens and interaction primitives;
2. low-level controls;
3. namespaced layouts such as `RootLayout` and `SettingsLayouts`;
4. content composition such as `Content` and `ContentAction`;
5. feature-owned domain components.

The useful lesson is the contract, not Onyx's exact styling. OrgMemory keeps its
existing typography, semantic colors, shadcn/Radix primitives, and restrained
copy.

## Decision

Add an app-local shared page system rather than extracting a separate package.
There is only one web application, so a package boundary would add build and
release overhead without a second consumer.

### Layout components

`PageLayout` is a namespaced composition with:

- `Root`: owns width, gutter, height, and scroll behavior;
- `Header`: owns one primary heading, optional breadcrumb, metadata, and
  actions;
- `Tabs`: owns top-level workspace switching;
- `Toolbar`: owns responsive search/filter/action arrangement;
- `Body`: owns ordinary content spacing;
- `Canvas`: fills the remaining workspace for graph/editor surfaces.

`Root` variants:

- `narrow`: focused instructions and compact forms;
- `standard`: settings and detail content;
- `wide`: catalogs, tables, governance, and review;
- `full`: large data surfaces;
- `canvas`: height-bound spatial workspaces with no page-level scrolling.

`SplitLayout` provides `Root`, `Main`, and `Aside` composition for detail panels.
It must not own domain state.

### Content patterns

`Content` composes an optional icon, title, optional supporting copy, and
metadata. `ContentAction` adds a right-side action slot. Supporting copy remains
optional and is used only when it changes a decision, prevents an error, or
explains an unfamiliar action.

`FilterBar` standardizes responsive search/filter/result/action placement.

Existing route/page loading and error components remain canonical and gain a
shared `EmptyState` for feature-level empty and no-result states.

### Breadcrumbs

Breadcrumbs are conditional, not universal:

- top-level views such as Assets, Sources, Assistant, and Administration do not
  render one;
- nested resource and governance pages render stable hierarchy links;
- a back button may remain for transient wizards, but it does not replace a
  hierarchy breadcrumb.

### Frontend stack

Keep:

- React 19 and Vite;
- Tailwind 4 with existing semantic tokens;
- shadcn/Radix for UI primitives;
- TanStack Query for server state;
- TanStack Router for URL state;
- Zustand for durable or high-frequency local UI state;
- Zod for runtime validation;
- Playwright for browser flows.

Add `@tanstack/react-table` because the product already has repeated document
and administration tables and needs a headless, semantic table model for
sorting, filtering, pagination, and later server-controlled state. The shared
wrapper still renders OrgMemory's existing `<Table>` primitives and accessible
HTML.

Add Vitest with Testing Library for fast, behavior-facing shared component
contracts. Keep the suite narrow: shared layout semantics and table
sorting/empty behavior run here; routing, responsive layout, authentication,
and end-to-end product journeys stay in Playwright. Do not snapshot Tailwind
classes or duplicate each page journey as a component test.

Do not add Motion in this increment. Current transitions are simple,
self-contained CSS transitions. Motion becomes justified when a concrete flow
needs interruptible layout animation, presence animation, or gestures. If
introduced later, it must use a global reduced-motion policy and lazy features.

Defer:

- Storybook until the shared catalog is large enough to justify a second
  preview/build surface;
- drag-and-drop until pack or workflow authoring requires reordering;
- table virtualization until measured row counts require it;
- a form framework until complex asset editing produces repeated form-state
  behavior.

## Knowledge Graph

The graph view uses `PageLayout`'s `canvas` variant:

- it no longer inherits the Documents `max-w-7xl` wrapper;
- the graph surface uses `flex-1 min-h-0`;
- the fixed `min(72vh, 52rem)` cap is removed;
- the small app-shell inset remains as deliberate separation;
- graph controls and properties stay inside the graph workspace.

## Boundaries

- `components/ui` remains low-level shadcn/Radix primitives.
- `components/layouts` contains layout-only compositions.
- `components/patterns` contains repeated product-neutral compositions.
- asset status, release, evaluation, approval, and pack semantics remain under
  `features/assets`.
- Skill, SOP, Prompt, and other asset profiles reuse asset-domain components
  rather than creating parallel component families.

## Verification

- Oxlint;
- TypeScript project build;
- focused Vitest component contracts;
- Vite production build;
- focused browser validation for Documents, Knowledge graph, Assets, MCP, and
  Administration at desktop and narrow viewports;
- existing Playwright tests where they cover the migrated routes.

Completed evidence:

- Oxlint passed.
- TypeScript project build passed.
- Four focused Vitest assertions passed in two test files in about three
  seconds locally.
- Vite production build passed with the existing large-chunk advisory.
- All eight Playwright flows passed.
- Every feature data grid now renders through the TanStack-backed `DataTable`;
  the only direct table primitives left are the shared renderer itself.
- Admin Users was inspected in a real Chromium session with mocked API data;
  accessible sorting moved Alice to the first row and produced no browser
  errors.
- Assets and Knowledge graph were inspected at `1440x900` and `390x844`;
  neither produced horizontal overflow, and the graph canvas filled the
  remaining app-shell workspace.
