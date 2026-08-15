# Google Drive Binary Ingestion Architecture Challenge Brief

## Reviewer Mandate

Attack this proposal; do not validate it. Verify every claim in the repository
at commit `93a0bc5952d5ee8651cf4082f4b8788d2af21367` and in the pinned Onyx
checkout at `618b5031bf21463f44e3bed9eb9d5073b806fec0`. Remain read-only. Read
`CLAUDE.md`, `docs/conventions.md`,
`docs/specs/domains/knowledge-ingestion.md`,
`docs/tests/domains/knowledge-ingestion.md`, the filenames under
`docs/decisions`, and this increment's `design.md`. Return plain Markdown with:

1. an explicit `ACCEPT`, `REVISE`, or `REJECT` verdict;
2. the strongest counterargument;
3. concrete failure scenarios with file-level repository evidence;
4. a must-fix list separated from deferrable scope;
5. a committed recommendation precise enough to update `design.md` and
   `plan.md`.

Do not edit files, enter plan mode, or propose implementation beyond what the
evidence supports.

## Product Context

OrgMemory is a governed organizational memory layer for enterprise AI work. It
must ingest real source evidence, preserve provenance and revision history, and
never serve evidence beyond the source ACL ceiling. The Tasco pilot uses PDF and
DOCX reports and procedures in Google Drive. Today those files are excluded
before materialization, so a successful Drive crawl can still produce a corpus
that cannot answer from the customer's actual documents. The product promise at
stake is not connector availability; it is cited, permission-correct,
revision-aware organizational memory.

## Exact Rule Under Review

> Connector adapters MUST separate metadata/ACL enumeration from content
> transfer. Binary files are streamed under configured caps into the canonical
> `EvidenceBlob`/`SourceRevision` ledger, then the existing durable
> `SourceIngestionJob` and `DocumentParser` own parse, chunk, and index.
> Adapters MUST NOT parse binaries or retain file bodies in
> `ConnectorContentItem` or crawl-batch memory. Permission reconciliation
> continues when binary admission, download, or parsing fails.

Current enforcement points and affected boundaries:

- `integrations/connectors/src/main/java/com/orgmemory/connectors/googledrive/GoogleDriveDocumentTypes.java`
- `integrations/connectors/src/main/java/com/orgmemory/connectors/googledrive/GoogleDriveApiClient.java`
- `integrations/connectors/src/main/java/com/orgmemory/connectors/googledrive/GoogleDriveConnectorBatchSource.java`
- `core/src/main/java/com/orgmemory/core/knowledge/connector/ConnectorContentItem.java`
- `core/src/main/java/com/orgmemory/core/knowledge/connector/ConnectorIngestionService.java`
- `core/src/main/java/com/orgmemory/core/knowledge/connector/ConnectorReconciler.java`
- `core/src/main/java/com/orgmemory/core/knowledge/sourceledger/KnowledgeIngestionService.java`
- `core/src/main/java/com/orgmemory/core/knowledge/sourceledger/StageSourceRevisionCommand.java`
- `core/src/main/java/com/orgmemory/core/knowledge/sourceledger/SourceRevision.java`
- `apps/worker/src/main/java/com/orgmemory/worker/ingestion/SourceIngestionProcessor.java`
- `components/graph-rag-core/src/main/java/com/orgmemory/graphrag/parsing/DocumentParser.java`
- `integrations/document-parsing-spring-ai/src/main/java/com/orgmemory/integrations/documentparsing/springai/SpringAiDocumentParser.java`

## Repository Facts To Challenge

1. Drive currently filters out binary files in its query, not merely at parse
   time. PDF/DOCX never enter connector reconciliation.
2. `ConnectorContentItem` is text-only and `ConnectorReconciler` performs an
   inline normalize/chunk/embed/publish path. It has no durable parse handoff.
3. Manual upload already owns immutable evidence storage, source-revision
   staging, a durable job, parser invocation, and publication.
4. `SourceRevision.rawSourceObjectId` is currently populated only in `ready()`,
   after parse and publication. Reusing connector-sealed ACL before parse likely
   requires an early binding or an equally explicit alternative.
5. Source ACL capture is a hard ceiling and permission-only reconciliation is
   separately versioned. Binary failure must not erase a known ACL, and content
   admission limits must not stop permission observation.
6. The recently shipped Drive hardening keeps one complete text response in
   memory, bounds aggregate retained UTF-8 text, and marks content incomplete
   while continuing permission capture. Raw binary bytes need a separate
   resource budget.
7. The parser can already handle PDF and OOXML formats for uploads. A
   connector-local parser would establish a second behavior and versioning
   convention.

## Comparable-System Evidence

| Question | Pinned source evidence | Observed behavior |
| --- | --- | --- |
| How are native and stored files retrieved? | `tmp/onyx/backend/onyx/connectors/google_drive/doc_conversion.py:233-249,283-324` | Native docs use Drive export; stored files use media download. |
| How is per-file size bounded? | `tmp/onyx/backend/onyx/configs/app_configs.py:1203-1223`; `tmp/onyx/backend/onyx/connectors/google_drive/doc_conversion.py:304-318,760-773` | Metadata size is checked against a default 10 MiB threshold. |
| Where does parsing happen? | `tmp/onyx/backend/onyx/connectors/google_drive/doc_conversion.py:327-510` | The provider connector directly dispatches PDF, Office, images, and text extraction. |
| How is resident work bounded? | `tmp/onyx/backend/onyx/connectors/google_drive/connector.py:114-121,1725-1888` | Up to 50 converted files form a sub-batch; comments estimate roughly 500 MiB resident documents. |
| Is source metadata preserved? | `tmp/onyx/backend/onyx/connectors/google_drive/doc_conversion.py:841-913` | Link, filename/MIME, folder metadata, owners, and modified time are attached to the document. |
| What happens when extraction fails? | `tmp/onyx/backend/onyx/connectors/google_drive/doc_conversion.py:500-510,633-705` | Several failures are warned and skipped/returned as no sections. |

## Motivating Operational Cost

This is an observed pilot-readiness defect, not a production incident. The
Tasco demo corpus contains PDF reports and DOCX procedures in Drive. The current
connector may report successful enumeration and permission progress while those
files are absent from source inventory and retrieval. Operators then have no
failure to fix, and the customer must either convert files to Google-native
formats or manually upload copies. Both workarounds destroy the sync,
revision/deletion, and source-ACL properties that justify the connector.

## Suspected Contradictions The Reviewer Must Test

1. Early registration of a raw source may pin an ACL snapshot that becomes stale
   while async parsing is pending; verify whether live source-head authorization
   actually prevents stale publication or retrieval.
2. The proposed generic source-specific fetch port may invert module ownership
   incorrectly or keep a provider credential/callback alive across the wrong
   boundary; identify the least coupled executable placement.
3. Staging binary bytes before the transaction commits can create unbounded
   orphan objects; determine whether current evidence identity and cleanup
   contracts are sufficient or must be expanded before this design is safe.
4. A complete crawl that observes a supported file but cannot download it must
   not retire the last ready revision, yet must still converge a revocation.
   Verify that the current omission/reconciliation contract can express both.
5. Using provider `modifiedTime` as the sole content revision may miss byte
   identity anomalies or duplicate work; determine the exact idempotency key
   needed from Drive metadata plus stored SHA-256.
6. Reusing the upload pipeline may smuggle upload-owned classification,
   actor/uploader, and ACL assumptions into connector ingestion. Name every
   invariant that must be split rather than reused blindly.
