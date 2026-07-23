# Frontend Design System Foundation

## Outcome

OrgMemory keeps its existing shadcn/Radix and AI Elements primitives while
introducing a small semantic design-token layer that gives light and dark themes
the same component contract. The authenticated shell reads page identity from
typed route metadata, and Sonner remains the notification engine behind one
product-level toaster composition.

## Decisions

- Keep Vite, TanStack Router/Query, shadcn/Radix, AI Elements, Zustand, and
  Sonner. Do not port Onyx Opal, SWR, Next.js, or its custom toast store.
- Separate primitive palette values, product semantics, shadcn compatibility
  aliases, Tailwind exposure, and base styles into small CSS files.
- Components use semantic state tokens; light and dark mode change token values
  rather than adding per-component `dark:` color overrides. The chart theme
  selector remains the one legitimate mode-specific data mapping.
- Keep process, route, query, URL, and local UI state ownership unchanged.
- Configure Sonner once through `AppToaster`; ingestion progress continues to
  live in Documents rather than in persistent toast state.
- Use TanStack Router `staticData` for shell page titles instead of matching raw
  pathnames.

## Scope

- Semantic light/dark color, surface, border, interaction, status, assistant,
  and control tokens.
- Compatibility aliases for existing shadcn components.
- Semantic interaction states for the primitives currently carrying manual
  dark-mode color overrides.
- App-level toaster composition and typed route titles.
- Browser verification of login, Assistant, Documents, narrow layout, and both
  themes.

## Non-goals

- Sentry, OpenTelemetry, a custom external toast store, a notification center,
  source-preview right panel, Assistant packet timeline, or a new brand system.
- Replacing shadcn or AI Elements.
- Changing backend contracts or product permissions.

## Exit Criteria

- Existing routes render correctly in light and dark mode with semantic tokens.
- Components no longer carry mode-specific color overrides except chart theme
  data mapping.
- Shell title comes from the deepest active route metadata.
- Toasts are themed, capped, dismissible, and remain callable through Sonner.
- Web lint, typecheck, production build, and real-browser checks pass.
