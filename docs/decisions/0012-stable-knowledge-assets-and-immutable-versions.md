# 0012 — Stable Knowledge Assets And Immutable Versions

## Status

Accepted on 2026-07-23.

## Context

The first ingestion slice used one `knowledge_assets` row as both immutable
content and the OpenFGA resource identity. A changed source therefore had no
safe place for version N+1, citations could not pin immutable content separately
from authorization identity, and graph indexing would inherit an ambiguous
lifecycle. Connector publication could also invoke OpenFGA while a caller-owned
PostgreSQL transaction was still uncommitted.

The retired Capability Asset MVP added a second unrelated asset lifecycle and
current-state documentation that no longer matched the product's secure
knowledge path.

## Decision

`KnowledgeAsset` is the stable governed resource and OpenFGA object identity.
`KnowledgeAssetVersion` is immutable content/security provenance with the linear
state transition `PENDING -> ACTIVE -> RETIRED`. Only one version may be active,
and the stable asset points to its current version. Archive is a tombstone on
the stable asset, not another version state.

`SourceObject` owns monotonically numbered immutable `SourceRevision` records.
Its latest pointer exposes in-progress work; its current pointer advances only
after successful publication. One version has a primary source revision and
append-only N:M evidence links for additional provenance.

Publication is split across independent transactions:

1. commit the pending version, inactive projections, evidence links, and outbox;
2. write OpenFGA relationships outside any caller transaction;
3. atomically activate the new version/projections, retire the prior version,
   advance stable/source heads, and mark the outbox applied.

Worker reconciliation republishes applied rows pinned to an obsolete OpenFGA
model and removes only OrgMemory-managed direct owner/Space tuples whose stable
asset no longer exists. Unknown, stale, incomplete, or unavailable decisions
fail closed.

Upload and connector ingestion share the same chunking and publication
contracts. Chunks, embeddings, and graph rows pin stable asset ID, immutable
version ID, source revision, profile, and projection generation.

The Capability Asset MVP is removed from runtime code, REST/OpenAPI, OpenFGA,
current specs, and forward schema. Historical Flyway migrations remain
immutable. A future governed workflow product may introduce a new capability
domain only from a separately approved design.

Because this decision lands before production data migration is required, V22
deliberately resets pre-versioned Knowledge Asset, upload, chunk, publication,
and graph-projection rows instead of carrying a compatibility backfill. Existing
source files must be imported again after the migration. This one-time reset
keeps the runtime model free of legacy identity rules.

## Consequences

Authorization relationships survive content updates, while retrieval and
citations remain reproducible against immutable versions. A failed external
authorization write cannot expose uncommitted or active content, and convergence
can repair model drift without inventing a second source of truth.

The schema and coordinator are more explicit than a single mutable document
row. That cost is accepted because source revisioning, permission-safe indexing,
graph provenance, rollback, and audit all depend on the distinction.

Graph extraction and Assistant graph retrieval remain separate follow-up work.
