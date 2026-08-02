# 0031 — Verify AGE preload through a least-privilege boolean probe

Status: accepted (2026-08-02)

## Context

The first production deployment of the explicit AGE backend rolled back after
the API became unhealthy. PostgreSQL correctly configured
`session_preload_libraries=age` for the application role, but that non-superuser
cannot read the setting directly without membership in `pg_read_all_settings`.
The original real-AGE test used the container superuser and did not expose the
privilege mismatch.

## Decision

The shared-database bootstrap owns
`orgmemory_runtime.age_session_preloaded()`, a SECURITY DEFINER function that
returns only whether the current database's configured session preload contains
AGE. Public access is revoked; the OrgMemory application role receives schema
usage and execute on this function only. AGE startup checks use that boolean,
then independently verify the extension and graph catalog.

Real AGE conformance runs through a production-shaped non-superuser role and
asserts that it is not a member of `pg_read_all_settings`.

## Rejected alternative

Granting `pg_read_all_settings` would make the old query work, but would expose
unrelated PostgreSQL configuration to the application solely to answer one
boolean readiness question. Silently dropping the preload check was also
rejected because it would defer a known configuration error until the first
graph operation.

## Operational evidence

Deployment workflow `30741127035` restored the previous immutable image set;
the prior API returned healthy after rollback. The corrected immutable image
set must pass a new automatic production deployment before this repair is
considered operationally verified.
