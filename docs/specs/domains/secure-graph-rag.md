# Secure GraphRAG

Source: `components/graph-rag-core`, `components/graph-rag-testkit`,
`integrations/graph-rag-*`, `apps/worker/src/main/java/com/orgmemory/worker/graph`,
`core/src/main/java/com/orgmemory/core/knowledge`,
`apps/web/src/features/knowledge`, and — for the payload-boundary configuration
this document states — `apps/api/src/main/resources/application*.yml` and
`apps/worker/src/main/resources/application*.yml`.

Reconciled: `2026-07-30-observability-platform (2b8a9d6)`.

## Current Contract

- `graph-rag-core` is pure Java and owns canonical graph identity, immutable
  evidence contributions, authorization-scoped read ports, atomic revision
  replacement, internal retrieval strategies, and context-budget rules.
- Canonical entities and relations do not contain merged descriptions.
  Descriptions, keywords, confidence, source revision, chunk, Knowledge Asset,
  ACL, projection, model, prompt, and extraction time remain on contributions.
- Every graph read requires an `AuthorizedEvidenceScope`; ranking, adjacency,
  degree, weight, aggregation, and citations can use only visible
  contributions.
- `SECURE_MIX` is the product default. Strategy selection remains internal.
- Query results preserve structured entity, relation, and chunk selections.
  Entity and relation descriptions retain their individual chunk evidence;
  they are never reduced to an authorization-free merged string.
- One deterministic renderer applies the final input-token budget across all
  selected Knowledge Spaces and assigns references to every contributing
  evidence item.
- One logical query prepares its keyword plan and distinct embedding batch once,
  then reuses that immutable provider output across authorized, pinned
  Knowledge Space snapshots. The prepared value contains no authorization
  decision or evidence.

## Structured Extraction

- `graph-rag-spring-ai` implements `EntityRelationExtractor` through Spring AI
  `ChatClient` structured output.
- The adapter is explicitly constructed with a provider id and `ChatModel`; it
  is not discovered as a generic Spring bean.
- The request profile supplies the model, prompt version, and item limits. The
  provider and prompt version must match the configured adapter.
- Source content is placed in the user message as untrusted evidence. The
  system instruction prohibits following source-embedded instructions or using
  facts outside the chunk.
- The response uses response-local entity references. Every relationship must
  resolve both endpoints within the same response.
- Malformed structured output, invalid orientation/confidence, limit overflow,
  duplicate references, unresolved endpoints, and provenance mismatch fail
  closed before a projection writer is called.

## PostgreSQL Projection

- `graph-rag-postgres` implements content, FTS lexical, pgvector, graph,
  publication, seed, embedding, and topology-candidate ports without becoming
  an authorization authority.
- Content, lexical, vector, and graph records stage under one immutable batch
  id. A namespace publication CAS exposes all required projection kinds
  together; losing and aborted batches never enter read history.
- New batches copy the exact published predecessor before applying mutations.
  Old winning batches remain readable, and discard removes unreachable staged
  data. Reads validate the complete snapshot identity before touching records.
- Canonical identity, contributions, publication heads, and entity/relation
  embeddings are stored relationally. Every query applies organization and the
  pre-authorized Knowledge Asset set before aggregation, distance threshold, and
  limit.
- Vector nearest-neighbor ordering uses the raw pgvector cosine-distance
  operator ascending so an eligible approximate index remains usable;
  similarity is derived only from the selected nearest rows.
- Revision replacement is atomic and generation-monotonic under a transaction
  advisory lock. Contribution and embedding writes are bounded by both record
  count and estimated payload bytes.
- pgvector supports exact, HNSW, half-vector HNSW, IVFFlat, and optional
  VChordRQ index strategies for immutable shared-projection vectors. Indexes
  are rebuildable and embedding profiles remain immutable.
- Apache AGE stores topology identity and evidence identifiers only. Bounded
  traversal filters every edge by authorized Knowledge Asset; all returned IDs
  remain candidates requiring relational evidence recheck.
- A globally bounded breadth-first relational traversal implements the same
  topology port when AGE is disabled. The Neo4j and OpenSearch adapters
  implement replaceable graph/search storage ports under the shared
  contribution-level authorization conformance suite; neither changes core
  retrieval contracts or becomes an authorization authority.

## Worker Publication

- A durable graph-index job is inserted only after the canonical source
  revision reaches `READY`. The job is unique per immutable Knowledge Asset
  version and immutable `GraphProcessingProfile`, and stores lease, attempts,
  retry time, and bounded failure evidence.
- `GraphProcessingProfile` and `EmbeddingProfile` are independent coordinates.
  The former snapshots algorithm, complete extraction settings, exact prompt
  templates, merge semantics and embedding-payload format; the latter owns
  provider/model/vector geometry. Changing either produces a new rebuildable
  generation without mutating a completed historical job.
- Claims pin the current asset/version/revision, active chunk generation, ACL
  snapshot/generation, language, immutable embedding profile and exact
  hash-addressed graph-processing profile. A retry reuses those coordinates;
  an explicit rebuild resolves the current profile and enqueues a distinct job
  only when its profile hash differs.
- Chunk extraction uses bounded virtual-thread concurrency and renews the lease
  between batches. Model output remains untrusted and must satisfy the
  structured extraction contract before assembly.
- Unicode-normalized entity and relation keys create deterministic,
  organization-scoped identities. Descriptions and confidence remain separate
  per-chunk evidence contributions.
- Contributions and their entity/relation embeddings publish through one
  PostgreSQL transaction after a current-version recheck. Retries cannot expose
  a partial generation or move the projection head backwards.
- The graph extraction route is independently configurable from Assistant chat;
  the graph embedding route must still equal the Knowledge Asset version's
  immutable embedding profile.

## Runtime Delivery

- Assistant retrieval resolves the full canonical ACL/classification/lifecycle
  scope before the graph engine or model sees evidence.
- Keyword planning has its own AI workload route and an exact organization-
  scoped cache keyed by query, language, strategy, route and prompt profile.
  Trusted caller keywords bypass both provider and cache.
- Published snapshots execute with bounded concurrency after provider
  preparation and outside a provider-spanning database transaction. Results
  remain deterministic by sorted Knowledge Space order; one failed snapshot
  fails the whole request closed. Multi-space reranking remains fail-closed
  until a global candidate-level rerank contract exists.
- The complete selected entity/relation/chunk evidence closure is BatchChecked
  and re-read from the canonical ledger after ranking. Scope, OpenFGA model,
  ACL snapshot, source revision, and projection generation must still match.
  That verified closure is the request authorization snapshot; the same
  pure-Java renderer creates the model prompt and citation numbering, and
  answer tokens stream without replaying the full authorization pipeline after
  generation.
- Reranking is server-owned typed policy. It defaults off, cannot be enabled
  without a named adapter, and a transient provider failure emits a sanitized
  fallback event before using the already-authorized retrieval order.
- Citation URLs point to an authenticated backend endpoint. The endpoint applies
  the current canonical authorization boundary on each open, streams the
  original evidence from object storage, and never exposes a MinIO key or
  presigned storage URL.
- Read-only MCP search uses the same application retrieval boundary. Graph
  explorer access stays curator/admin-only.
- Payload-free OpenTelemetry stages separate keyword planning/cache status,
  embedding, hashed per-snapshot retrieval, consolidation, authorization and
  provider-only reranking duration.
- Telemetry export is off until a deployment names a collector. Micrometer's OTLP
  metrics registry is opt-out with a `localhost:4318` default URL, so it is
  disabled by default in both apps; OTLP log export is disabled because the
  collector tails the container log; production also disables `OTEL_*`
  environment-variable mapping so telemetry cannot acquire an unchosen
  destination. Spans carry `service.version` and `deployment.environment`. The
  worker samples traces in full and the API at 0.1.
- `Stage` declares fourteen values; production emits thirteen. `GENERATE` has no
  producer, so no answer-generation latency is reported: generation runs in the
  application shell rather than the GraphRAG runtime, above an engine-neutral
  retrieval interface with a non-GraphRAG implementation, so wiring it is a
  boundary decision rather than a wiring one. Deletion and rebuild have no
  stage.
- `PARSE` and `CHUNK` are emitted by source ingestion under the same `jobId` the
  graph indexing stages use, so one upload reads as one operation across both
  processors. `PARSE` reports one source document in and the canonical blocks
  produced; `CHUNK` reports those blocks in and the chunks produced. The engine
  measures both windows itself and carries them out on
  `ProcessedSourceDocument`, because its caller makes one call and cannot see
  where parsing ended. A semantic chunker's failover to the recursive chunker is
  inside the chunk window, being time the document really spent chunking.
- `GLEAN` reports the second extraction round separately from the first. It is
  emitted once per indexing job when the profile enables gleaning, counting
  chunks eligible against chunks that completed a gleaning round, so a token
  guard declining the round is distinguishable from gleaning being configured
  off. Its duration is aggregate model time across concurrently gleaned chunks
  and is nested inside `EXTRACT`'s wall clock, so stage durations within one job
  are not additive. A profile with gleaning disabled emits nothing rather than a
  zero.
- Two backends ship: an OpenTelemetry span adapter and a Micrometer meter
  adapter. Neither displaces the other, and each has its own enable property;
  with both off the producers compose to `NO_OP`. Spans are sampled and show one
  request end to end; meters are unsampled and answer stage latency and failure
  rate. Meter tags are restricted to bounded enumerations — stage, outcome, cache
  status, and failure code on the failure counter only. Organization and
  operation identifiers and fingerprints are not tags; they stay on the span.
- Context assembly reports the token cost of one answer and the context the
  budget refused to carry: the rendered prompt size, the ceiling it was fitted
  to, a breakdown across system prompt, query, entity, relation and chunk, and
  how many merged contributions were evicted to make it fit. Counts only — a
  number cannot reconstruct the text it measured. No other stage carries the
  measurement, and its absence means the stage took none rather than measured
  zero. Deduplication is not eviction: merging two copies of one grounding
  reports nothing dropped, because the model still sees everything retrieval
  selected. Meters accumulate tokens by channel, summarise the prompt size so
  headroom is visible before truncation starts, and count evictions two ways —
  the number of contributions refused and the number of answers affected, which
  no sum of the first can recover. Tokens are not tagged by organization; that
  attribution stays on the span, which already carries the identifier.
- Extraction reports what the provider charged, separately from what retrieval
  budgeted. `EXTRACT` carries the first round's provider input and output token
  totals and `GLEAN` carries the second round's, so the second is never billed
  twice. Absent when the extractor reported nothing, because an unmeasured
  provider is not a free one. Meters keep input and output apart, since every
  provider prices them differently.
- The worker opens one span per claimed indexing job so every stage below it has
  a parent. Nothing else creates a trace there — the worker serves no request —
  so without it each stage span was a root of its own.
- Work fanned out to virtual threads carries the submitting thread's observation
  context, through `GraphRagTaskDecorator`. The domain modules that own those
  executors declare the port; the implementation ships with the telemetry
  adapters, and absent one the executors behave exactly as before. The
  concurrency itself is unchanged: same executors, same lifetimes, same bounds.
- `apps/mcp` starts a trace of its own and propagates `traceparent` on its API
  client, which is built through Spring Boot's `RestClientBuilderConfigurer`
  because a bare `RestClient.builder()` carries no customizer and therefore no
  propagation. Its OTLP defaults match the other services: metrics export off,
  log export off, environment-variable mapping off in production.
- Indexing and retrieval fan one stage event out to every registered
  `GraphRagEventSink`, so an application may observe the same stage through more
  than one backend. Sinks fail independently and emission never controls
  indexing or retrieval availability. Both composition sites wrap the composite
  in `FailureTolerantGraphRagEventSink`, which absorbs an emission failure while
  counting it, recording its type and logging once per change of kind, so a
  backend broken since startup is distinguishable from a quiet one. Only class
  names are recorded.
- Every exported span passes through `ExceptionSanitizingSpanExporter` before
  leaving the process. It keeps `exception.type` alone from each event and clears
  the status description, because Micrometer's bridge copies
  `throwable.getMessage()` into both and an OrgMemory exception can be raised
  holding query, evidence or provider-response text. It applies to all spans, not
  only GraphRAG ones, and has no toggle. Span attributes are not filtered: the one
  Spring AI content flag that reaches a span attribute is refused at startup by
  `ObservationContentBoundaryVerifier`, and the rest write to the application log,
  which no span filter would see.
- The OpenAI and Anthropic client packages are pinned above WARN in both apps,
  because their own logging concatenates the prompt or the response content into
  messages that Spring AI's `log-prompt`/`log-completion` settings do not govern.
  That pin is the default, not the boundary: `LOGGING_LEVEL_*` variables, system
  properties, logback configuration and a level on a more specific logger all
  outrank it. `ProviderLoggingBoundaryVerifier` asks each class holding such a
  call site whether WARN is enabled on its own logger and fails startup if any
  is, so the check is against the resolved level rather than any source of it.
  It has no disable property.
- Spring AI captures prompt, completion and tool-argument content only when a
  property turns it on, and names two families identically one layer apart:
  `spring.ai.chat.observations` governs the ChatModel and
  `spring.ai.chat.client.observations` the ChatClient that every call is built
  through. Both are declared false in `apps/api` and `apps/worker`. Declaring them
  is the default, not the boundary: `ObservationContentBoundaryVerifier` fails
  startup when any of the eight known flags resolves true, read from the
  `Environment` so an environment variable, system property, command-line argument
  or profile cannot outrank the file. It guards the image, tool-calling and
  vector-store families no path exercises today, because a dependency addition
  would otherwise step outside the boundary silently, and each application asserts
  against its own classpath metadata that Spring AI declares no content flag the
  list has missed. It has no disable property.

## Graph Explorer

The Sources UI reads the same permission-scoped published projection and never
creates node-owned ACLs or a permission-independent merged description. The
explorer fills the remaining shared app-shell canvas, wraps controls on narrow
screens, and opens selected entities or relations through the responsive split
layout without changing graph authorization or query state.

## Related Decisions

- [0005](../../decisions/0005-secure-java-graph-kernel.md)
- [0010](../../decisions/0010-internal-retrieval-strategies-one-hop-graph.md)
- [0011](../../decisions/0011-postgresql-multimodel-graph-projection.md)
- [0012](../../decisions/0012-stable-knowledge-assets-and-immutable-versions.md)
- [0013](../../decisions/0013-full-lightrag-semantic-port.md)
