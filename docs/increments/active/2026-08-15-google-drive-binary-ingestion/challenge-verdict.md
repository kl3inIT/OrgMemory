# Google Drive Binary Ingestion Architecture Challenge Verdict

**Date:** 2026-08-15  
**OrgMemory commit reviewed:** `93a0bc5952d5ee8651cf4082f4b8788d2af21367`  
**Pinned Onyx commit reviewed:** `618b5031bf21463f44e3bed9eb9d5073b806fec0`  
**Reviewer:** independent Claude Fable 5 session launched through Orca CLI  
**Review mode:** read-only, repository- and reference-source-verified

## Verdict

**REVISE.** The proposed boundary is directionally correct: provider adapters
should not parse binary files, and binary Drive evidence should use the canonical
immutable evidence, durable source revision/job, parser, and publication
machinery. Implementation must not start from the original design because its
“reuse the upload pipeline” framing crossed text-bound and upload-owned
contracts that would fail at runtime or widen source ACLs.

After incorporating the seven must-fixes below, the reviewer committed to the
recommendation and judged the design safe to execute. No second challenge is
required unless the raw-source representation deviates from nullable binary
`raw_content` with `payload_sha256` equal to the evidence-blob SHA-256.

## Strongest Counterargument

The real objection is not that Onyx-style synchronous parsing is simpler. It is
that the proposal called this reuse while the current worker is upload-shaped:
`RawSourceObject` requires non-blank text, the worker registers the raw source
after parsing, hard-codes `UPLOAD`, synthesizes upload ACLs, and parser routing
uses filename suffixes. Blind reuse would create a third ingestion variant,
quarantine extensionless Drive files, or replace a source-sealed ACL with an
organization-wide upload grant.

The revised design therefore reuses only the durable job table, parser,
processing/profile machinery, source revision idempotency, and publication
pipeline. It explicitly splits registration ordering, raw-payload semantics,
ACL sourcing, job kind, and MIME admission.

## Verified Repository Findings

1. `GoogleDriveDocumentTypes.indexableTypeClause()` excludes PDF/DOCX before
   reconciliation; they are not merely failing at parse time.
2. `ConnectorReconciler.materializeRevision()` already stores evidence and
   stages a `SourceRevision`; the missing binary capability is the bounded
   provider transfer plus durable worker resume, not all staging.
3. `SourceRevision.rawSourceObjectId` is populated only by `ready()`. A durable
   connector job needs an earlier explicit binding.
4. Secure retrieval joins the live source ACL head, serves only the current
   revision, and requires a published asset version. A pending unpublished
   revision has no retrieval surface, while a later source revocation can deny
   immediately without reparse.
5. `ConnectorCrawlBatch.crawledObjectIds()` includes permission-only items, and
   pruning retires only unmentioned objects. A failed supported download can
   remain prune-protected while its observed ACL still rotates.
6. Current raw registration requires non-blank `rawContent`, stores it in a
   non-null text column, and hashes that text. Binary registration before parse
   needs an explicit schema and command contract.
7. `SourceIngestionProcessor` currently derives source system and ACL from
   upload claims. A connector-staged job must not execute that branch.
8. Parser routing is extension-based. Drive MIME must be authoritative and a
   canonical suffix must be synthesized for an extensionless stored filename;
   Tika still verifies the actual content.
9. No generic orphan-object sweep exists. The original design's claim of
   bounded orphan cleanup was unsupported.
10. The parser contract is `DocumentParser`, not `DocumentParserPort`.

## Required Revisions And Disposition

### 1. Binary raw-source contract

**Required:** make `raw_source_objects.raw_content` nullable for binary
registrations and define binary `payload_sha256` as the evidence-blob SHA-256.
Use an explicit payload-kind discriminator rather than infer binary state from a
null. Add a binary-specific registration command and idempotency comparison;
text registration remains unchanged.

**Disposition:** accepted in the revised design and Slice 2. This is an
API-owned Flyway migration in addition to early source-revision binding.

### 2. Explicit worker job kind

**Required:** persist an ingestion job kind. Connector-binary jobs resume the
already registered raw source and sealed ACL through the early revision binding;
they never call upload registration or upload ACL synthesis.

**Disposition:** accepted in the revised design and Slice 4. Retrieval safety is
preserved because retrieval uses the published asset's raw source and current
source ACL head, not the early revision field as a read grant.

### 3. MIME-authoritative admission and suffix synthesis

**Required:** use Drive's declared MIME to select the product content type,
synthesize `.pdf` or `.docx` when the source name has no matching suffix, then
let Tika/parser verification quarantine a mismatch.

**Disposition:** accepted in Slices 1 and 3, with extensionless and mismatch
contract tests.

### 4. Enumeration versus content completeness

**Required:** binary transfer/parse failure must not withdraw the complete
object-enumeration claim that authorizes pruning. Scope filters, truncation,
`incompleteSearch`, unread metadata, and unread sharing do withdraw it.
Supported binary failures become per-item content failures while the permission
item remains in the crawled object set.

**Disposition:** accepted in Slices 1, 3, and 5. The existing Google-native/text
export path keeps its current crawl-time failure behavior for this pilot; moving
all text export to per-item materialization is an explicit follow-on.

### 5. Tracked pre-write transfer reservation

**Required:** stop claiming cleanup exists. Either implement bounded orphan
reconciliation or explicitly accept a visible leak.

**Disposition:** implement it. A durable connector-binary transfer reservation
is committed before object storage receives bytes. It owns a deterministic
staging key and state. Finalization transactionally attaches the blob,
raw-source/ACL, revision, and job. An age-gated recovery path can delete or retry
only stale unfinalized reservations and can never sweep a referenced evidence
blob. This removes the untracked put-to-database crash window.

### 6. Content identity

**Required:** commit the exact idempotency rule and request Drive checksum
metadata.

**Disposition:** `sha256Checksum` is the preferred pre-download change signal
and is verified against the storage-computed SHA-256. `md5Checksum`, size,
`version`, `headRevisionId`, and modified time are provenance/change hints only;
when Drive supplies no SHA-256, the system streams and computes SHA-256 rather
than treating MD5 as canonical identity. `SourceRevision` idempotency remains
`(sourceObject, contentSha256)`. A checksum mismatch is a stable integrity
failure.

### 7. Connector publication owner

**Required:** decide whether the connection actor should own connector-produced
assets or split that tuple from publication.

**Disposition:** preserve the existing connector behavior deliberately for this
increment: the connection's provisioning actor is the accountable publication
owner. This tuple does not bypass the current source ACL head for evidence read,
and connector sources remain excluded from user-owned delete. A different
non-human ownership model is a separate authorization-model decision, not a
silent side effect of binary ingestion.

## Rejected Alternative

Parse stored Drive files synchronously in the Google adapter, as pinned Onyx
does. It is locally smaller but duplicates parser/version behavior, holds
converted documents in crawl memory, couples permission cadence to parser
latency, and turns process death or extraction failure into a silent missing
file. The canonical durable job boundary is retained after making its
upload-owned assumptions explicit.

## Scope Limits

This verdict covers PDF/DOCX stored-file transfer, raw-source/revision/job
persistence, parser handoff, current source ACL enforcement, pruning semantics,
and publication ownership. It does not approve XLSX/PPTX, OCR/images, shortcuts,
Drive comments/labels, user OAuth, Google Group membership expansion,
`changes.list`, webhooks, rich preview, or a new OpenFGA ownership model.
