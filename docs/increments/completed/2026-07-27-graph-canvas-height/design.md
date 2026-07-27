# Graph Canvas Height

## Problem

The full-height Sources workspace gives the graph canvas its remaining page
height through flex layout. `PageLayout.Canvas` was itself a flex item, but it
did not establish a flex formatting context for its `SplitLayout` child.
Consequently the nested `SigmaContainer` could resolve `height: 100%` against a
zero-height chain and abort the route when a non-empty graph was rendered.

The empty graph state did not instantiate Sigma, so it concealed the regression
until permission-visible graph data returned in production.

## Decision

`PageLayout.Canvas` establishes a flex formatting context. Its existing
`min-height: 0` and `flex: 1` contract then propagates the bounded application
viewport through `SplitLayout` to Sigma.

Do not:

- restore a fixed viewport-height graph;
- enable Sigma `allowInvalidContainer`;
- defer initialization with timing-based retries.

Those alternatives either break the full workspace layout or conceal an invalid
container rather than repairing it.

The graph toolbar also uses the headless React Sigma `useFullScreen` behavior
through OrgMemory's existing `IconControl`. React Sigma's prebuilt
`FullScreenControl` applies `className` to an outer wrapper rather than its
native button, so it cannot share the size, theme, hover, focus, and tooltip
contract of the surrounding shadcn controls.

## Verification

- protect the shared canvas layout contract with the focused Vitest test;
- run web lint, typecheck, unit tests, and production build;
- verify the populated graph and aligned fullscreen control in a real browser
  with no Sigma console error.

## Completion Evidence

PR #75 merged as `4fa215cfc66b60311f725767858fed4829ebdeff`.
Main CI run `30237427612`, production image run `30237507156`, and production
deployment run `30237618292` passed. The deployed Bùi Đức Minh account rendered
92 entities and 129 relations with no console errors. The graph canvas resolved
to a non-zero full workspace height, all camera and fullscreen controls measured
32 by 32 pixels, and entering and exiting fullscreen succeeded.
