# Google Drive Binary Ingestion Design

**Status:** accepted after independent architecture challenge; implementation not
started

## Problem

The Google Drive connector currently indexes Google Docs, Sheets, and Slides by
exporting them to text, plus files that are already textual. Its Drive query
excludes PDF, DOCX, XLSX, PPTX, images, archives, and every other binary format.
A Tasco pilot Drive can therefore crawl successfully while its PDF reports and
Word procedures never become source objects, revisions, chunks, citations, or
retrieval evidence.

Manual upload does not close this gap. Upload already sends binary bytes through
the canonical durable source-ingestion pipeline, but it loses Drive sync,
revision identity, deletion, and source ACL reconciliation. The connector must
share the immutable evidence, durable job, parser, idempotency, and publication
machinery without inheriting upload-owned registration or ACL behavior.

## Product Promise At Stake

OrgMemory is a governed organizational memory layer: source evidence should be
retrievable with its provenance and source permissions intact. A connector that
silently defines common business documents out of existence violates that
promise even when its crawl and permission checkpoints are internally correct.
The pilot gate is not “Drive API calls succeed”; it is “the documents Tasco
actually uses can be answered from, cited, updated, retired, and revoked.”

## Current Evidence

- `GoogleDriveDocumentTypes` accepts only Google-native Docs/Sheets/Slides and
  textual MIME families; `queryClause()` excludes every other file before the
  crawl sees it.
- `GoogleDriveApiClient.downloadText()` reads a complete response into a
  `String`, and `ConnectorContentItem` carries only a text body. The generic
  connector path has no binary observation or bounded streaming contract.
- `ConnectorReconciler` already stores connector text as immutable evidence and
  stages a `SourceRevision`, then normalizes, chunks, embeds, and publishes it
  inline. The missing binary capability is durable parser-job handoff, not all
  evidence staging.
- Manual upload stores an `EvidenceBlob`, stages a `SourceRevision`, enqueues a
  durable `SourceIngestionJob`, and lets the worker parse through
  `DocumentParser`. The worker then registers an upload raw source and builds an
  upload ACL; those two steps cannot be reused by a source-ACL connector.
- `RawSourceObject` requires non-null text and hashes that text. A connector
  binary needs a raw-source payload contract before parse rather than fake text.
- `SourceRevision.rawSourceObjectId` is populated only when processing becomes
  ready. A durable connector job needs an earlier explicit association.
- Parser routing derives a suffix from the filename. Drive does not require file
  extensions, so MIME admission alone is insufficient unless the system also
  synthesizes a canonical parser filename.
- `KnowledgeContentType` and `SpringAiDocumentParser` already admit PDF and
  OOXML Office for uploads. Images are storable/previewable but are not admitted
  for text indexing because OCR/multimodal extraction is not a complete product
  path.
- The shipped Drive hardening bounds retained text and continues permission
  observation after content admission closes, but intentionally does not add
  binary ingestion.

## Reference Comparison

Pinned Onyx revision `618b5031bf21463f44e3bed9eb9d5073b806fec0` demonstrates the
missing provider mechanics, not an architecture to copy wholesale.

| Capability | Pinned Onyx evidence | OrgMemory decision |
| --- | --- | --- |
| Separate Google-native export and stored-file download | `tmp/onyx/backend/onyx/connectors/google_drive/doc_conversion.py:233-249,283-324` | Keep MIME dispatch in the Drive adapter. |
| Per-file download threshold | `tmp/onyx/backend/onyx/configs/app_configs.py:1203-1223`; `doc_conversion.py:304-318,760-773` | Enforce declared-size and streamed-byte limits; never trust metadata alone. |
| PDF and Office extraction | `tmp/onyx/backend/onyx/connectors/google_drive/doc_conversion.py:327-510` | Reuse OrgMemory `DocumentParser`; do not parse inside the connector. |
| Bounded conversion batches | `tmp/onyx/backend/onyx/connectors/google_drive/connector.py:114-121,1725-1888` | Avoid resident converted batches: stage one bounded stream before parsing. |
| Source metadata and link | `tmp/onyx/backend/onyx/connectors/google_drive/doc_conversion.py:841-913` | Preserve canonical `webViewLink`, filename, MIME, modified time, and checksums. |
| Extensionless stored files | `tmp/onyx/backend/onyx/connectors/google_drive/doc_conversion.py:451-460` | Synthesize a canonical suffix from admitted MIME; Tika still verifies content. |
| Images embedded in PDF | `tmp/onyx/backend/onyx/connectors/google_drive/doc_conversion.py:479-497` | Defer image/OCR extraction; text-bearing PDF only for this increment. |
| Skipped download or extraction can disappear with a warning | `tmp/onyx/backend/onyx/connectors/google_drive/doc_conversion.py:500-510,633-705` | Reject this behavior: a failed supported file remains observable and retryable while ACL reconciliation continues. |

## Challenged Decision

> Connector adapters MUST separate metadata/ACL enumeration from content
> transfer. Binary files are streamed under configured caps into the canonical
> `EvidenceBlob`/`SourceRevision` ledger, then the existing durable
> `SourceIngestionJob` and `DocumentParser` own parse, chunk, and index. Adapters
> MUST NOT parse binaries or retain file bodies in `ConnectorContentItem` or
> crawl-batch memory. Permission reconciliation continues when binary admission,
> download, or parsing fails.

The independent verdict was **REVISE**, then adopt after seven must-fixes. The
[verdict](challenge-verdict.md) is binding on the details below.

## Accepted Architecture

### 1. Separate observation, transfer, and publication

The generic connector content contract becomes a sealed payload:

- inline text for existing Slack, GitHub, and Google-native/text Drive paths;
- a binary source observation containing provider object identity, retrieval
  locator, source URI, filename, declared MIME and size, modified time,
  `sha256Checksum` when available, and bounded provenance/change hints.

The observation carries metadata, never bytes, credentials, callbacks,
`InputStream`, or temporary paths. Existing text callers migrate in one cutover;
there is no compatibility alias.

Enumeration completeness and content-capture completeness become explicit
separate facts. Scope filters, truncation, Drive `incompleteSearch`, unread
metadata, and unread sharing withdraw the enumeration claim. A supported binary
transfer or parse failure does not: its permission item remains in the crawled
object set, prevents accidental retirement, and can still rotate the ACL. The
existing Google-native/text export path retains its current crawl-time failure
behavior for this pilot; moving it to per-item materialization is a follow-on.

### 2. Fetch through a registered source-specific port

Core owns `ConnectorContentFetcher` and a per-source registry equivalent to the
existing connector source/probe registries. Startup refuses an unclaimed source
or two implementations claiming one source.

The fetch call receives typed connection identity, provider object/revision
locator, a byte cap, and a caller-owned sink. The Google Drive implementation
resolves the current credential per call through `ConnectorConnectionDirectory`
and streams `files.get(..., alt=media)` into the sink. It closes the response
deterministically, aborts on the first byte beyond the cap, and never logs a
token or body. No credential or executable callback crosses the durable
boundary.

The first support matrix is PDF and DOCX. Drive MIME is authoritative for
admission. If the source name lacks the matching extension, the observation
provides a canonical `.pdf` or `.docx` parser filename while preserving the
actual display name. Tika/parser verification still quarantines MIME/content
mismatch. The policy is derived from `KnowledgeContentType`; Drive does not own
an independent parser allowlist.

### 3. Make binary raw evidence explicit

Binary registration is a distinct source-ledger command, not a null smuggled
through the text command. An API-owned Flyway migration:

- adds a required raw payload-kind discriminator (`TEXT` or `BINARY`);
- makes `raw_source_objects.raw_content` nullable only for `BINARY`;
- defines binary `payload_sha256` as the referenced evidence blob's SHA-256;
- adds/uses an immutable association sufficient to prove that blob identity;
- preserves non-null text and text hashing for every existing raw source.

A second persisted discriminator identifies `UPLOAD` and `CONNECTOR_BINARY`
ingestion jobs. A connector-binary job resumes the already registered raw
source and sealed source ACL through an early `SourceRevision.rawSourceObjectId`
binding. It never executes upload raw registration, hard-coded `UPLOAD` source
identity, or upload ACL synthesis. Normalization and promotion still occur after
parse in the worker.

The early binding is not a read grant. Secure retrieval uses the published
asset's raw source, current live source ACL head, current source revision, and a
published version. A pending revision has no retrieval surface.

### 4. Track the object-write crash window

A connector-binary transfer reservation is committed before object storage
receives bytes. It owns a deterministic staging key, organization/source/object
identity, provider observation key, byte cap, state, and timestamps. The flow is:

1. resolve the file's source ACL; unknown authority fails closed;
2. admit MIME and declared size against per-file and per-crawl raw-byte budgets;
3. reserve the transfer transactionally;
4. stream to `ObjectStoragePort`, enforcing the observed-byte cap and collecting
   storage SHA-256/length;
5. transactionally create/reuse `EvidenceBlob`, register the binary raw source
   and sealed ACL, stage/bind `SourceRevision`, enqueue the connector-binary job,
   and finalize the reservation;
6. let the worker parse, normalize, chunk, embed, publish, and mark ready.

A process death can leave a stale reservation and deterministic object, never an
untracked object. Age-gated recovery retries or deletes only unfinalized
reservations and must prove no `EvidenceBlob` reference before delete. Delete
failure is visible/retryable. It never sweeps arbitrary keys or a referenced
immutable blob.

### 5. Use content SHA-256 as canonical identity

Drive `sha256Checksum` is the preferred pre-download change signal and is
verified against the storage-computed SHA-256. `md5Checksum`, size, `version`,
`headRevisionId`, and modified time are provenance/change hints, not canonical
content identity. If Drive supplies no SHA-256, the system streams and computes
one rather than trusting MD5.

`SourceRevision` materialization identity remains `(sourceObject,
contentSha256)`. A Drive checksum mismatch is a stable integrity failure. A
retry for the same observation/reservation cannot duplicate the blob, source
revision, job, or publication.

### 6. Keep ACL convergence independent

Binary content state and permission capture state remain separate. Permission
observation and source-head rotation continue after raw-byte budget exhaustion,
a content transfer failure, an unchanged content digest, and a prior parse
failure. A newly staged revision is not retrievable before its source ACL is
sealed and publication succeeds. A later Drive revocation denies retrieval
without content re-download or reparse.

Only complete identity enumeration can retire unmentioned source objects.
Content failure for an observed supported file neither retires its last ready
revision nor claims verified empty content.

### 7. Preserve publication accountability deliberately

The connection's provisioning actor remains the accountable publication owner,
matching the shipped inline connector path. This tuple does not bypass the live
source ACL for evidence read, and connector sources remain excluded from
user-owned delete. A non-human connector-ownership model would change the
authorization model and is a separate decision; binary ingestion will not change
it implicitly.

### 8. Make failure visible and retryable

Stable states distinguish at least:

- unsupported format outside the advertised capability matrix;
- declared file too large;
- per-crawl raw-byte budget exhausted;
- streamed response exceeded its cap;
- provider checksum/observed SHA-256 mismatch;
- provider download refusal or retryable network failure;
- quarantined or failed document parsing;
- stale transfer reservation/recovery failure.

A supported file is never silently removed from the connector universe because
a transfer or parse failed. Permission completeness and content completeness
remain separately visible.

## Pilot Scope

### In scope

- PDF and DOCX stored in My Drive, shared folders, and shared drives visible to
  the configured service account or delegated Workspace user.
- Extensionless PDF/DOCX names and MIME/content mismatch handling.
- Canonical `webViewLink`, display filename, canonical parser filename, MIME,
  modified time, checksums, provider revision hints, and observed byte size.
- New file, unchanged file, changed bytes, delete/retire, permission-only change,
  source revocation, over-limit, transient download failure, parse failure,
  tracked transfer recovery, and worker-restart recovery.
- Existing manual upload and inline-text connector behavior preserved.

### Out of scope

- OCR, standalone image indexing, and embedded-image extraction.
- Password-protected/encrypted document recovery.
- Archives and recursive expansion.
- User OAuth consent; current service-account/domain-wide-delegation remains.
- Drive comments, labels, shortcuts, Google Forms/Drawings/Sites, and arbitrary
  native app types.
- Incremental `changes.list`, push notifications, and webhooks.
- Google Group membership enumeration beyond the existing source-principal
  model.
- A new connector ownership tuple or authorization model.

## Remaining Google Drive Gap Register

| Gap | Pilot impact | Disposition |
| --- | --- | --- |
| PDF/DOCX stored-file ingestion | Blocker: Tasco reports and procedures are absent | This increment. |
| XLSX/PPTX and legacy/OpenDocument/HTML/RTF/CSV/JSON/XML/Markdown coverage | Common but not required for the first binary proof | Follow the same pipeline after PDF/DOCX evidence. |
| Images and scanned PDFs | Text may remain empty without OCR | Separate OCR/multimodal increment with cost, privacy, and quality gates. |
| Canonical Drive link and source metadata | Citations currently use a synthetic connector URI | This increment for all Drive content paths. |
| Shortcut resolution | Shortcuts are excluded; target identity and ACL semantics are unresolved | Follow-on provider-contract decision. |
| Folder hierarchy/owners/created time | Limits browsing and provenance presentation | Add as bounded metadata after the retrieval blocker. |
| Drive comments and labels | Context loss, not corpus loss | Evidence-gated follow-on. |
| Google Forms/Drawings/Sites and unknown native types | Partial native-app coverage | Explicit capability matrix before each type. |
| `changes.list`/webhooks | Full listing cost and slower convergence at scale | Defer until pilot volumes show cadence pressure. |
| User OAuth consent | Admin/service-account onboarding is heavier | Separate authentication and connection-ownership decision. |
| Google Group membership truth | Group principals can be observed but membership may remain incomplete | Keep fail closed; SCIM/directory owns membership truth. |
| Rich preview | Browser delivery remains conservative | Separate untrusted-content/sanitization contract. |
| Google-native export failure stalls retirement | Existing text path withdraws crawl completeness on export failure | Accept for pilot; move exports to per-item materialization only with evidence. |

## Strongest Counterargument

“Reuse the canonical upload pipeline” is misleading because its raw-source,
worker, ACL, and suffix contracts are upload-shaped. A binary has no parsed text
at registration; the upload worker constructs organization ACLs; and an
extensionless Drive file will fail suffix routing. Unless those contracts split
explicitly, the new path is a third ingestion variant with a fail-open ACL risk.

The design accepts this counterargument. Reuse is limited to immutable evidence,
durable jobs, `DocumentParser`, processing profiles, source-revision
idempotency, and publication mechanics. Binary raw registration, source-sealed
ACL, job kind, MIME admission, suffix synthesis, and transfer reservation are
explicit contracts, not hidden conditionals.

A secondary alternative is synchronous parsing in the connector, as Onyx does.
That is locally smaller but duplicates parser/version behavior, retains
converted documents in crawl memory, couples permission cadence to parsing, and
cannot survive provider-download/process-death boundaries cleanly. It remains
rejected.

## Acceptance Gates

1. A real PDF and DOCX in the Tasco Drive become retrievable through the same
   citation and secure-search path as manually uploaded documents.
2. An extensionless PDF/DOCX parses; a MIME/content mismatch quarantines.
3. The citation opens the canonical Drive source link and identifies the actual
   filename and media type.
4. Editing either file creates one new content-SHA revision; retry and worker
   restart create no duplicate.
5. Revoking one user's Drive access denies retrieval without re-download or
   reparse; an allowed user still retrieves it.
6. Deleting the file retires it only after complete identity enumeration; a
   transfer/parse failure for another observed binary does not stall retirement.
7. Declared oversize, streamed oversize, raw-byte exhaustion, checksum mismatch,
   download failure, parse failure, and stale reservation have stable visible
   outcomes; none becomes silent success or verified empty content.
8. Permission capture continues after content admission or processing failure.
9. Crash recovery leaves no untracked object and never deletes referenced
   evidence.
10. Existing Slack, GitHub, Google-native/text Drive, and manual-upload focused
    tests stay green.
11. The terminating backend clean test and two-user live Workspace proof pass.

## Rejected Alternatives

- **Add PDF/DOCX to `GoogleDriveDocumentTypes` and decode through
  `downloadText()`.** Binary data is not text and would bypass the parser.
- **Parse in the Google Drive adapter.** Duplicates upload behavior and couples
  provider crawling to expensive parsing.
- **Put `byte[]` or `InputStream` in `ConnectorContentItem`.** Makes batches
  memory-bound and cannot cross a durable retry boundary.
- **Reuse upload registration/ACL branching unchanged.** Would conflict with or
  widen the source-sealed ACL.
- **Treat filename suffix as authoritative.** Drive permits extensionless and
  misleading names; declared MIME plus parser verification is required.
- **Tell Tasco to use native Google Docs or manual upload.** Removes Drive
  revision, retirement, and ACL semantics.
