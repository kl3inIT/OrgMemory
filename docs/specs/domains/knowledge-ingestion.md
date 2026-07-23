# Knowledge Ingestion Spec

## Current Behavior

An authenticated user can upload PDF, DOCX, PPTX, TXT, or Markdown through the
Documents API and web view. The user must select a Knowledge Space returned by
OpenFGA `ListObjects(can_create_asset)`, and the mutation rechecks
`can_create_asset` before writing evidence. MinIO stores immutable evidence bytes
behind the provider-neutral object-storage contract. PostgreSQL persists canonical
`SourceObject`, `SourceRevision`, `EvidenceBlob`, and leased durable ingestion
jobs with the target Knowledge Space identity.

The worker validates content integrity, parses and normalizes text through the
Spring AI ETL readers, then uses one shared chunking contract for uploads and
connectors before requesting embeddings. `KnowledgeAsset` is the stable
authorization identity. `KnowledgeAssetVersion` is immutable content/security
provenance with a linear `PENDING -> ACTIVE -> RETIRED` lifecycle; at most one
version is active for one asset. Review decisions and processing failures are
separate evidence, not extra asset lifecycle states.

Publication uses three independent transaction phases:

1. PostgreSQL commits the pending version, inactive pgvector chunks,
   append-only evidence links, and a pending outbox row.
2. The worker writes the target `knowledge_space#space` and uploader `owner`
   relationships to OpenFGA outside the caller transaction, using one
   idempotent request against the pinned model.
3. PostgreSQL atomically retires the prior active version, activates the new
   version/chunks, advances the stable asset and current source-revision heads,
   and marks the outbox `APPLIED`.

Unknown or unavailable authorization writes fail closed and use the leased
ingestion job's durable retry path. A scheduled reconciliation sweep republishes
applied rows whose authorization model is obsolete and removes only managed
direct owner/Space tuples for stable asset IDs that no longer exist. The first
verified profile is OpenAI `text-embedding-3-large` at 1536 dimensions with
cosine distance.

Every vector references an immutable organization-scoped `EmbeddingProfile`.
The vector column supports multiple dimensions, while each search and index
route is profile-specific and every supported dimension receives its own
partial expression index. Canonical source and evidence records remain
independent from rebuildable full-document, chunk, entity, relationship, and
graph projections.

Public and internal upload ACL evidence grants the organization; confidential
upload ACL evidence grants the target Space's department and therefore requires
a department-bound Space. Restricted upload evidence remains organization-bound
while the independent classification gate limits retrieval to executives.
Effective retrieval remains the intersection of Space authorization,
immutable/current source ACL, classification, tenant, and lifecycle state.

External source principals observed from a source are recorded in a
`source_principals` registry (observation grants nothing) and resolved to active
internal users through a verified `source_principal_mappings` ledger. Automatic
matching runs a trusted issuer/subject IdP join first, then an SSO-verified email
join gated on the principal's own `sso_verified` flag as the crawl reported it;
unverified tails use explicit self-claim or admin confirmation. An administrator
can record a standing per-connection identity trust decision in
`source_connections` (`UNTRUSTED` by default), but no ingestion path reads it yet;
it is stored governance intent until the live adapter consumes it. Each
mutation keeps at most one active mapping per principal and appends a permission
audit event. A `SOURCE_USER` ACL entry grants only through an active mapping to
the querying user; a `SOURCE_GROUP` entry grants only through that snapshot's
sealed group membership joined to an active mapping. Any unmapped, revoked, or
inactive principal grants nothing. Per [ADR 0009](../../decisions/0009-dynamic-source-acl-ceiling.md),
live (non-upload) sources enforce the current sealed ACL generation as the
ceiling while upload sources keep the ingestion-current intersection.

A fixture-driven Slack connector ingests a versioned staging contract
(`contracts/connector/`: three separately-versioned payload kinds — content,
identity, permissions — plus tombstones and an opaque crawl cursor) through a
dedicated `ConnectorIngestionService`. It observes and matches external
principals, then seals ACL generations carrying `SOURCE_GROUP`/`SOURCE_USER`
evidence and channel membership through package-private connector-aware
seal/rotate methods on `KnowledgeIngestionService` that the public upload path
does not expose (the upload path still rejects external principals). A new object
materializes content by reusing the `normalize` and `publish` use cases, with
chunks embedded through a `ConnectorTextEmbedder` port; a membership re-crawl
appends a sealed generation and rotates the head without re-materializing content,
converging grants and revocations under the [ADR 0009](../../decisions/0009-dynamic-source-acl-ceiling.md)
live-source ceiling; changed content creates source revision N+1 and publishes
a new immutable Knowledge Asset version through the same chunking/publication
contracts used by upload; a tombstone retires the `SourceObject` from retrieval.
Each object reconciles in its own preparation transaction so a per-object
failure is isolated, publication begins only after that transaction commits,
and an unknown payload version fails closed. A worker `ConnectorBatchSource`
port (fixture implementation now) feeds the driver.

The current path does not yet implement the live Slack Web API adapter
(credentials, rate limiting, checkpoint/resume, webhooks), Airbyte staging,
OCR, malware and DLP integrations, entity and
relationship extraction, graph publication, or hybrid retrieval extensions beyond
the current secure FTS + pgvector path.

## Source Modules

- `core.knowledge`
- `apps.api.source`
- `apps.worker.ingestion`
- `apps.worker.connector`
- `contracts/connector` (staging contract)
- `integrations.object-storage-minio`

## Related Decisions

- [0004](../../decisions/0004-manual-upload-is-a-first-class-source.md)
- [0005](../../decisions/0005-secure-java-graph-kernel.md)
- [0008](../../decisions/0008-worker-owns-ingestion-and-derived-indexes.md)
- [0009](../../decisions/0009-dynamic-source-acl-ceiling.md)
- [0012](../../decisions/0012-stable-knowledge-assets-and-immutable-versions.md)
