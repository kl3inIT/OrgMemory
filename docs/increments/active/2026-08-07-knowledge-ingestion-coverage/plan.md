# Knowledge Ingestion Coverage Plan

## Measure first

- [ ] Probe what Tika actually exposes in XHTML mode for XLSX, CSV and HTML:
  does it emit `<table>`/`<tr>`/`<td>` and heading elements, or flat text?
  Record the finding in `design.md`. If it is flat, stop and re-plan item 1
  before writing any parser code.

## 1. Typed block IR

- [ ] Parse through Tika with an XHTML content handler and build
  `HEADING` / `TABLE` / `PARAGRAPH` blocks with correct char spans, so
  `CanonicalDocument.hasStructuredBlocks()` is true for single-document sources.
- [ ] Make normalization block-kind aware: prose keeps the current collapse, a
  `TABLE` block keeps its cell and row boundaries.
- [ ] Keep `PagePdfDocumentReader` page provenance intact; a PDF must not lose
  `startPage`/`endPage` when it gains block kinds.
- [ ] Cover: a spreadsheet becomes `TABLE` blocks with the header row intact, an
  HTML export becomes headings plus tables, a plain text file stays one
  `PARAGRAPH`, and a multi-page PDF keeps its page range.

## 2. Strategy resolution by content type

- [ ] Complete the independent architecture challenge on per-content-type
  chunker resolution against the profile hash as an idempotency input; record
  proposal, strongest counterargument, evidence, choice and rejected
  alternative.
- [ ] Resolve the chunker from content type instead of one global property,
  recording requested and actual in the existing profile fields.
- [ ] Cover: a spreadsheet resolves to `paragraph-semantic`, resolution is
  recorded in the canonical profile, and the existing semantic-vector failure
  fallback still works.

## 3. Format allowlist

- [ ] Move all five gates together for CSV, XLSX and XLS: servlet multipart
  limit, `KnowledgeContentType`, `SpringAiDocumentParser.ALLOWED_MEDIA_TYPES`,
  `DocumentProcessingEngine.SPRING_AI_READER_SUFFIXES`, and the browser
  `accept` list.
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

The measurement at the top is not ceremony. Item 1's mechanism depends on Tika
exposing table markup in XHTML mode, and that has not been verified.
