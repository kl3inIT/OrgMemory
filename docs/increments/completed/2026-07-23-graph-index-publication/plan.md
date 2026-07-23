# Graph Index Publication Plan

## Contracts

- [x] Add deterministic extraction-to-contribution assembly and tests.
- [x] Add one atomic contribution-and-embedding publication contract.
- [x] Strengthen PostgreSQL publication and read-time current-version checks.

## Durable Worker Flow

- [x] Persist graph-index jobs when source-backed Knowledge Asset versions
  become active, including a migration backfill for existing active versions.
- [x] Claim pinned graph inputs with a multi-replica-safe lease.
- [x] Extract chunks in bounded batches and renew the lease between batches.
- [x] Embed entity/relation contributions with the version's immutable
  organization embedding profile.
- [x] Publish the complete graph generation atomically and record success,
  retry, failure, or supersession.

## Verification

- [x] Prove deterministic canonicalization and per-evidence contribution
  isolation.
- [x] Prove an embedding/publication failure leaves no partial new generation.
- [x] Prove retries are idempotent and superseded versions are not visible.
- [x] Run focused component, PostgreSQL integration, worker integration, full
  Gradle, migration/mechanical, and repository documentation gates.
