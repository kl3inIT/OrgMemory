# Production Hardening Design

This increment closes four small but high-impact runtime gaps without changing
the product model.

- OIDC logout sends Keycloak the exact registered post-logout redirect
  `WEB_BASE_URL/login`; the UI no longer depends on a query marker.
- Capability Asset collection reads use one OpenFGA `ListObjects`, at most one
  contextual `BatchCheck`, and one aggregate usage query. Single-resource
  mutations retain their canonical authorization checks.
- OpenAPI JSON and Swagger UI are disabled by default and enabled only by the
  `dev` profile. The security chain also denies those paths outside `dev`.
- The `prod` profile requires explicit database, OIDC, OpenFGA, object-storage,
  and AI settings. The API additionally rejects known local secrets, insecure
  public OIDC/web origins, missing routes, and capability-incompatible AI
  gateways during startup.

Authorization failures remain fail closed. Persistent OpenFGA relationships
may satisfy `ListObjects`; assets that still rely on transactional ownership or
visibility facts are checked together with resource-specific contextual tuples.
No per-row OpenFGA call or per-row usage count query remains on the list path.
