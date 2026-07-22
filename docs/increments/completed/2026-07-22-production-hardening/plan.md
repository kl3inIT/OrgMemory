# Production Hardening Plan

- [x] Replace the invalid Keycloak post-logout redirect and remove the obsolete
  UI query marker.
- [x] Extend batch authorization requests with per-resource contextual tuples.
- [x] Replace Capability Asset list-path OpenFGA and usage-count N+1 calls.
- [x] Disable OpenAPI/Swagger by default and expose it only in `dev`.
- [x] Add explicit API and worker production configuration contracts.
- [x] Add production semantic validation for secrets, HTTPS identity origins,
  OpenFGA, object storage, and both AI routes.
- [x] Run full backend, frontend, OpenFGA, static-analysis, and runtime gates.
- [x] Consolidate current architecture, specs, tests, and roadmap.
- [x] Leave the verified hardening slice isolated on its implementation branch.
