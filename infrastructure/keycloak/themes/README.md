# OrgMemory Keycloak login theme

OrgMemory ships a login-only [Keycloakify](https://www.keycloakify.dev/) theme
named `orgmemory-shadcn` for the pinned Keycloak 26.7 runtime.

## Architecture

- The source package lives in `infrastructure/keycloak/theme-ui`.
- Keycloakify is pinned to `11.15.14` with an isolated React 18 / Vite 5 /
  TypeScript 5 toolchain.
- The package keeps Keycloakify's default page coverage and CSS. It does not
  eject authentication pages, add Storybook, or provide account/admin themes.
- `src/login/main.css` applies the OrgMemory shadcn `new-york` visual language,
  semantic OKLCH tokens, responsive light/dark behavior, and focus/motion
  accessibility guardrails.
- Hanken Grotesk and its SIL Open Font License are bundled locally. The login
  experience has no third-party font or asset requests.

## Immutable image packaging

The Keycloak Dockerfile builds the theme JAR in a disposable pinned Node/Maven
stage, copies `orgmemory-keycloak-theme.jar` into `/opt/keycloak/providers/`,
and runs `kc.sh build` before producing the runtime image. Generated JARs and
frontend build outputs are not committed.

Fresh realms select `orgmemory-shadcn` through the realm import. Existing realms
are reconciled only after the new image is running and the provider JAR is
present. Deployment rollback restores the prior realm theme before returning to
an older immutable image, including prior custom theme names that exist only in
that older image.

## Development and verification

```console
corepack pnpm install --filter @orgmemory/keycloak-theme
corepack pnpm --filter @orgmemory/keycloak-theme typecheck
corepack pnpm --filter @orgmemory/keycloak-theme build
infrastructure/deployment/scripts/test-keycloak-theme.sh
infrastructure/deployment/scripts/test-keycloak-mcp-onboarding.sh
```

`corepack pnpm --filter @orgmemory/keycloak-theme dev` provides a mocked login page for
fast styling. The contract test is the stronger gate: it builds the immutable
JAR image, starts the exact Keycloak version, and exercises login, recovery,
invalid credentials, local assets, desktop/mobile layout, and request/console
failures with Playwright.
