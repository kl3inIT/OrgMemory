# Frontend App Shell And Assistant Design

## Outcome

OrgMemory has a small authenticated application shell and one useful product
surface: a permission-aware AI workspace. The implementation reuses AI Elements
for chat primitives, keeps identity at the protected route boundary, and avoids
carrying the discarded prototype navigation into the new product.

## Decisions

- TanStack Router file routes define the root, public login, and pathless
  authenticated layout.
- The authenticated route resolves the browser session before rendering and
  passes the verified identity into the shell.
- The shell uses shadcn Sidebar primitives and exposes only implemented routes.
- The assistant composes AI Elements conversation, message, response, actions,
  and prompt-input primitives instead of owning a parallel chat design system.
- AI SDK `DefaultChatTransport` calls the existing `/api/ai/chat` contract with
  only the current message and conversation identifier.
- Browser mutations go through one CSRF-aware fetch boundary.
- The response renderer is loaded only after assistant output exists so the
  initial workspace does not pay the full markdown-rendering cost.
- Hey API generation is pinned in an isolated command because the current
  generator release is not compatible with the workspace TypeScript 7 runtime.
  The generated client remains committed and drift-checked in CI.
- The current backend emits text only. The web must not invent citations or
  evidence that the server has not supplied.

## Runtime Flow

```mermaid
flowchart LR
    ROUTE[Protected route] -->|session query| BFF[Spring browser session]
    ROUTE -->|verified identity| SHELL[App shell]
    SHELL --> ASK[Assistant workspace]
    ASK -->|CSRF plus message| CHAT[/api/ai/chat]
    CHAT -->|permission-aware stream| ASK
```

## Exit Criteria

- Protected routes redirect unauthenticated users through the BFF login flow.
- The shell is responsive, keyboard reachable, and contains no dead navigation.
- Empty, pending, streaming, failed, and completed assistant states are visible.
- The browser sends the exact chat contract and the server-issued CSRF header.
- Generated API and route artifacts are reproducible and checked by CI.
- Lint, typecheck, build, and real browser checks pass in light and dark modes.
