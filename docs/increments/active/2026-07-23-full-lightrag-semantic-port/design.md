# Full LightRAG Semantic Port Program

## Outcome

Port the complete LightRAG `v1.5.4` semantic engine and runtime capability set
to Java while preserving OrgMemory's stricter evidence-level authorization.

The twelve PRs integrate through the remote `light-rag` branch. No program PR
targets `main` directly. After all twelve PRs pass their completion audit, one
final `light-rag -> main` PR carries the complete program.

## Invariants

- `graph-rag-core` remains pure Java.
- `graph-rag-core` contains executable LightRAG semantics, not an
  interface-only abstraction layer.
- Spring AI and vendor SDKs are adapters; Spring Boot is the imperative
  runtime shell.
- PostgreSQL is the first production adapter, not a permanent core assumption.
- Every adapter implements a shared port and conformance suite.
- The full engine supports all upstream query strategies; product delivery
  applies a separate secure policy and default.
- Canonical evidence, ACL, provenance, lifecycle, and audit remain in
  PostgreSQL.
- Derived stores are rebuildable and cannot grant access.
- No PR closes a parity row using compilation alone.
- Core orchestration is synchronous and Reactor-free. The runtime may use
  bounded virtual threads for blocking adapters and Reactor at streaming
  delivery boundaries.
- Material architecture decisions use the repository's Fable 5 debate rule
  before code is committed.
- Publication retries bind batch identity and namespace-scoped idempotency to
  one canonical manifest fingerprint persisted on the visible snapshot.
- Durable preparation receipts live beside the publication head. Publication
  verifies every required kind; readers validate pinned snapshots against
  immutable publication history rather than trusting caller-created state.

## Branch And Review Flow

```text
origin/main
    |
    +-- light-rag
          |
          +-- PR 1 branch --review/CI--> merge to light-rag
          |
          +-- PR 2 branch --review/CI--> merge to light-rag
          |
          +-- ... PR 12
          |
          +-- final reviewed PR ------------------------> main
```

Each next branch starts from the latest remote `light-rag` after the preceding
merge. CodeRabbit findings are fixed when actionable. A PR merges only after all
required GitHub checks are green and no unresolved actionable review thread
remains.

## PR 4 Multimodal Boundary

`graph-rag-core` owns the executable semantics for image, table, and equation
analysis: exact canonical-text anchors, deterministic surrounding context,
preflight policy, content-addressed invocation identity, terminal outcomes, and
derived chunk construction. `PENDING` is a worker scheduling state and is not a
multimodal analysis result. The only terminal results are:

- `Success`, which materializes validated analysis plus its ACL, route, cache
  identity, source span, and artifact hash;
- `Skipped`, which is allowed only for deterministic pre-provider rules such
  as a disabled modality, unsupported raster format, or configured size limit;
- `Failure`, classified as transient or permanent and never converted to
  `Skipped`. Any enabled analysis failure blocks publication.

The LightRAG `1.0` split-bundle decoder is an integration adapter. It validates
the declared canonical hash and block anchors, rejects absolute/traversing
asset paths, converts storage locations to opaque content-addressed artifacts,
and ignores upstream `llm_analyze_result` values. OrgMemory recomputes model
analysis under the pinned processing profile and current ACL snapshot.

Spring AI is the provider adapter. Images use a media-bearing vision request;
tables and equations use the text-extraction route. Structured-output
conformance gets exactly one retry. Provider failures remain failures.

Runtime worker wiring and durable outcome/cache persistence stay in PR 11 and
PR 6/8 respectively; PR 4 exposes no silent no-op runtime configuration.

## PR 5 Extraction And Indexing Boundary

The pinned LightRAG `v1.5.4` implementation is the semantic oracle for prompt
shape, one continuation/gleaning pass, token guarding, longer-description
replacement, relation support weights, and entity/relation embedding payloads.
Persisted chunk headings flow into the prompt as token-bounded, untrusted
section context and may disambiguate evidence but may not create facts.
OrgMemory intentionally tightens two boundaries:

- a malformed final relation whose endpoints cannot be resolved after all
  extraction rounds fails the chunk instead of manufacturing an ungrounded
  global entity;
- descriptions are never merged into a globally visible canonical summary.

`graph-rag-core` owns the extraction prompt, conversation plan, gleaning token
guard, deterministic cross-round merge, final endpoint validation, canonical
identity, relation-support aggregation, and permission-scoped summary input.
The Spring AI adapter owns only model invocation, structured-response parsing,
and provider usage metadata. The worker owns bounded virtual-thread execution,
lease heartbeats, embedding effects, profiling telemetry, and the existing
atomic publication transaction.

Canonical entity and relation rows remain identity-only. An entity identity is
its organization-scoped normalized name. A relation identity is its
organization-scoped endpoint pair plus orientation; relation type does not
split an otherwise identical edge. Every entity/relation type, description,
keyword, confidence, support weight, ACL snapshot, and extractor identity stays
on an evidence contribution. A query may merge or summarize only contributions
already filtered by one `AuthorizedEvidenceScope`. A reusable summary cache is
therefore keyed by canonical identity, authorization fingerprint, and a
content-derived projection fingerprint; durable cache wiring belongs to PR 6.

LightRAG's three vector surfaces remain distinct. OrgMemory's existing
`knowledge_chunks` projection owns chunk vectors, while graph publication owns
entity- and relation-contribution vectors. All three must use the immutable
embedding profile pinned to the Knowledge Asset version. PR 5 makes the
extraction/indexing semantics complete without folding chunk vectors into the
graph-specific storage contract; PR 8 migrates all projection kinds to the
shared namespace snapshot.

This boundary was reviewed with Fable 5. The accepted decision is functional
pure-Java semantics plus Spring AI effect adapters and a Spring Boot runtime
shell. The rejected alternative was persisting one global LightRAG-style
summary or weight on canonical graph identities, because that would combine
text and support from differently authorized evidence.

## PR 6 Lifecycle, Curation And Cache Boundary

PR 6 implements the lifecycle and management behavior present in pinned
LightRAG `v1.5.4`, with security-preserving storage semantics defined by
[Decision 0014](../../../decisions/0014-lightrag-lifecycle-curation-and-cache.md).

Updates create immutable revisions and atomically move the Knowledge Asset
head. Delete immediately revokes visibility and removes only the retired
revision's derived contributions; shared identities survive while authorized
surviving evidence contributes to them. Retry, resume, expired-lease recovery,
cancellation, and rebuild are durable job transitions. Every worker rechecks
current-version and cancellation state immediately before publication.

Graph create/edit/merge/delete is represented by an append-only curation
overlay rather than destructive mutation. Curated contributions, reversible
identity aliases, and reversible suppressions carry actor, authorization-model,
ACL-generation, reason, and time provenance. Overlay application happens only
after evidence authorization. Export uses the same authorization scope and
includes provenance; it is explicitly permission checked and audited.

Exact model and keyword caches remain separate from permission-scoped retrieval
results. Canonical SHA-256 keys include every output-affecting parameter.
Retrieval keys additionally bind the published snapshot and authorization
fingerprint, and citations are rechecked on every hit. Namespace invalidation
runs after publish, abort, delete, and curation; generation-bound keys and TTL
provide structural and eventual cleanup.

`graph-rag-core` owns these executable semantics without Spring. Spring owns
authorization, transactions, audit, and leased runtime jobs. PostgreSQL is the
first adapter. The PR does not move `GRAPH` into the shared publication head
early; that migration remains PR 8.

## PR 7 Full Query Runtime Boundary

PR 7 completes the executable query semantics in `graph-rag-core` and proves
them through deterministic testkit adapters. It does not enable production
mixed graph and chunk retrieval until PR 8 moves `GRAPH` into the shared
namespace snapshot. This resolves the ordering constraint without reducing the
LightRAG capability set: PR 7 owns the algorithm, while PR 8 makes every
production read tear-free across graph, vector, lexical, and content stores.

Query modes describe seed and expansion behavior, not output-channel filters:

- `LOCAL` seeds entities with low-level keywords, recovers their incident
  relations and supporting chunks;
- `GLOBAL` seeds relations with high-level keywords, recovers their endpoint
  entities and supporting chunks;
- `HYBRID` runs both graph branches and deterministically interleaves them;
- `NAIVE` retrieves only chunk vectors from the original query;
- `MIX` combines the complete hybrid graph branch with original-query chunk
  retrieval;
- `BYPASS` performs no retrieval and invokes the answer model directly.

Caller-supplied high/low keywords are a separate trusted-planner input for any
graph mode, not an alias for `BYPASS`. When they are absent, the keyword model
uses the pinned LightRAG prompt and normalization rules. If both lists are
empty, queries shorter than fifty characters fall back to one low-level
keyword containing the original query; longer queries return a typed
no-retrieval result.

Embedding work is one batched provider call over distinct texts: original
query for chunk seeds, joined low-level keywords for entity seeds, and joined
high-level keywords for relation seeds. One vector is never reused across
these semantically different inputs.

Related chunks are recovered from authorized contribution evidence. Entity
chunks are occurrence-counted, stable-deduplicated, and selected by weighted
polling or vector similarity with deterministic fallback. Relation chunks use
the same process after removing chunks already selected from entities. The
trace records `ENTITY`, `RELATION`, or `VECTOR` origin, occurrence frequency,
and channel order.

Entity seed order follows vector similarity. Incident relations use a stable
composite order of visible degree, visible support weight, and relation
identity; no lossy scalar encoding is used. Hybrid entity/relation lists and
mixed chunk lists use deterministic round-robin interleaving with stable
deduplication. Reranking is an effect port over chunk candidates, records raw
and rerank scores, applies the configured minimum score and `chunkTopK` after
rerank, and falls back to the original authorized order on provider failure
while emitting a trace event.

Token accounting includes rendered entity/relation/chunk records and the
context/system-template overhead. Whole evidence items are selected within
entity, relation, and total budgets; evidence text is never partially cut in a
way that detaches it from provenance.

The engine returns a sealed result containing context, full prompt when
requested, authorized references, processing metadata, and an immutable raw
retrieval trace. Answer results may be complete or streaming through a
framework-neutral iterator contract; references and trace exist before the
first answer token. Partial streams are never cached. Conversation history is
passed only to the answer model and disables completed-answer cache reuse until
history is included in the exact key. Every delivery shell rechecks citations
before egress.

This boundary was reviewed with Claude Fable 5. The strongest counterargument
was that PR 7 and PR 8 previously contradicted each other about when mixed
retrieval could be wired. The accepted decision is executable core/testkit
parity in PR 7 plus fail-closed production activation in PR 8. Rejected
alternatives were enabling mixed PostgreSQL reads before a shared snapshot,
reducing `MIX` to chunk-only retrieval, treating modes as output filters, and
reusing one query embedding for all channels.

## PR 8 Shared PostgreSQL Snapshot Boundary

PR 8 uses one namespace publication head across content, lexical, vector, and
graph projections. Competing batches may prepare the same next generation, but
every staged row is addressed by `batch_id`; publication is serialized per
namespace and only the winning batch enters immutable publication history.
Readers pin the exact winning batch rather than selecting staged rows by a
generation-shaped predicate. An aborted or losing batch therefore cannot leak
into retrieval.

Each adapter initializes a new batch from the exact published predecessor,
applies its mutations, and records preparation separately. Publication advances
the namespace head only when every required receipt exists and the expected
previous generation still matches. Historical winning batches remain readable,
while discard removes unreachable staged rows. This correctness-first
copy-forward representation can later be replaced by ancestry plus compaction
without changing the core contracts.

PostgreSQL FTS and pgvector apply organization and authorized Knowledge Asset
filters before scoring and `LIMIT`. The snapshot graph store applies the same
scope to contributions and requires both relation endpoints to remain visible.
Apache AGE remains an optional rebuildable topology accelerator; relational
snapshot reads are authoritative and are the fallback for historical pins.

## Scope Authority

[Decision 0013](../../../decisions/0013-full-lightrag-semantic-port.md) and the
[parity manifest](../../../research/lightrag-v1.5.4-parity-manifest.md) define
the program scope. Earlier completed increments are historical evidence, not
reasons to omit a manifest row.
