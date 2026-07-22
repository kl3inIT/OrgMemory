# Reproducible Demo Bootstrap Plan

- [x] Remove XLSX-specific parsing and validation from production/runtime modules.
- [x] Export deterministic Keycloak, PostgreSQL, OpenFGA, document, and evaluation fixtures.
- [x] Add repository-level Gradle lifecycle tasks and idempotent bootstrap scripts.
- [ ] Add generic manifest-driven document import through the real source-ingestion API.
- [ ] Derive canonical source ACL and Knowledge Space relationships from declared access metadata.
- [ ] Run the public permission evaluation suite against indexed documents and report the supplied `P035` contradiction separately.
