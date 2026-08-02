# Challenge Verdict - Document View And Delete

Date: 2026-08-02  
Base commit reviewed: `39281c33f8dfd551fc96df1e7eac520dc5c58e1a`  
Verdict: **accept with changes**

The independent reviewer remained read-only and accepted Source Object ids as
the browser command identity. The original proposal was narrowed because it did
not yet fence a worker that had already crossed into split publication.

## Strongest contradiction

Setting an ingestion job to cancelled before retiring the source is not a
publication fence. `SourceIngestionProcessor` calls
`KnowledgeAssetPublicationService.publish` before
`SourceIngestionCoordinator.complete` rechecks job ownership. Publication then
uses independent transactions to prepare an asset/chunks/outbox, write tuples,
activate the version/chunks, and advance the source head. A delete racing in
that interval could therefore be followed by successful publication.

## Committed recommendation

Keep one opaque source-action API and Source Object ids in the UI, while
preserving Knowledge Asset as the published authorization aggregate.

This increment ships metadata View for every visible row, protected content for
the exact current READY revision, and Delete only for fully published READY
manual uploads. A parent application service coordinates Source and Asset owner
APIs; Source Ledger must not mutate Asset repositories directly. Pending,
processing, failed, quarantined, and connector-owned rows explain why Delete is
unavailable.

Pre-publication cancellation becomes a separate increment requiring a
monotonic claim token, terminal cancellation, an aborted-publication contract,
publication/retirement serialization, and race tests. A `CANCELLED` enum without
those mechanics is explicitly rejected.

## Must-fix items accepted

1. Content authorization is source-specific and canonical. It resolves source,
   current revision, active asset/version, OpenFGA `can_view`, current source
   ACL, classification, tenant, lifecycle, publication model, and current head.
   Creator-list visibility and browser flags grant nothing.
2. READY deletion rechecks `knowledge_asset can_delete`; `can_create_asset` is
   never substituted. `canView` and `canDelete` remain separate actions.
3. Denied, missing, cross-tenant, archived, and concurrently changed resources
   use one opaque not-found contract and reveal no title, identifier, or count.
4. Security comes first from source/asset tombstones and retrieval filters.
   The final design rejects physical projection and tuple cleanup from this UI
   command: canonical active-state joins immediately suppress retired evidence,
   while physical erasure remains owned by retention policy.
5. Preview uses an exact representation allowlist: escaped Markdown/plain text,
   PNG, JPEG, GIF, WebP, and sandboxed PDF with download fallback. DOCX, PPTX,
   HTML, SVG, XML, JSON, unknown, and provider-rich content stay attachment-only.
6. Preserve `no-store`, `nosniff`, safe filename disposition, blob-integrity
   checks, allow/deny audit, and browser object-URL revocation.

## Mandatory gates

- Characterize source-list identity, citation headers/opaque denial, READY asset
  retirement, and split publication ordering before implementation.
- Cover owner, unrelated contributor, revoked owner, Space ACL administrator,
  cross-tenant actor, and connector source authorization.
- Prove permission revocation after the detail response, unavailable OpenFGA,
  incomplete/unsealed ACL, lifecycle mismatch, and integrity failure all fail
  closed.
- Prove duplicate READY deletes are idempotent and leave no active
  source/asset/version or retrievable chunk/citation/graph contribution.
- Test exact preview types and hostile renamed HTML/SVG/XML/JSON fixtures, plus
  PDF sandbox/download fallback and Office attachment-only behavior.
- Run focused core/API tests, terminating clean test, OpenAPI/Hey API drift,
  web unit/typecheck/build, real-browser flows, docs checks, and diff checks.

## Forbidden scope

Connector deletion, physical evidence/blob purge, retention changes, bulk
deletion, reindex, Office/HTML/SVG/rich rendering, chunk ids as document ids,
or weaker authorization based on creator-list visibility, `can_create_asset`,
or served frontend flags.

## Final owner choice after challenge

The cleanup-outbox recommendation is not adopted in this increment. The
repository already treats Knowledge Asset/version and Source Object tombstones
as the serving boundary: secure retrieval requires a non-archived asset, an
ACTIVE version, and an ACTIVE source, while graph exploration intersects its
evidence with the same canonical active scope. Adding a second destructive
meaning to the Documents button would conflate governed retirement with
retention erasure and expand the schema/operations scope without improving the
immediate authorization guarantee. The rejected alternative is a button-driven
physical cleanup job.
