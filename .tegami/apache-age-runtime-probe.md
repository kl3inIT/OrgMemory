---
packages:
  orgmemory: patch
subject: Repair Apache AGE startup for least-privilege roles
---

## Fixes

Apache AGE startup now verifies session preload through a bootstrap-owned
boolean probe instead of requiring the application to read all PostgreSQL
settings. Production-shaped conformance tests use a non-superuser role and keep
the broader `pg_read_all_settings` privilege denied.
