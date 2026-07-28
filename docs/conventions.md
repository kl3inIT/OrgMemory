# Repository Conventions

## Backend

- Java package root is `com.orgmemory`.
- Business rules live in `core`; delivery apps translate protocols.
- Spring Modulith packages are the default domain boundary.
- A Gradle subproject is justified only by deployability, reusable engine code,
  or a replaceable external integration.
- Use ports at external/provider boundaries; avoid generic interfaces around
  every class.
- For reusable AI engines, prefer an executable functional core and an
  imperative framework shell. Framework adapters may implement model,
  persistence, telemetry, and delivery ports; framework types must not leak
  into deterministic algorithm contracts.
- Keep `ddl-auto=validate`; pair every persisted model change with Flyway.
- For populated tables, use expand/bounded-backfill/validate migrations. Do not
  run `CREATE INDEX CONCURRENTLY` inside application-owned Flyway because its
  schema-history connection can block the concurrent build; pre-stage those
  indexes through the deployment pipeline for large-table upgrades.
- API runs migrations. Worker and MCP do not own schema evolution.
- Use durable idempotency, outbox, and compare-and-set at retried ingestion and
  projection boundaries.
- OpenFGA models use schema 1.1, singular lowercase types, assignable noun
  relations, and computed `can_*` permissions. Business roles are tuple data,
  not Java enums or authorization-model relation names.
- Bind custom application configuration through typed `@ConfigurationProperties`.
  Do not inject scattered `@Value` expressions or read environment variables
  directly from domain, application, or adapter code.

## Security And AI

- Tenant and permission context comes from the authenticated actor, never request
  ownership fields.
- Source ACL is a hard ceiling; `UNKNOWN` fails closed.
- Filter evidence before ranking/limit/context assembly and recheck citations.
- Treat uploaded, connected, and retrieved content as untrusted data, not system
  instructions.
- Provider credentials stay outside clients, logs, docs, and git.
- In-app agent and MCP call the same use cases; MCP is not a privileged bypass.

## Web

- Do not use the generic `frontend-design` skill for OrgMemory. Extend the
  established product shell and tokens, inspect the named upstream reference,
  and make small repo-native changes instead of introducing a new aesthetic.
- Prefer shadcn/ui local primitives and maintained domain libraries.
- Before composing a control by hand, check the installed shadcn/Radix
  primitives and registry. The Sources workspace owns top-level app tabs such as
  Documents and Knowledge graph. Document-status switching is a separate nested
  Tabs control and must not reuse the app-navigation treatment.
- Generate ordinary REST clients, Zod schemas, and TanStack Query options from
  the committed OpenAPI contract with Hey API. Do not duplicate generated DTOs
  or endpoint calls by hand.
- Handwritten feature queries are allowed when they add product behavior such as
  view-model mapping, optimistic updates, or deliberate cache invalidation.
  Handwritten transports are reserved for protocol-specific behavior such as
  streaming, upload progress, and browser-navigation logout; keep them thin and
  document why generation is not sufficient.
- Product-specific composition belongs in features; generic primitives do not.
- Page composition belongs in `components/layouts`: use the namespaced
  `PageLayout` width/scroll variants instead of repeating `mx-auto`,
  `max-w-*`, and page gutters in features. Use `SplitLayout` for a main surface
  with a responsive detail panel. Product-neutral repeated compositions such as
  `FilterBar`, `EmptyState`, and `DataTable` belong in `components/patterns`.
- Use the shared headless `DataTable` for repeated table mechanics. TanStack
  Table owns sorting/filtering/pagination state while the existing shadcn table
  primitives keep the semantic HTML and OrgMemory styling. Prefer controlled,
  server-side pagination before loading an unbounded collection, and add
  virtualization only after measured row counts require it.
- TanStack Query owns server state and TanStack Router owns the current route.
  Zustand is for durable UI preferences and high-frequency local interaction
  state; never mirror query data or the active URL in a second store.
- Use one primary heading per view. Add subtitles or helper text only when they
  change a decision, prevent an error, or explain an unfamiliar action; never
  repeat the heading, button label, or nearby copy in different words.
- Preserve light and dark themes, keyboard access, visible loading/error states,
  and narrow/mobile layouts.
- The current prototype page structure is not a compatibility contract.

## Verification

Run the narrowest tests while iterating and completion gates before handoff:

```powershell
.\gradlew.bat --no-daemon compileJava
.\gradlew.bat :core:test
.\gradlew.bat --no-daemon clean test
corepack pnpm -C web typecheck
corepack pnpm --dir web run test:unit
corepack pnpm -C web build
Push-Location integrations\authorization-openfga\src\test\openfga
& '..\..\..\..\..\.tools\openfga\fga.exe' model test --tests store.fga.yaml
Pop-Location
```

Use a real browser flow when UI behavior changes. Do not claim runtime behavior
from compilation alone.

## Commits

- Keep each commit to one coherent change and stage files intentionally.
- Use an imperative subject; prefer the established `type(scope): subject`
  form when a useful scope exists.
- Do not mix generated artifacts, unrelated formatting, or another worktree's
  changes into the commit.
- A commit that changes durable behavior also carries its documentation
  consolidation and relevant verification evidence.
