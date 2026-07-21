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

## Consequences

Pipeline states and failures are visible. API and worker are separate processes,
so in-process Modulith events cannot be the job transport. A database outbox plus
leased/claimed durable jobs is the default until another transport is justified.
At-least-once delivery, idempotency,
checkpoint/tombstone, reconciliation, quarantine, and retry semantics are part
of the contract from the first production slice.
Independent source revisions may execute in parallel, while publication for one
source/revision key is serialized by claim/version semantics.
