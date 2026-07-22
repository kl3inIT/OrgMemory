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
Spring AI ETL readers, chunks it, and requests embeddings. Publication first
commits a `PENDING` Knowledge Asset, inactive pgvector chunks, and a publication
outbox row in one database transaction. It writes the target
`knowledge_space#space` and uploader `owner` relationships to OpenFGA in one
idempotent request with duplicate-ignore semantics against the pinned model, then atomically
marks the outbox `APPLIED`, activates the asset/chunks, and publishes the ready
revision. Unknown or unavailable authorization writes fail closed and use the
leased ingestion job's durable retry path. The first verified profile is OpenAI
`text-embedding-3-large` at 1536 dimensions with cosine distance.

Every vector references an immutable organization-scoped `EmbeddingProfile`.
The vector column supports multiple dimensions, while each search and index
route is profile-specific and every supported dimension receives its own
partial expression index. Canonical source and evidence records remain
independent from rebuildable full-document, chunk, entity, relationship, and
graph projections.

Internal upload ACL evidence grants the organization; confidential upload ACL
evidence grants the target Space's department and therefore requires a
department-bound Space. Effective retrieval remains the intersection of Space
authorization, immutable/current source ACL, classification, tenant, and
lifecycle state.

The current path does not yet implement Airbyte or Slack staging contracts,
external source-group mapping, OCR, malware and DLP
integrations, entity and relationship extraction, graph publication, or hybrid
retrieval extensions beyond the current secure FTS + pgvector path.

## Source Modules

- `core.knowledge`
- `apps.api.source`
- `apps.worker.ingestion`
- `integrations.object-storage-minio`

## Related Decisions

- [0004](../../decisions/0004-manual-upload-is-a-first-class-source.md)
- [0005](../../decisions/0005-secure-java-graph-kernel.md)
- [0008](../../decisions/0008-worker-owns-ingestion-and-derived-indexes.md)
