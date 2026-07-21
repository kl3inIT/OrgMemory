# Repository Conventions

## Source Of Truth

`ARCHITECTURE.md` and specs record only implemented facts. Vision and roadmap
record intent. Active increments contain design and execution plans. Decisions
are append-only; supersede them explicitly instead of silently rewriting history.
Do not create status-summary documents that duplicate those sources.

## Backend

- Java package root is `com.orgmemory`.
- Business rules live in `core`; delivery apps translate protocols.
- Spring Modulith packages are the default domain boundary.
- A Gradle subproject is justified only by deployability, reusable engine code,
  or a replaceable external integration.
- Use ports at external/provider boundaries; avoid generic interfaces around
  every class.
- Keep `ddl-auto=validate`; pair every persisted model change with Flyway.
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

- Prefer shadcn/ui local primitives and maintained domain libraries.
- Generate ordinary REST clients, Zod schemas, and TanStack Query options from
  the committed OpenAPI contract with Hey API. Do not duplicate generated DTOs
  or endpoint calls by hand.
- Handwritten feature queries are allowed when they add product behavior such as
  view-model mapping, optimistic updates, or deliberate cache invalidation.
  Handwritten transports are reserved for protocol-specific behavior such as
  streaming, upload progress, and browser-navigation logout; keep them thin and
  document why generation is not sufficient.
- Product-specific composition belongs in features; generic primitives do not.
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
corepack pnpm -C web build
.\.tools\openfga\fga.exe model test --tests store.fga.yaml
```

Use a real browser flow when UI behavior changes. Do not claim runtime behavior
from compilation alone.
