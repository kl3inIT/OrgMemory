# Architecture Challenge Brief - Manual Failed Upload Retry

Date: 2026-08-05  
Base commit: `876246da`

## Reviewer Role

Attack this proposal. Do not validate it by default. Remain read-only, inspect
the cited implementation, and find a concrete path that could duplicate,
publish without authority, cross a tenant boundary, or resurrect terminal work.

OrgMemory is a governed organizational-memory layer. Uploaded evidence may
become searchable and graph-visible only after canonical publication and the
current permission intersection. A helpful retry button cannot weaken that
promise.

## Exact Proposal

> Add `POST /api/sources/{sourceId}/retry` for an ACTIVE native upload whose
> latest revision and unique ingestion job are FAILED. Require the authenticated
> actor to be the original uploader and recheck `knowledge_space
> can_create_asset`. Transactionally reset the same job and revision to a fresh
> bounded retry cycle. Reject QUARANTINED, processing, READY, connector,
> archived, cross-tenant, and unrelated-user sources through the opaque
> not-found contract. Reuse publication idempotency if publication already
> applied before Source Ingestion recorded READY.

The proposed design is in
`docs/increments/active/2026-08-05-knowledge-operations-graph-inspector/design.md`.

## Repository Evidence To Verify

- `SourceIngestionJob` has one job per revision, bounded attempts, and terminal
  FAILED, but no public manual reset today.
- `SourceIngestionCoordinator.fail` clears the claim before terminal FAILED.
- `KnowledgeAssetPublicationCoordinator.complete` activates chunks/version,
  advances the source head, and marks an outbox APPLIED in a `REQUIRES_NEW`
  transaction before `SourceIngestionCoordinator.complete` marks the revision
  READY and job SUCCEEDED.
- `KnowledgeAssetPublicationService.publish` resolves an existing APPLIED
  publication idempotently.
- `KnowledgeSpaceService.requireUploadTarget` rechecks
  `knowledge_space can_create_asset`.
- The completed Document View/Delete verdict forbids pre-publication deletion,
  creator visibility as delete authority, and an unfenced CANCELLED enum.
- The unique `(source_revision_id, job_type)` constraint forbids creating a
  second ingestion job for the same revision.

## Comparable-System Evidence

| System | Behavior | File evidence |
| --- | --- | --- |
| Onyx `618b503` | User-file UI models uploading, processing, failed, deleting, and completed states distinctly; deletion is a separate API action. | `tmp/onyx/web/src/sections/modals/UserFilesModal.tsx`, `sections/cards/FileCard.tsx`, `lib/projects/svc.ts` |
| LightRAG `9a45b64` | Graph UI keeps mutation actions in selected-node context; it does not define OrgMemory's source publication/authorization contract. | `tmp/upstream-lightrag-v1.5.4/lightrag_webui/src/components/graph/PropertiesView.tsx` |

## Questions To Attack

1. Can FAILED coexist with an APPLIED publication, and will the exact retry path
   converge to READY without duplicating asset/version/chunks/tuples?
2. Is original-uploader plus current `can_create_asset` the correct authority
   for retry, or is another permission/aggregate required?
3. Which rows must be pessimistically locked so two retry requests and a worker
   claim cannot interleave incorrectly?
4. Can a stale worker still act after FAILED is visible, given worker id reuse?
5. Should retry reset attempt count, and how is a new bounded cycle distinguished
   from an infinite retry loop?
6. Which denial paths must be opaque and which failure detail is safe to expose
   to an already-visible source actor?
7. If the proposal is unsafe, what is the smallest honest UI remediation that
   can ship without backend retry?

## Required Verdict

Return `accept`, `accept with changes`, or `reject`; give the strongest
contradiction, must-fix list with repository paths, committed recommendation,
mandatory tests, and forbidden scope. Do not edit any file.

