# Document View And Delete

## Problem

The Documents table exposes ingestion state but has no row action to inspect or
retire a document. The existing contracts cannot safely be wired straight to
buttons:

- `SourceResponse.id` is a stable Source Object id, while
  `DELETE /api/knowledge-assets/{knowledgeAssetId}` requires a Knowledge Asset
  id;
- the protected content endpoint is citation-shaped and requires a chunk id,
  which the Documents table does not have;
- in-flight and failed uploads may not have a published Knowledge Asset yet;
- a title visible in the list does not by itself authorize content delivery or
  deletion.

## Proposal

Make Documents actions source-centric. Add a permission-aware source detail
contract that resolves the latest revision and, when publication has completed,
its stable Knowledge Asset. The response reports only actions the authenticated
actor may invoke. The server rechecks the same authorization on every action;
the browser's action flags are presentation hints, not authority.

`View` opens a responsive detail sheet. It shows governed metadata for every
visible source and streams original bytes only through a new protected
source-content endpoint. Reuse the closed `KnowledgeContentType` delivery
policy: PDF, plain text, Markdown-as-text, and supported images may render
inline; DOCX and PPTX remain download-only. Keep `Cache-Control: no-store`,
`nosniff`, safe `Content-Disposition`, integrity verification, and permission
audit behavior. Do not render provider HTML or arbitrary rich content.

`Delete` is a destructive action with an explicit confirmation naming the
document. In this increment it means governed retirement, not physical erasure.
The first slice enables it only for a fully published `READY` manual upload:

- archive the stable Knowledge Asset and its current version, retire the Source
  Object, make derived graph/search contributions unservable immediately, and
  invalidate caches;
- make retries idempotent by resolving the published version from the retained
  Source Ledger history and accepting an already-consistent retirement;
- keep pending, processing, failed, quarantined, and connector-owned rows
  non-deletable with an explicit explanation.

Physical graph rows, chunks, tuples, and evidence are not erased by this
command. Canonical active-state joins remove retired content from retrieval and
the graph explorer; retained data continues to follow organization policy.
Physical projection/tuple cleanup is a separate retention operation and is not
represented as part of this UI action.

Deleting pre-publication work is a separate follow-up. The worker currently
publishes through independent transactions before it rechecks job ownership, so
adding a `CANCELLED` enum alone would allow a stale worker to resurrect content.
That follow-up requires a claim-generation fence, an aborted-publication state,
serialization of retirement with publication prepare/complete, and race tests
at every split-transaction cut point.

After success the row disappears from the active list and the query is
invalidated. Authorization denial and concurrent lifecycle changes fail closed
without disclosing whether another asset exists.

## UI

Add one compact actions column to the existing Documents `DataTable`:

- `View` is the primary row action and also opens from the document title;
- `Delete` is in the row overflow menu and opens a destructive confirmation;
- non-READY work may still show metadata, but content and delete availability
  are explained;
- connector rows explain that deletion is managed from the connector;
- keyboard, mobile, loading, disabled, and error states use existing
  shadcn/Radix primitives and product tokens.

No `Reindex` action is included. Graph/index lifecycle controls stay separate
from document inspection and retirement.

## Reference evidence

Pinned Onyx separates document inspection from connector deletion: its document
explorer opens the source link, while connector deletion is scheduled as a
lifecycle job. OrgMemory should retain that separation but cannot depend on an
external source URL for uploaded evidence, so it needs its own protected content
endpoint and source-centric retirement command.

The existing Assistant citation preview proves the repository's safe delivery
allowlist and browser rendering pattern. This increment extracts a shared web
preview component but does not weaken citation authorization or reuse a chunk id
as a document identity.

## Strongest counterargument

Expose the existing Knowledge Asset delete endpoint only for READY rows and
reuse any chunk from the asset to open the citation preview. That is smaller,
but it leaves stuck/failed uploads undeletable, makes a chunk an accidental
document handle, and creates inconsistent behavior when an asset has no
retrievable chunk. The source-centric contract is the smaller durable boundary.

## Scope

- Source detail/action metadata, protected source content, and READY manual
  upload retirement.
- Documents View/Delete UI and focused API/core/web/browser tests.
- Current behavior and test-matrix consolidation after implementation.
- No pre-publication cancellation, reindex button, rich Office/HTML renderer,
  connector deletion, physical blob purge, retention-policy rewrite, or bulk
  actions.

## Architecture challenge

Status: **accepted with changes**. The review required a source-specific
canonical authorization use case, opaque non-disclosure, an exact preview
allowlist, and READY-only deletion unless publication fencing lands as its own
increment. Its proposed physical projection/tuple cleanup was rejected from
this user-facing command because canonical retirement already makes evidence
unservable and retention policy, not a button click, owns erasure. See
[challenge-verdict.md](challenge-verdict.md).
