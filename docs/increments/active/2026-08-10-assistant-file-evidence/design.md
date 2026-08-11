# Assistant Governed File Evidence

## Intent

Deliver a governed Assistant composer upload that becomes citable evidence
without bypassing Source Ledger, processing identity, or authorization. It uses
the reusable parser boundary shipped by the preceding Knowledge ingestion
coverage increment; this increment does not add another parser caller or move
ingestion ownership into Assistant.

The first Assistant delivery is deliberately durable governed evidence. It does
not introduce an ephemeral “send these bytes directly to the model for this turn”
path.

## Current facts

- `components:graph-rag-core` owns the channel-neutral parsing port, canonical
  document IR, typed blocks, and parse-failure taxonomy.
- `integrations:document-parsing-spring-ai` owns the concrete public parser
  adapter, dedicated CSV reader, sanitized XHTML extraction, format capability,
  and parser identity. Worker remains its only production caller.
- `DocumentProcessingEngine` is correctly worker-owned: it resolves the immutable
  processing profile, chunks, embeds, and coordinates durable publication.
- `structured-block-v1` is the production processing policy. It dispatches by
  canonical block kind and pins requested and resolved processing identity for
  deterministic retry.
- `SourceUploadService` already validates the actor, Knowledge Space,
  classification, declared access, immutable object key, and upload limits before
  registering an asynchronous Source revision.
- Assistant currently accepts text only. Its answers and persisted citations use
  permission-scoped Knowledge retrieval and re-hydrate canonical Source evidence.

## Shipped dependency: reusable parsing boundary

### Ownership

The completed Knowledge ingestion coverage increment keeps deterministic
contracts and canonical IR in `components:graph-rag-core` and the concrete
Tika/Spring AI implementation in the replaceable integration:

`integrations:document-parsing-spring-ai`

Worker remains the only direct production caller after Assistant ships, because
Assistant uses the Source pipeline rather than calling the parser. The module
owns:

- PDF page reading;
- Tika XHTML extraction for structured office and HTML documents;
- the dedicated CSV reader;
- HTML boilerplate and active-content removal;
- block-kind-aware normalization;
- format detection and parser capability descriptors; and
- the parser component identity/version.

It does not own:

- which product channel admits a format;
- upload size or per-format resource policy;
- Source, revision, organization, Space, classification, or actor identity;
- chunker selection, embedding, indexing, publication, or retry orchestration;
- HTTP/UI error copy; or
- Spring component discovery for a particular application.

The implementation is a public adapter constructed by worker configuration.
Framework exceptions are translated into the channel-neutral parse failure
taxonomy at the `DocumentParser` boundary. Knowledge admission and durable
failure mapping remain outside the adapter.

### Capability is not admission

Parser capability and product admission are intentionally separate:

```text
parser can extract format
        ∩
channel admits format
        ∩
resource policy accepts this file
        =
accepted upload
```

The parser may know how to extract a format that Knowledge or Assistant has not
yet opened. Knowledge remains the authority for the accepted upload set. The API
publishes that channel capability to the browser; the web `accept` list is a UX
hint, never an authorization rule. Contract tests prove every admitted format has
a parser and every advertised format is admitted server-side.

### Why `DocumentProcessingEngine` stays in worker

Parsing is reusable; ingestion orchestration is not a parsing concern. Assistant
governed uploads call the existing Source use case and are processed by the same
worker. Moving `DocumentProcessingEngine` into a shared module would duplicate
Source lifecycle semantics in a nominally generic layer and encourage request-
thread processing.

## Architecture decision: governed Assistant evidence

### One upload, one Source identity

Composer upload invokes an Assistant application use case which delegates byte
registration to `SourceUploadService`. The user must choose a Knowledge Space;
classification defaults and validation remain exactly those of ordinary Source
upload. The operation creates the same immutable Source object, revision, blob,
and ingestion job as the Documents workspace.

Assistant adds only a binding, not another copy of the file:

```text
Assistant conversation
  -> Assistant evidence binding
       -> immutable Source object + requested revision
            -> Source blob / ingestion job
            -> READY Knowledge Asset / chunks
            -> active-engine projection published / answerable
```

The binding is conversation-scoped and records creator identity and creation
time for audit. It never grants read access. A submitted binding is usable only
when all of these are true at turn claim time:

1. the binding belongs to the current organization and conversation;
2. the authenticated actor may use that conversation and currently read the
   target Source under the canonical Knowledge policy;
3. the bound revision is current, its evidence integrity checks pass, and it is
   answerable under the active Assistant retrieval engine; and
4. the resulting chunks survive the existing authorization filter and citation
   hydration path.

`SourceRevision.READY` alone is insufficient for the default GraphRAG engine.
The graph-index job is enqueued when the revision becomes ready, and the winning
projection batch may still be queued, processing, retrying, or failed. The
binding status therefore resolves through an engine-aligned readiness port. For
GraphRAG, presentation `READY` requires the selected Asset's projection batch to
be published and visible to the exact engine serving the turn. For a configured
canonical engine it requires the canonical chunks that engine reads. Return 404
across ownership/tenant boundaries and a typed not-ready state for a valid
binding still processing. Never turn possession of an attachment UUID into
authority.

### Turn semantics

- The client uploads at most three files per turn and polls their Source-derived
  preparation state.
- Text may be sent without waiting, but a selected file is never silently
  omitted. A turn carrying a selected binding is enabled only when it is ready;
  terminal processing failure is shown before submit.
- Turn claim atomically persists the ordered binding IDs beside the user message
  and rechecks them. A retry reuses the same ordered, immutable selection.
- A turn with bindings is selection-only in the first delivery. It does not
  silently mix the rest of organizational Knowledge into “summarize this file.”
  A new internal `KnowledgeEvidenceSelection` argument carries the selected
  Asset/Source identities into the configured `knowledge::search` engine. It is
  not a public strategy selector.
- The selected identity set is a second ceiling after actor authorization:
  intersect it before seed scoring, preserve it through graph expansion, and
  reapply it during closure verification and citation hydration. Chunks or
  citations from non-selected Assets cannot enter context. Entity/relationship
  facts survive only when their accepted provenance belongs to the selected
  set. The normal global context budget applies across the selected authorized
  subset.
- An attachment turn must receive usable evidence from every selected binding or
  return a typed evidence-unavailable result before generation; a selected file
  is never treated as optional because another file produced hits. Raw extracted
  text is not concatenated directly into the prompt.
- The answer emits ordinary OrgMemory citations. Transcript replay uses existing
  citation behavior and fails closed if evidence later becomes unavailable.
- Uploaded content remains untrusted evidence. Existing prompt/data boundaries
  apply on every turn in which it can be retrieved.

### Lifecycle

The file is durable Knowledge evidence from creation; there is no automatic
promotion step and no hidden personal-file store. The composer explicitly says
that upload publishes the processed file to the chosen Space audience. An actor
with no Space where they hold `can_create_asset` cannot attach a file and is
directed to the Documents workflow or an administrator; the product does not
fall back to a private attachment store. Removing it from the composer only
removes the pending binding selection. Source retirement/deletion follows the
normal governed lifecycle and must account for persisted conversation references
in the same way citations already do.

The MVP accepts only formats admitted by Knowledge ingestion. Images, direct
provider file handles, OCR, archive recursion, MSG/EML attachment recursion, and
model-generated files are out of scope.

## Rejected first-delivery alternative: dual lane

A second lane could create an `AssistantTransientAttachment` scoped to
organization, actor, conversation, and turn, with TTL deletion, bounded
synchronous parsing, no publication, and no canonical citation. Explicit
promotion would copy its bytes into a new Source identity and reprocess them.

That is a legitimate later product, but it needs its own retention, malware/DLP,
egress, authorization, replay, deletion, tool-access, and disclosure model. It
would also tempt the API to reuse the parser on the request thread and create an
uncitable answer path. The first delivery rejects this complexity.

## API and UI shape

The exact OpenAPI names are implementation details, but the contracts are:

- create governed evidence binding with multipart bytes, conversation ID,
  Knowledge Space ID, and classification; there is deliberately no
  bind-an-existing-Source-by-ID endpoint;
- list/get binding status with `UPLOADING`, `PROCESSING`, `INDEXING`, `READY`,
  `FAILED`, and `UNAVAILABLE` presentation states derived from Source state plus
  active-engine projection readiness;
- submit an ordered list of at most three binding IDs with the Assistant turn;
  and
- open the resulting evidence through the existing governed document/citation
  viewer, not a new raw-file endpoint.

The composer owns pending local upload progress and selected binding IDs. Server
state remains in TanStack Query. The generic prompt-input primitive exposes file
selection events but contains no Knowledge or permission policy.

## Security invariants

1. Attachment reference never grants access.
2. Tenant, actor, Space, and classification come from authenticated/server-owned
   context and canonical Source commands, not trusted client ownership fields.
3. Authorization occurs before retrieval/ranking/context assembly and again
   during citation hydration/open.
4. Unsupported, unready, retired, stale, or integrity-failing evidence fails
   closed and is never silently excluded from a selected turn.
5. Parser detection cannot widen channel admission and embedded-resource
   recursion remains disabled.
6. Heavy parsing, chunking, embedding, and graph publication remain off API
   request threads.
7. No uploaded bytes or extracted text enter logs, traces, audit payloads, or
   Northstar notes.

## Upstream influence

The pinned evidence is in [reference-study.md](reference-study.md).

- From Northstar: immutable content hashing, visible async readiness, a small
  per-turn limit, bounded context, and repeated untrusted-evidence fencing.
- From Onyx: separate transient and durable identities, use-time ownership
  verification, explicit processing/deletion states, and context-budget-aware
  selection between direct context, retrieval, and tools.
- Deliberately not inherited: Northstar's UUID-sufficient attachment access and
  Onyx's user/project authorization ceiling.

## Architecture challenge result

The independent reviewer returned `REVISE`. The accepted design now gates READY
on active-engine answerability, carries a selected-evidence ceiling through
retrieval and citation hydration, and requires usable evidence from every
selected binding. The execution record and rejected transient-lane alternative
are in `challenge-verdict.md`.
