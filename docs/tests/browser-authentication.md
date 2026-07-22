# Browser Authentication Verification

## Automated Contracts

- Anonymous `/api/session` responses do not resolve a canonical actor.
- Authenticated OIDC sessions and bearer JWTs resolve the same explicit
  issuer/subject binding.
- Verified email and external admin-role claims cannot create a binding.
- Inactive and unlinked users fail closed.
- `/api/session/csrf` exposes the server-issued header, parameter, and token.

## Runtime Flow

1. Open a protected route without a session and verify it redirects directly to
   Keycloak while preserving the local return path.
2. Open `/login` after logout and verify the single enterprise SSO fallback
   action. For an authentication failure, verify the concise error message.
3. Complete Keycloak login and verify the internal name/email rendered by the
   workspace, not an external role mapping.
4. Refresh the workspace and verify the JDBC-backed session is restored.
5. Switch light/dark mode and confirm both surfaces remain usable.
6. Sign out and verify the browser returns to `/login` with the local and
   provider sessions ended; Keycloak must not return an invalid-redirect `400`.

Automated configuration contracts verify the exact provider logout redirect,
Swagger's default-off/dev-only profile settings, and production startup guards
for local secrets, insecure identity origins, and incomplete AI routes.

Use `linh` / `orgmemory` only for the local imported demo realm. Never use demo
credentials outside local development.
