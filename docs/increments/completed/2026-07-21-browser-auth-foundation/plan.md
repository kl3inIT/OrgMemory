# Browser Authentication Foundation Plan

## 1 — Server Boundary

- [x] Add Spring Security OAuth2 Client and Spring Session JDBC.
- [x] Add versioned Spring Session tables through Flyway.
- [x] Configure a confidential Keycloak client without a committed real secret.
- [x] Separate stateless bearer requests from browser session requests.
- [x] Add session, CSRF, login success/failure, and OIDC logout behavior.
- [x] Resolve both JWT and OIDC-session principals through the canonical actor.

## 2 — Contract And Web

- [x] Commit the OpenAPI contract and generate Fetch/Zod/TanStack artifacts.
- [x] Configure generated requests for same-origin session cookies and CSRF.
- [x] Replace automatic browser-token auth with a session query.
- [x] Adapt the official shadcn `login-01` block to one enterprise SSO action.
- [x] Replace prototype routes with login plus the authenticated workspace shell.
- [x] Auto-redirect protected routes while retaining `/login` for logout/error
  fallback and validating the server-side return path.

## 3 — Verification And Consolidation

- [x] Add focused JWT/session actor and security contract tests.
- [x] Run compile, API tests, clean tests, web typecheck, and web build.
- [x] Run login, refresh, logout, failure, and both-theme browser checks.
- [x] Consolidate architecture, identity spec, test matrix, and roadmap facts.
