# 0008 — Worker Owns Ingestion And Derived Indexes

## Status

Accepted on 2026-07-20.

## Context

Uploads, parsing, OCR, extraction, embedding, graph publication, and connector
reconciliation are expensive and retryable. Running them on request threads
creates latency, partial publication, and weak recovery semantics.

## Decision

API accepts commands and exposes state. Worker owns durable, idempotent stages
and atomic head publication. Airbyte or custom connectors write versioned staging
contracts only. Search, graph, and OpenFGA updates are rebuildable projections
published from canonical ledger/outbox state.

Embedding configuration is not a mutable global string. An immutable,
organization-scoped embedding profile identifies provider, model, dimensions,
and distance metric. A revision and every derived vector pin the profile used to
produce them. PostgreSQL can store mixed vector dimensions, while physical
indexes and search requests are routed to exactly one profile and dimension.
Profile-key components are encoded before serialization. Resolution verifies
that an existing row still matches all requested settings and fails
deterministically on mismatch; changed settings require a new profile key.

## Consequences

Pipeline states and failures are visible. API and worker are separate processes,
so in-process Modulith events cannot be the job transport. A database outbox plus
leased/claimed durable jobs is the default until another transport is justified.
At-least-once delivery, idempotency,
checkpoint/tombstone, reconciliation, quarantine, and retry semantics are part
of the contract from the first production slice.
Independent source revisions may execute in parallel, while publication for one
source/revision key is serialized by claim/version semantics.
Supporting a new vector dimension requires an explicit partial expression index
and a profile-scoped rebuild. Embeddings from different profiles are never
compared in one ranking operation.
