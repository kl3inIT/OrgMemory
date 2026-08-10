# Architecture Challenge Verdict: Ingestion Chunker Resolution

Date: 2026-08-10
Source checkout: `D:\OrgMemory-worktrees\ingestion-coverage`
Commit reviewed: `7966f851bf2fee8efc8275f3563a1ef4646bd8da`
Independent reviewer: Codex `gpt-5.6-sol`, `ultra`, Orca terminal
`term_e5e5997a-e043-496e-8ed5-d58a830771bb`

## Verdict

**REJECT** the proposed whole-document resolution by content type.

Content type is not a sufficient selection key for mixed DOCX and HTML
documents, which can contain headings, prose, and tables in one immutable
source revision. The current ingestion job also pins no processing policy, so
a retry after a deployment or configuration change can execute a different
parser, chunker, dispatch map, or option set for the same revision.

Keep the typed-block parser work. Replace the rejected proposal with the
versioned structured-block composite policy below before item 2 proceeds.

## Committed architecture

The production default is the named, immutable policy
`structured-block-v1`.

- Selection key: `CanonicalDocument` block kind and structured/unstructured
  shape, not file suffix or media type alone.
- Owner: the worker processing-policy resolver, bound atomically before the
  first parsing or chunking side effect.
- Document-level component: `paragraph-semantic:lightrag-v1.5.4`.
- Dispatch behavior: the composite owns its versioned table-, heading-, prose-,
  and unstructured-document branches. Its dispatch table and every invoked
  sub-algorithm identity are profile inputs; a single friendly component name
  must not conceal materially different execution.
- Default precedence: an explicitly selected named/versioned policy may
  override `structured-block-v1`; a live raw `chunker-id` string is not
  production authority and is never re-read by a retry.
- Safety: an alternate policy that cannot preserve structured-table invariants
  is refused for governed ingestion or confined to an explicit evaluation or
  rebuild workflow.

This adopts Architecture C as one auditable document-level composite rather
than several unrecorded per-block "actual chunkers".

## Requested and resolved snapshots

Before the first processing side effect, atomically persist a requested
snapshot containing at least:

- processing-profile schema version;
- requested parser id/version and all output-affecting parser options;
- requested policy id/version;
- composite/dispatch-table id/version;
- requested component and every output-affecting component option;
- tokenizer and semantic-embedding identities/options when applicable;
- the normalization/canonicalization identity.

Upload-enqueue binding is required only if the product promises "policy active
when upload was accepted". Otherwise, an atomic first-claim binding is valid.
Every later claim must return the stored snapshot and ignore live defaults.

After parsing and chunking, but before raw registration, normalization,
embedding publication, Asset publication, or another durable downstream side
effect, compare-and-set a resolved execution snapshot containing:

- the complete requested snapshot;
- actual parser id/version;
- actual composite component and invoked sub-algorithm identities;
- actual semantic fallback target and normalized fallback reason, if any;
- canonical-text SHA-256;
- canonical chunk-manifest SHA-256;
- the canonical, sorted option maps used by every output-affecting component.

`profileSha256` is computed from that complete canonical form. Map order must
not change the hash. Policy, resolver, dispatch, component, option,
normalization, fallback, canonical-text, or chunk-manifest changes must change
the hash.

## Retry and fallback semantics

- Before a resolved snapshot exists, a retry uses the same requested snapshot.
- After a resolved snapshot exists, a retry uses its actual parser, composite,
  dispatch, and fallback outcome; it does not resolve against current defaults.
- The allowed semantic-vector failure fallback remains explicit:
  requested `semantic-vector`, actual `recursive-character`, with both option
  sets, embedding identity, and a normalized fallback code in the profile.
- A retry after that fallback remains on `recursive-character` even if the
  semantic provider later recovers.
- Re-execution must reproduce the canonical-text and chunk-manifest hashes.
  Mismatch fails closed as `PROCESSING_PROFILE_MISMATCH` and publishes nothing.
- An unavailable pinned component is retried or explicitly failed; it is not
  silently replaced by the active default.
- Existing nonterminal jobs without snapshots must be drained or explicitly
  pinned to a verified legacy policy. A migration must not guess from the new
  deployment configuration.

## Existing READY revisions

READY revisions produced under the previous parser remain immutable and valid.
Do not modify their parser/chunker versions, profile, canonical text, chunks,
or Asset version, and do not silently enqueue them after parser 2.1.0 deploys.
They remain queryable while they are the Source Object's current READY
revision, and retained as historical immutable evidence after later
publication.

Rebuild is opt-in and creates new identity: a future same-object rebuild creates
revision N+1 and a new immutable Asset version, while the current upload path
creates a new Source Object, revision, blob, and object key. This verdict
approves no automatic backfill.

## Strongest counterargument

Whole-document content-type resolution is simpler: XLSX/DOCX/HTML could select
`paragraph-semantic`, TXT/PDF could select a recursive strategy, and one
requested/actual pair is easy to audit.

That does not defeat structured dispatch. DOCX and HTML are demonstrably mixed.
A whole-document choice either makes table preservation depend on a prose
strategy or prevents prose from using appropriate logic. Typed parsing already
paid the cost of preserving block kinds; discarding them during resolution
reintroduces the structural-homogeneity error this increment fixes. One
versioned composite identity plus a canonical dispatch table answers the audit
objection.

The counterattack did refine two bindings without changing `REJECT`:

1. deterministic binding may occur on atomic first claim rather than upload
   enqueue, unless acceptance-time policy is a product promise;
2. operator override remains valid through named immutable policies, not a raw
   live chunker string.

If sub-strategies later become independently configurable or plugin-provided,
the current single composite profile is insufficient and this decision must be
reopened.

## Must-fix implementation gates

1. Replace content-type resolution with `structured-block-v1`.
2. Make `paragraph-semantic` the default composite document component.
3. Bind the complete requested snapshot before the first processing side
   effect and return it in every later claim.
4. Persist the resolved snapshot before downstream durable side effects.
5. Add schema, policy, dispatch, sub-algorithm, fallback, complete option, and
   chunk-manifest identities to canonical profile hashing.
6. Make READY require a nonblank canonical processing profile and valid SHA.
7. Preserve only explicit, recorded fallbacks.
8. Correct the graph-job/document-profile evidence distinction.
9. Reconcile the Knowledge Ingestion spec and mirrored test matrix.

## Must-pass test gates

- Mixed DOCX and HTML pass through `DocumentProcessingEngine` under the default
  policy with table headers repeated, no row split/loss, and correct surrounding
  prose/heading behavior.
- Plain TXT and PDF cover the composite unstructured branch.
- Any output-affecting policy/profile input changes `profileSha256`; option map
  order does not.
- A malformed profile or chunk manifest is rejected.
- A job bound to policy v1 remains on v1 after v2 becomes active.
- Retry after chunking reproduces profile and manifest hashes across deployment
  changes.
- Semantic fallback records requested V/actual R and remains on R on retry.
- An unavailable pinned component fails closed.
- READY without the required profile identity is rejected by the database.
- Existing READY parser-2.0 DOCX evidence stays unchanged after parser 2.1,
  while explicit re-ingestion creates new identity.
- Required suites: `:components:graph-rag-core:test`, `:core:test`, and
  `:apps:worker:test`.

## Decisive evidence

- Current live global selection and whole-document execution:
  `application.yml:101-112`, `SourceProcessingProperties.java:14,34`, and
  `DocumentProcessingEngine.java:108-155`.
- Typed mixed-document blocks and existing block-aware dispatch:
  `SpringAiDocumentParser.java`, `XhtmlBlockHandler.java`, and
  `ParagraphSemanticChunker.java:39-129,299-379,434-525`.
- Final profile currently appears only after execution:
  `SourceIngestionProcessor.java:199-205,278-296`.
- Job and claim carry no requested snapshot:
  `SourceIngestionJob.java`, `ClaimedSourceRevision.java`, and
  `V1__baseline.sql:1181-1200`.
- READY stores actual chunker/profile, but its database constraint does not
  require the processing profile: `SourceRevision.java:166-183` and
  `V1__baseline.sql:1334-1343`.
- The cited graph test concerns graph-processing identity, not the document
  processing profile: `GraphIndexingCoordinatorTests.java:430-445` and
  `GraphIndexJob.java:387-395`.
- Pinned references support snapshot and semantic-section dispatch:
  LightRAG `routing.py:237-247,264-324,353-382,467-521` and Onyx
  `document_chunker.py:41-48,90-127`,
  `tabular_section_chunker.py:231-276`.

## Rejected alternatives and reopening conditions

Whole-document resolution by content type may be reopened only if the admitted
parser contract proves structural homogeneity (or emits separately governed
child documents), quality tests show a material advantage without table or
provenance loss, the mapping is a named immutable policy in the profile hash,
jobs pin it, and mixed DOCX/HTML negative tests prove safe routing.

Raw global operator-selected chunking may be reopened only for an isolated
evaluation/rebuild workflow or after policy validation proves every
structured-block invariant. It must not govern ordinary production uploads as
an unchecked string.

No format allowlist, CSV reader, per-format size limit, UI, or operational
backfill design is approved by this verdict.
