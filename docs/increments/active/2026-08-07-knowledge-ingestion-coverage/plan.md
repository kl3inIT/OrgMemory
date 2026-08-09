# Knowledge Ingestion Coverage Plan

## Measure first

- [x] Probed what Tika exposes in XHTML mode. XLSX, DOCX and HTML carry real
  `<table>`/`<tr>`/`<td>` plus headings; CSV is flat text under every
  configuration tried. Recorded in `design.md`. Item 1 stands, with CSV split
  out into its own reader.

## 1. Typed block IR

- [x] Parse through Tika with an XHTML content handler and build
  `HEADING` / `TABLE` / `PARAGRAPH` blocks with correct char spans, so
  `CanonicalDocument.hasStructuredBlocks()` is true for single-document sources.
- [x] Bump the parser component version, because `parser.actual` is part of the
  canonical profile that `profileSha256` is computed over. Changing what the
  parser emits while keeping its identity would let two different outputs share
  one hash. Now `spring-ai-document-reader` `2.1.0`.
- [x] Make normalization block-kind aware: prose keeps the current collapse, a
  `TABLE` block keeps its cell and row boundaries.
- [x] Take the header row from `<th>` when present, otherwise the table's first
  row, since XLSX and HTML disagree. Recorded as the block's `header` attribute.
- [x] Keep `PagePdfDocumentReader` page provenance intact; a PDF must not lose
  `startPage`/`endPage` when it gains block kinds.
- [ ] Confirm against a genuine Word export whether heading styles surface as
  `<h1>`; the synthetic probe fixture could not answer this. Deferred to item 3
  browser verification, where a real file is available. Low risk: if Tika does
  not map them, a Word heading stays a paragraph, which is today's behaviour.
- [x] Cover: a spreadsheet becomes `TABLE` blocks with the header row intact, an
  HTML export becomes headings plus tables, a DOCX table stops being flattened,
  a plain text file stays one `PARAGRAPH`, and a multi-page PDF keeps its page
  range.
- [x] Prove end to end that a parsed table now reaches `ParagraphSemanticChunker`
  and comes back as fragments that each repeat the header row, never split a
  row, and lose no row.

## 2. Versioned structured-block processing policy

- [x] Complete the independent architecture challenge on per-content-type
  chunker resolution against the profile hash as an idempotency input; record
  proposal, strongest counterargument, evidence, choice and rejected
  alternative. It must also settle what happens to DOCX revisions already
  ingested flat, since item 1 changes how they would parse. Verdict: reject
  whole-document content-type resolution; adopt `structured-block-v1`. Existing
  READY revisions stay immutable and rebuild is opt-in/new-identity only.
- [x] Bind a complete named/versioned requested policy snapshot atomically
  before the first processing side effect, return it on every claim, and never
  re-read live defaults during retry.
- [x] Execute `structured-block-v1` as the production default, dispatching from
  canonical block kind through the existing paragraph-semantic composite while
  recording the versioned dispatch table and invoked sub-algorithm identities.
- [x] Persist the resolved execution snapshot and chunk-manifest hash before
  downstream durable publication; extend profile hashing and READY database
  constraints accordingly.
- [x] Preserve semantic-vector's explicit requested/actual recursive fallback,
  and allow operator override only through a named immutable policy.
- [x] Cover mixed DOCX, plain TXT/PDF parser behavior, policy/profile hash
  sensitivity, retry across configuration change, pinned fallback, unavailable
  components, and READY constraints. HTML remains in item 3 because its upload
  allowlist is not open yet.
- [x] Add a V29-to-V30 migration fixture containing an existing READY revision
  to prove the migration validates it without changing its processing identity.
- [x] Lock the job table and fail the V30 cutover while legacy PENDING or
  PROCESSING jobs exist; the rollout must stop and drain the old worker rather
  than pin those jobs from new deployment defaults.

## 3. Format allowlist

- [ ] Move all five gates together for CSV, XLSX and XLS: servlet multipart
  limit, `KnowledgeContentType`, `SpringAiDocumentParser.ALLOWED_MEDIA_TYPES`,
  `DocumentProcessingEngine.SPRING_AI_READER_SUFFIXES`, and the browser
  `accept` list.
- [ ] Read CSV with a dedicated reader rather than Tika, handling delimiter
  sniffing, quoted newlines and BOM, emitting the same `TABLE` blocks. It lands
  here rather than in item 1 because Tika detects CSV as `text/plain`, so the
  reader is only reachable once the allowlist admits the extension.
- [ ] Add HTML/HTM with boilerplate removal, so navigation, script and style
  content never becomes evidence.
- [ ] Add DOC, PPT, RTF and ODT (with ODS/ODP decided by then).
- [ ] Refuse archives explicitly by declared content type, never by container
  sniffing, and cover that DOCX/XLSX/PPTX still ingest.
- [ ] Cover: each new format ingests end to end, and a format absent from any
  one gate is proven to fail closed rather than half-succeed.

## 4. Limits per format

- [ ] Replace the single `maximum-chunks` and single upload size with
  per-format limits; a 25 MB CSV and a 25 MB PDF are not the same load.
- [ ] Cover: a spreadsheet larger than its limit is rejected with an actionable
  message rather than a generic chunk-limit failure.

## Consolidation

- [ ] Reconcile the Knowledge domain spec and its test matrix, refresh
  `Source:`/`Reconciled:`, and record the ingestion-coverage decision with its
  rejected alternative.
- [ ] Run `:core:test`, `:components:graph-rag-core:test`, `:apps:worker:test`,
  `:apps:api:test`, web lint, typecheck, unit tests, production build, and
  browser verification of uploading one spreadsheet and one HTML export.

## Sequencing note

Each item is unusable without its predecessor. Opening the allowlist first would
admit a spreadsheet and cut it into headerless 800-token fragments, because
`ParagraphSemanticChunker` — which already splits tables row-wise and repeats
the header into every fragment — cannot run until the parser produces `TABLE`
blocks and a strategy selects it.

The measurement at the top was not ceremony. It confirmed the mechanism for
three formats, removed CSV from it entirely, and revealed that DOCX — already
allowed — is losing its tables today.
