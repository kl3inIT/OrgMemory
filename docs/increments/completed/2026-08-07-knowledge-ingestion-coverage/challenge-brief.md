# Architecture Challenge: Ingestion Chunker Resolution

## Reviewer mandate

Act as an adversarial, read-only architecture reviewer. Your job is to attack
the proposal below, not to validate it. Verify every material claim in the code
itself. Do not edit files, run migrations, change configuration, or implement a
solution.

Read `AGENTS.md`, `CLAUDE.md`, `docs/conventions.md`,
`docs/specs/domains/knowledge-ingestion.md`,
`docs/tests/domains/knowledge-ingestion.md`, decision filenames under
`docs/decisions/`, this increment's `design.md` and `plan.md`, and every source
path cited below.

Return a structured Markdown verdict with:

1. `ACCEPT`, `ACCEPT WITH BINDING CORRECTIONS`, or `REJECT`;
2. one committed architecture, including the selection key and ownership;
3. the strongest counterargument and why it does or does not defeat the choice;
4. exact processing-profile and retry/re-ingestion semantics;
5. must-fix implementation and test gates;
6. repository evidence for every claim;
7. the rejected alternative and the conditions that would reopen it.

## Product promise at stake

OrgMemory is a governed organizational memory layer. Uploaded evidence must be
answerable without silently changing its meaning: table rows must retain their
headers and boundaries, prose must retain useful semantic continuity, and the
exact parser/chunker behavior used for an immutable source revision must remain
auditable and identity-safe. A plausible answer produced from structurally
damaged evidence is worse than a rejected upload.

## Exact proposal under review

The active design currently says:

> Replace the single global `chunker-id` with a resolved policy selected by
> content type, and record requested and actual chunkers in the existing
> immutable processing profile.

This is not yet approved. The reviewer must choose among, or replace, these
concrete architectures:

- **A. One global `paragraph-semantic` default.** Every parsed document uses P;
  its internal block handling owns the difference between prose and tables.
- **B. Whole-document resolution by content type.** A deterministic policy maps
  suffix/media type to one chunker before execution.
- **C. Structured-block dispatch.** One document can route `TABLE` blocks to
  table-aware logic and prose/heading blocks to text-aware logic, analogous to
  Onyx's per-section dispatch, without pretending an XLSX, DOCX, or HTML file is
  structurally homogeneous.
- **D. Keep the global operator-selected chunker.** Typed blocks improve input,
  but selection remains one explicit deployment setting.

If none is correct, state the replacement precisely. Do not settle for
"configurable" without naming the default, precedence, snapshot boundary, and
failure behavior.

## OrgMemory repository evidence

| Fact | Evidence |
| --- | --- |
| Worker configuration has one global `chunker-id`, default `fixed-token`. | `apps/worker/src/main/resources/application.yml:103`; `apps/worker/src/main/java/com/orgmemory/worker/ingestion/SourceProcessingProperties.java:14,34` |
| `DocumentProcessingEngine` reads that value once per document, executes one chunker across the entire `CanonicalDocument`, and only changes actual strategy for the semantic-vector failure fallback. | `apps/worker/src/main/java/com/orgmemory/worker/ingestion/DocumentProcessingEngine.java:108-155` |
| The profile canonical form already includes requested parser, actual parser, requested chunker, actual chunker, tokenizer, semantic embedding, canonical-text hash, and sorted options. | `components/graph-rag-core/src/main/java/com/orgmemory/graphrag/processing/ResolvedDocumentProcessingProfile.java:94-119` |
| That canonical form is hashed and constructor-verified; the profile is explicitly documented as an idempotency input. | `components/graph-rag-core/src/main/java/com/orgmemory/graphrag/processing/ResolvedDocumentProcessingProfile.java:13-16,35-51,61-86` |
| READY source revisions persist actual `chunker_version`, canonical processing profile, and profile SHA-256. | `core/src/main/java/com/orgmemory/core/knowledge/sourceledger/SourceRevision.java:74-84,166-183` |
| Uploading bytes creates fresh source, revision, blob, and object identities before processing; it does not mutate an existing immutable revision in place. | `core/src/main/java/com/orgmemory/core/knowledge/sourceledger/SourceUploadService.java:59-86` |
| The cited graph-job test is not evidence for document-processing identity: it covers the separate graph-processing profile and must not be used to justify document parser/chunker retry semantics. | `core/src/test/java/com/orgmemory/core/knowledge/graph/GraphIndexingCoordinatorTests.java:430-445`; `core/src/main/java/com/orgmemory/core/knowledge/graph/GraphIndexJob.java:387-395` |
| Item 1 now produces `HEADING`, `TABLE`, and `PARAGRAPH` blocks, preserves table cell/row boundaries, and bumps parser identity to `spring-ai-document-reader:2.1.0`. | `apps/worker/src/main/java/com/orgmemory/worker/ingestion/SpringAiDocumentParser.java`; `apps/worker/src/main/java/com/orgmemory/worker/ingestion/XhtmlBlockHandler.java`; commits `508e6e8c` and `7966f851` |
| A real parser-to-chunker proof shows a 40-row DOCX table reaches `ParagraphSemanticChunker`, repeats the header in every fragment, splits no row, and loses no row. | `apps/worker/src/test/java/com/orgmemory/worker/ingestion/ParsedTableReachesTheParagraphChunkerTests.java:31-75` |
| Existing DOCX uploads were already allowed but were parsed flat before item 1, so identical source bytes can produce different canonical text under parser 2.1.0. | `docs/increments/active/2026-08-07-knowledge-ingestion-coverage/design.md`, "Measured" and "Binding constraints" |

## Comparable-system evidence from pinned source

Pins verified locally for this review:

- LightRAG `v1.5.4`, commit
  `9a45b64c2ee25b1d806e90db926a8af37480bb16` at
  `D:/OrgMemory/tmp/upstream-lightrag-v1.5.4`.
- Onyx commit `618b5031bf21463f44e3bed9eb9d5073b806fec0` at
  `D:/OrgMemory/tmp/onyx`.

| System | Behavior | Mechanism and source |
| --- | --- | --- |
| LightRAG v1.5.4 | Parser/process rules can select F/R/V/P per file/suffix; selection is not one deployment-global strategy. | `lightrag/parser/routing.py:1045-1079,1166-1204` |
| LightRAG v1.5.4 | The selected strategy's options are reduced to a per-document snapshot; already-enqueued documents keep a frozen snapshot when runtime defaults change. | `lightrag/parser/routing.py:237-247,264-324,357-382,467-521` |
| LightRAG v1.5.4 | P is not a universal fallback: its documented contract relies on structured sidecar content and degrades to R when that content is absent. | `docs/FileProcessingPipeline.md:480-503`; `lightrag/pipeline.py` chunker dispatch path |
| Onyx | One document dispatches each section by semantic type rather than choosing one strategy from the file suffix. | `backend/onyx/indexing/chunking/document_chunker.py:41-48,90-127` |
| Onyx | Tabular sections have dedicated row/header-aware chunking and flush surrounding text state before table processing. | `backend/onyx/indexing/chunking/tabular_section_chunker/tabular_section_chunker.py:47-79,137-228,231-276` |

These are evidence, not requirements. OrgMemory's immutable processing profile,
revision ledger, authorization gates, and deterministic graph-job identity are
stricter than either reference and must win where copying would weaken them.

## Observed cost motivating the decision

Before item 1, every Tika document became a flat paragraph and normalization
removed tab boundaries. `ParagraphSemanticChunker` contained table-aware behavior
but could not receive a TABLE block in the production parser path. DOCX was
already admitted, so this was an existing correctness defect, not merely missing
spreadsheet support. Item 1 fixes the structural input and passed
`:apps:worker:test`, `:components:graph-rag-core:test`, and `:core:test` before
commits `508e6e8c` and `7966f851`.

The unresolved cost is policy drift. A content-type map hidden in code can change
future outputs without an operator-visible configuration diff. Conversely, a
single global strategy can force table policy onto prose or make structure-aware
behavior unreachable. The decision must make this drift explicit and auditable.

## Required attacks

At minimum, try to falsify the proposal with these contradictions:

1. **Mixed documents:** DOCX and HTML can contain headings, prose, and tables in
   one source. Explain why content type is or is not a sufficient selection key.
2. **Identity versus policy:** distinguish requested policy identity from actual
   execution identity. State exactly what enters `profileSha256`, including the
   resolver version or policy revision if one exists.
3. **Old READY revisions:** parser 2.1.0 changes canonical output for existing
   DOCX bytes. Decide whether old revisions remain immutable and queryable,
   whether rebuild is opt-in/new-revision only, and why silent in-place
   reprocessing is allowed or forbidden.
4. **Retry determinism:** a leased ingestion retry after deployment/config
   change must not silently execute a different policy for the same revision.
   Identify whether current claim/job data is sufficient and require a fix if
   not.
5. **Fallback semantics:** preserve semantic-vector's explicit requested/actual
   fallback without turning every table/prose branch into an unrecorded actual
   strategy.

## Scope limits

Do not design the new format allowlist, CSV parser, per-format size limits, UI,
or an operational backfill job. You may identify binding interfaces those later
items need, but the verdict is only for chunker resolution, profile identity,
retry determinism, and treatment of revisions already READY under the old parser.

## Reviewer routing record

- 2026-08-10: the required Fable 5 launch through Orca failed before a terminal
  handle existed: `Timed out waiting for terminal handle after creation`.
  `orca terminal list` confirmed zero terminals in this worktree. Per the
  architecture-challenge procedure, review falls back to a fresh Codex
  `gpt-5.6-sol` session with `ultra` reasoning.
- 2026-08-10: launching that fallback directly in the existing ingestion
  worktree returned the same handle timeout and again left zero terminals. The
  fallback is therefore being retried in a fresh Orca worktree while reading
  this branch and brief by absolute path.
