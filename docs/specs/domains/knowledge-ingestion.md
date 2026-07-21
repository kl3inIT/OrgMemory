# Knowledge Ingestion Spec

## Current Behavior

The backend service and integration tests persist one source-shaped
`RawSourceObject`, one
`NormalizedRecord`, and one `KnowledgeAsset` per implemented leaf. This is not a
public ingestion endpoint. Tenant and
provenance constraints protect promotion. Optional citation URIs must be
absolute credential-free HTTP(S) locations.

The current path does not accept direct file uploads, object-store blobs,
Airbyte staging records, external source groups, multiple source contributions,
OCR, malware/DLP results, or durable pipeline checkpoints. The worker only
contains the permission-workbook validator plus job scaffolding.

## Source Modules

- `core.knowledge`
- `apps/api.knowledge`
- `apps/worker.dataset`

## Related Decisions

- [0004](../../decisions/0004-manual-upload-is-a-first-class-source.md)
- [0008](../../decisions/0008-worker-owns-ingestion-and-derived-indexes.md)
