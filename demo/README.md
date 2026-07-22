# Reproducible Demo Data

This directory contains synthetic, repository-owned fixtures for the enterprise
knowledge POC. A fresh clone can recreate the same identities, authorization
relationships, documents, and evaluation cases without running spreadsheet
parsing code in a production application.

## Fixture boundaries

- `datasets/` preserves the original workbook and its checksum as source evidence.
- `fixtures/postgres/directory.sql` seeds organization directory records and
  explicit OIDC identity bindings.
- `../keycloak/orgmemory-realm.json` provides the native Keycloak realm import.
- `fixtures/openfga/dataset-tuples.csv` provides native OpenFGA relationship tuples.
- `fixtures/documents/` contains Markdown documents plus a manifest for the real
  source-ingestion path.
- `fixtures/public-evaluation.json` preserves the public evaluation cases outside
  application runtime code.

The fixture IDs are deterministic and the PostgreSQL import is idempotent.
Keycloak only owns authentication identities. Application roles and resource
relationships remain in PostgreSQL and OpenFGA.

## Start the fixtures

```powershell
.\gradlew.bat demoBootstrap
.\gradlew.bat :apps:api:bootRun
# In another terminal after the API has applied Flyway migrations:
.\gradlew.bat demoSeed
```

`demoBootstrap` recreates only the ephemeral local Keycloak container so its
committed realm is re-imported. It creates a new local OpenFGA store and writes
the selected store/model IDs to `.openfga.local.properties`. It does not reset
PostgreSQL, MinIO, or any named Docker volume.

All synthetic Keycloak users use usernames `u001` through `u032` and the local
development password `orgmemory`. These credentials are strictly local demo
fixtures and must never be enabled in a deployed environment.

## Document ingestion

The Markdown files are intentionally not inserted into chunk, vector, or graph
tables. They must enter through OrgMemory's public source upload/ingestion flow,
which retains the original evidence, creates immutable revisions, embeds the
content, and publishes a permission-aware Knowledge Asset. Automated document
import is the next increment.

## Known dataset inconsistency

Public evaluation case `P035` expects a deny for `DOC030`, while the supplied
metadata marks that document `Internal` and accessible to `All Employees`.
OrgMemory preserves the source data and flags this as an evaluation-fixture
inconsistency instead of hard-coding a special authorization exception.
