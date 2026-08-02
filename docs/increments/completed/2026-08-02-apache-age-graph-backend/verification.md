# Explicit Apache AGE graph backend verification

Verified: 2026-08-02

## Outcome

- `APACHE_AGE` and `RELATIONAL` are exact runtime topology selections.
- `APACHE_AGE` is the default and fails startup when AGE is not usable; there
  is no automatic relational fallback.
- AGE topology, its exact publication-batch ready marker, and relational graph
  staging commit together. Permit-bound discard removes the same batch.
- AGE traversal pages apply tenant, batch, authorized-asset, entity, and cursor
  bounds before deduplication, ordering, and limit; relational evidence and the
  core traversal coordinator remain authoritative.

## Evidence

- Characterization commit: `86ca18cd`.
- Design and challenge commit: `02df71f8`.
- Runtime and real AGE conformance commit: `6cb1a251`.
- Current-main merge was completed before final verification.
- `gradlew.bat --no-daemon :integrations:graph-rag-postgres:test` — passed.
- `gradlew.bat --no-daemon clean test` — passed in 9m44s, 108 tasks.
- `python scripts/check_docs.py` — passed.
- `git diff --check` — passed.

IDE semantic inspection was unavailable in this environment. The documented
fallback was the clean Java compilation plus focused and full terminating test
gates above.

## Remaining operational evidence

No production-scale latency claim is made here. A representative multi-tenant
corpus benchmark and the existing production canary remain separate operational
evidence, not a condition for selecting the correct backend or preserving its
authorization contract.
