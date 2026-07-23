# Knowledge Asset Foundation

## Problem

The current `knowledge_assets` row is an immutable content version, but its name
also acts as the stable OpenFGA resource identity. `SourceRevision` is fixed at
revision one, connector publication can call OpenFGA while the surrounding
PostgreSQL transaction is still uncommitted, connector chunking bypasses the
worker pipeline, and the retired Capability Asset prototype still appears in
code, schema, OpenAPI, authorization, and current docs.

Graph indexing must not be wired on top of those ambiguous boundaries.

## Design

### Canonical identity and versions

`KnowledgeAsset` is the stable governed resource. It owns the organization,
Knowledge Space, optional originating `SourceObject`, current version pointer,
and optional archive tombstone. It does not have a workflow state machine.

`KnowledgeAssetVersion` is immutable knowledge content and security provenance.
Its lifecycle is linear:

```text
PENDING -> ACTIVE -> RETIRED
```

Only one version may be active per asset. OpenFGA tuples target the stable asset
ID. Retrieval and citations additionally pin the active version ID.

One source-backed version has one primary `SourceRevision`. Derived knowledge
uses append-only evidence links so one version can cite multiple revisions and
spans without weakening the intersection of their ACL ceilings.

### Revision and projection ownership

`SourceObject` owns monotonically increasing revision numbers. Registering the
same content hash is idempotent; changed content creates revision N+1. The
current revision pointer advances only after its revision is ready.

The publication coordinator allocates the next projection generation from the
version/publication ledger. Callers do not hard-code it. Chunk and graph rows
are rebuildable projections tied to stable asset, immutable version, source
revision, embedding profile, and generation.

### Publication boundary

Publication is a three-step process:

1. PostgreSQL prepare commits the pending version, inactive chunks, evidence
   links, and pending outbox row.
2. OpenFGA is called outside any caller-owned transaction.
3. PostgreSQL completion atomically activates chunks/version, retires the prior
   version, advances asset/revision heads, and marks the outbox applied.

Every coordinator phase uses an independent transaction. Connector code first
commits source/revision/normalization preparation, then invokes publication
outside that transaction. Failed OpenFGA writes remain retryable evidence.

Reconciliation compares pending/applied publication rows with OpenFGA state,
removes tuples whose asset no longer exists, and republishes rows pinned to an
obsolete authorization model before retrieval can use them.

### Legacy removal

The Capability Asset MVP is removed from Java, REST/OpenAPI, OpenFGA, demo
tuples, skills, specs, and current architecture. A forward Flyway migration
drops its unused tables; historical migrations remain immutable.

## Non-goals

- Wiring graph extraction or graph retrieval into worker/Assistant.
- Adding review workflow states to `KnowledgeAsset`.
- Implementing live Slack Web API calls.
- Adding Neo4j, OpenSearch, Kafka, or a generic event framework.

## Safety invariants

- A version is immutable after creation.
- At most one active version exists for one stable asset.
- Retrieval requires the asset current-version pointer, active version,
  current source revision, applied publication, matching OpenFGA model,
  embedding profile, and projection generation.
- OpenFGA failure, stale model, missing evidence link, or PostgreSQL/OpenFGA
  divergence fails closed.
- Review decisions and processing failures are evidence records, not extra asset
  lifecycle states.
