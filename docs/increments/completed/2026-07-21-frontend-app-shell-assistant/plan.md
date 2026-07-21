# Frontend App Shell And Assistant Plan

## 1 — Routing And Shell

- [x] Replace handwritten route switching with TanStack Router file routes.
- [x] Move authentication to a pathless protected route boundary.
- [x] Build a responsive shadcn application shell around routed content.
- [x] Keep navigation limited to the implemented assistant surface.

## 2 — Assistant Vertical Slice

- [x] Study Northstar's AI Elements composition and reuse its proven patterns.
- [x] Compose conversation, message, prompt, pending, and action states.
- [x] Connect AI SDK transport to the existing permission-aware chat endpoint.
- [x] Add one CSRF-aware browser mutation boundary.
- [x] Avoid synthetic citations until the backend emits evidence parts.

## 3 — Generated Contracts And Verification

- [x] Add official TanStack Router Vite generation.
- [x] Pin Hey API generation independently from TypeScript 7.
- [x] Add CI drift checks for generated API and route artifacts.
- [x] Run final lint, typecheck, build, backend, and browser gates.
- [x] Publish the branch and open the pull request; CodeRabbit review was
  attempted but rate-limited, with no code feedback produced.
- [x] Move this increment to completed after local and GitHub CI gates pass.
