# Page rhythm follow-up

## Problem

The shared page system shipped the correct width, gutter, header, canvas, and
table primitives, but ordinary page content was still rendered directly under
`PageLayout.Root`. `PageLayout.Body` existed and was not used by any feature.
As a result, independent controls and surfaces visually touched:

- the Asset catalog filter touched the first card row;
- Administration cards touched the following directory search;
- the MCP client tabs and capability callout touched adjacent cards;
- the same missing body rhythm affected Asset detail, Pack journey, governance,
  and user-permission pages.

This is a layout-contract defect, not an intended stacked-card treatment. The
surfaces retain independent rounded borders, so zero spacing communicates a
relationship the product does not have.

## Reference evidence

The local Onyx reference keeps settings chrome and page content separate:

- `SettingsLayouts.Header` owns heading-level controls;
- `SettingsLayouts.Body` adds top padding and a consistent vertical gap;
- list toolbars add an explicit bottom gap before cards or tables;
- persistent protocol facts remain ordinary content; `MessageCard` is reserved
  for contextual, actionable notices.

OrgMemory keeps its own visual language. It adopts the composition contract,
not Onyx styling.

## Decision

1. `PageLayout.Body` owns `pt-6` and `gap-6`.
2. `AdminPage` wraps all administration content in that body once.
3. Ordinary Asset and MCP pages explicitly compose `Header` then `Body`.
4. Asset catalog filters live in the page header because they control the whole
   catalog; the result grid lives in the body.
5. The MCP page removes the hard-coded `Available` status and decorative
   protocol badges. Read-only OAuth is stated once in the endpoint description.
   Permission and mutation limits remain as one subdued supporting sentence.
6. Canvas and Sources tab layouts keep their specialized spacing contracts.

## Verification

- focused PageLayout component test;
- Asset golden-flow browser assertion that the filter and result grid do not
  touch;
- MCP browser assertions for the simplified hierarchy;
- frontend lint, typecheck, production build, Vitest, and Playwright.
