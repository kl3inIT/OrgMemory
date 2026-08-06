# Knowledge Ingestion Coverage

## Intent

Accept the document formats an organization actually holds, and make the
ingested result answerable. A spreadsheet that arrives as an unlabelled run of
numbers is worse than a rejected upload, because the rejection is honest.

Today Knowledge Base upload accepts five formats. The formats a company keeps
its policies, headcount and salary data in — spreadsheets and wiki exports — are
refused at the door.

## Observed problem

### Five formats, refused at four separate gates

`KnowledgeContentType` marks `uploadAllowed=true` for `PDF`, `WORD(docx)`,
`POWERPOINT(pptx)`, `MARKDOWN(md)`, `TEXT(txt)` only. The same five appear as
`SpringAiDocumentParser.ALLOWED_MEDIA_TYPES:31-36`, again as
`DocumentProcessingEngine.SPRING_AI_READER_SUFFIXES:41`, and a fifth time in the
browser at `source-upload-dialog.tsx` `accept=".pdf,.docx,.pptx,.txt,.md"`.

A format missing from any one of them fails at a different layer with a
different error, so they must move together.

### The parser emits no structure

`SpringAiDocumentParser.canonical()` constructs every block as
`DocumentBlockKind.PARAGRAPH` (`SpringAiDocumentParser.java:113`), with
`headingLevel = null` and empty attributes — one block per PDF page, or a single
block for all Tika output.

Repository-wide, `DocumentBlockKind.TABLE` has **two consumers and no
producer**: both reads are inside `ParagraphSemanticChunker`, plus one test
fixture. `HEADING` is produced only by `LightRagSidecarDecoder`, in a module
(`:integrations:graph-rag-sidecar-json`) that `settings.gradle.kts:33` includes
but no application depends on.

### Normalization destroys the signal structure would be built from

`SpringAiDocumentParser.normalize():144` collapses `[\t\x0B\f]+` to one space
and runs of two or more spaces to one, across the whole document. Tika emits
spreadsheet and HTML cells tab-separated. The column boundary is erased before
any block IR could observe it.

### One global strategy

`orgmemory.ingestion.processing.chunker-id` defaults to `fixed-token`
(`application.yml:103`) and applies to every document. The only deviation is a
failure fallback from `semantic-vector` to `recursive-character`. Nothing
selects a strategy by content type.

### The consequence: the capability exists and is unreachable

`ParagraphSemanticChunker` is 557 lines carrying exactly the behaviour a
spreadsheet needs — row-wise table splitting that **re-prepends the header line
to every fragment**, and a `TableRole` that forbids merging a table interior
into surrounding prose. It has never run in production. Two independent reasons:
`hasStructuredBlocks()` is false for any single-block document, which is all
Tika output, and no block is ever `TABLE`.

Widening the allowlist first would therefore let a spreadsheet in and cut it
into 800-token fragments of headerless numbers. The order of work is the design.

## What is already inherited from LightRAG

All four chunkers carry version `lightrag-v1.5.4` and are ports of LightRAG's
four strategies:

| OrgMemory | LightRAG selector |
| --- | --- |
| `fixed-token` | `F` |
| `recursive-character` | `R` |
| `semantic-vector` | `V` |
| `paragraph-semantic` | `P` |

The strategy layer is ported. LightRAG's **parser layer** — the typed block IR
and sidecar its strategies consume — is not. That asymmetry is the whole of this
increment.

Two things OrgMemory already does better and must keep: the processing profile
is fully resolved, canonicalized and SHA-256 hashed onto the revision
(`ResolvedDocumentProcessingProfile`), and a DB check constraint refuses a
`READY` revision missing parser, chunker, pipeline or embedding identity.
LightRAG has no equivalent.

## What Spring AI already provides

`spring-ai-tika-document-reader` transitively supplies
`tika-parsers-standard-package`, already resolved: Microsoft (doc/xls/ppt/msg),
miscoffice, HTML, XML, text, mail, pkg, image, OCR-module wrappers, plus POI,
PDFBox and jsoup. Spreadsheet, CSV, HTML and legacy-Office text extraction needs
**no new dependency**.

Paid for and unused: `ParagraphPdfDocumentReader` (bookmark/TOC driven, a real
source of headings) ships in the already-declared PDF reader jar, and
`JsonReader`/`TextReader` live in `spring-ai-commons`.

## Decision

Work in this order, because each step is unusable without the one before it:

1. **Typed block IR.** Parse through Tika in XHTML mode and build
   `HEADING` / `TABLE` / `PARAGRAPH` blocks from the markup. Make normalization
   block-kind aware instead of global, so a table's cell boundaries survive.
   This alone activates `ParagraphSemanticChunker`.
2. **Strategy resolution by content type.** Replace the single global
   `chunker-id` with a resolved policy, recorded in the existing
   requested-versus-actual profile fields.
3. **Format allowlist.** Only now, and across all five gates together.
4. **Limits per format.** One `maximum-chunks` and one upload size cannot serve
   both a 25 MB PDF and a 25 MB CSV.

## Format scope

In scope, organization formats:

| Format | What it is in an organization | Structure sought |
| --- | --- | --- |
| CSV | Export from HRM, accounting, CRM | Table |
| XLSX | Salary bands, headcount, reports | Table per sheet |
| XLS | Excel 97-2003 | Table per sheet |
| HTML / HTM | Confluence, SharePoint, wiki export | Headings and tables |
| DOC | Word 97-2003 | As DOCX |
| PPT | PowerPoint 97-2003 | As PPTX |
| RTF | Interchange from older systems | Weak; near plain text |
| ODT / ODS / ODP | LibreOffice / OpenOffice | As their OOXML peers |

Five formats become fifteen.

Deferred to their own decisions, not silently dropped:

- **MSG / EML.** Tika reads them, but Tika also recurses into attachments. That
  is a permission-boundary question about who owns an attached file and who may
  see it — the same question the composer-attachment backlog entry raises. It
  needs that challenge, not a parser change.
- **JSON / XML / YAML.** Valuable as system exports or API contracts, worthless
  as stray configuration files. The distinction is a product decision.
- **Code formats.** `py`, `java`, `js`, `ts`, `go`, `sql`, `sh`, `log`, `conf`
  and the rest. LightRAG lists them but only reads them as plain text. Low value
  unless engineers become a primary audience.

Refused deliberately:

- **Archives (zip, tar, 7z).** Tika recurses into containers by default. An
  archive that slipped through the allowlist would carry uncontrolled content
  and a decompression-bomb surface into ingestion. Note that DOCX, XLSX and PPTX
  *are* zip containers, so the refusal must key on the declared content type,
  never on container sniffing.
- **Images.** Coupled to the separate wire-or-retire decision on the multimodal
  pipeline.
- **Scanned PDF.** Needs an OCR service. Neither OrgMemory nor LightRAG performs
  OCR in-process; LightRAG delegates to Docling or MinerU.

## Binding constraints

1. **The five gates move together.** Servlet multipart limit,
   `KnowledgeContentType`, `ALLOWED_MEDIA_TYPES`, `SPRING_AI_READER_SUFFIXES`,
   and the browser `accept` list. A format present in four of five fails
   somewhere confusing.
2. **Normalization becomes block-kind aware.** The current global tab collapse
   must not run over a `TABLE` block. Whitespace policy for prose is not
   whitespace policy for a cell.
3. **Archives are refused by declared type.** Never by asking Tika what is
   inside, because the three formats already supported are themselves zips.
4. **The profile hash is an idempotency input.** `profileSha256` is computed
   over the resolved processing profile and persisted with the revision.
   Changing how a chunker is resolved changes what that hash is a function of.
   This is why item 2 requires an independent challenge before implementation.
5. **Do not copy LightRAG's filename directives.** `report.[native-iteP].docx`
   puts processing policy in a filename a user types. OrgMemory has a governed
   administrative surface; policy belongs there.
6. **Do not copy LightRAG's nominal suffix list.** Of its 38 extensions,
   `rtf`, `odt`, `tex` and `epub` have no dedicated extractor and fall through a
   plain-UTF-8 path; ODT and EPUB are zip binaries and fail there. A supported
   format means a parser that produces usable structure, not a list entry.

## Required architecture challenge

Before item 2 is implemented: **per-content-type chunker resolution versus one
global chunker**, given that the resolved profile's SHA-256 is persisted as an
idempotency input and that a revision's `chunker_version` is a check-constrained
column. The challenge must state what re-ingesting the same bytes under a
changed policy is expected to do, and whether the policy itself belongs in the
profile hash.

## Open questions

- **Unverified assumption.** The whole of item 1 rests on Tika's XHTML mode
  exposing real `<table>` and heading markup for XLSX, CSV and HTML rather than
  the flat text the current `BodyContentHandler` path returns. This has not been
  measured. If it is false, item 1 needs a different mechanism and every
  downstream estimate changes. Measure before committing to the approach.
- Whether ODS and ODP earn inclusion, or whether ODT alone covers the real
  LibreOffice usage.
- Whether `ParagraphPdfDocumentReader` should replace or supplement
  `PagePdfDocumentReader`, given it can supply headings that the page reader
  cannot.
