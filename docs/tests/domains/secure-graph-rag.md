# Secure GraphRAG Test Evidence

Source: `components/graph-rag-core/src/test`,
`components/graph-rag-testkit/src/test`, `integrations/graph-rag-*/src/test`,
`integrations/observability/src/test`,
`apps/worker/src/test/java/com/orgmemory/worker/graph`,
`apps/api/src/test/java/com/orgmemory/api/observability`,
`apps/worker/src/test/java/com/orgmemory/worker/observability`,
`core/src/test/java/com/orgmemory/core/knowledge`, and
`apps/web/test/e2e`.

Reconciled: `2026-08-02-publication-lifecycle-coordinator (e1d84c91)` and
`2026-08-02-graph-extraction-model-route (aca7eede)`.

## Automated

- Graph-core unit tests cover extraction-result invariants, internal retrieval
  plans, deterministic ranking/interleaving, and context budgets.
- API, worker, and shared gateway-property tests prove graph extraction's
  `gpt-5.4-mini` default is independent from the Assistant model and remains
  explicitly overridable. Production Compose validation checks the same route.
- Graph-testkit security tests prove permission-scoped contribution,
  adjacency, degree, weight, seed, replacement, and removal behavior.
- Core traversal tests prove exact-snapshot validation before zero/empty
  returns, authorized seed normalization, multi-page completion, canonical UUID
  ordering, one global limit, cycles, disconnected nodes, seed permutations,
  model-level self-loop rejection, and fail-closed page/cursor/source contract
  violations.
- The reusable `GraphStoreConformance` suite runs against PostgreSQL, Neo4j,
  and OpenSearch and proves the same authorized traversal result plus exclusive
  relation-UUID paging across all production stores. The in-memory query double
  executes the same core coordinator rather than maintaining a second queue
  policy.
- `SpringAiEntityRelationExtractorTests` exercises Spring AI's actual structured
  response conversion with a deterministic fake `ChatModel`.
- Adapter tests cover valid mapping, prompt placement and limits, model options,
  provider mismatch, unsupported prompt version, unresolved relation endpoints,
  deduplicated keywords, and non-disclosure in the public exception message.
- No test calls a provider or requires an API key.
- PostgreSQL Testcontainers tests prove tenant isolation, ACL filtering before
  lexical/vector ranking and topology expansion, relation endpoint visibility,
  generation rollback denial, atomic replacement, embedding-profile safety,
  bounded batch partitioning, and replaceable vector index strategies.
- Shared-snapshot PostgreSQL tests run the reusable publication conformance
  suite and prove one authorized content/FTS/vector/graph snapshot, historical
  reads after delete, losing-batch non-disclosure, staged-row discard, durable
  receipts across store recreation, same-epoch exclusion, and higher-claim-
  epoch recovery of an unpermitted abandoned physical attempt.
- The shared publication conformance suite pins exact predecessor identity,
  logical-operation manifest/projection conflicts, register-before-stage, and
  store-issued discard authority across the in-memory, PostgreSQL, and
  OpenSearch publication stores.
- Lifecycle tests inject an acknowledgement failure after durable permit
  issuance and prove no cleanup occurs; recovery reuses the exact permit and
  fails if any projection restages or discards. Worker tests prove a concurrent
  loser retires its durable job permit before reverse-order staging cleanup,
  and that both caches invalidate before proof-based job completion.
- OpenSearch integration tests use an operations decorator that writes the real
  namespace head and then throws `Error`. A recreated store observes the exact
  head plus `COMMITTING` marker, skips staging, repairs `PUBLISHED`, and never
  receives discard authority. Existing copy-forward tests keep byte identity,
  page-to-bulk count/byte bounds, canonical target keys, distinct graph
  entity/relation units, and rejection of `_reindex`.
- Worker tests prove deterministic assembly, bounded extraction orchestration,
  immutable embedding-route enforcement, durable job creation for both upload
  and connector ingestion, and atomic contribution-plus-embedding publication
  rollback.
- The pinned PostgreSQL 18 image test proves real Apache AGE graph creation,
  idempotent replacement, content-free topology properties, authorized
  traversal, denied-edge exclusion, and revision removal.
- LightRAG runtime conformance tests prove contribution-level references,
  complete evidence closure, one final input-token budget, disabled reranker
  non-invocation, threshold behavior, bounded provider fallback, and one
  keyword-model call plus one embedding batch across repeated snapshot
  executions of one prepared query.
- Application tests prove entity/relation/chunk closure BatchCheck plus
  canonical recheck, authorization-model mismatch denial before rendering,
  request-scope revocation retry, exact Assistant handoff, and sanitized rerank
  fallback telemetry. Multi-space tests additionally prove one preparation,
  bounded concurrent snapshot execution, deterministic collection, fail-closed
  multi-space reranking and hashed snapshot-stage telemetry.
- Keyword-cache tests prove exact hit/miss isolation across organization,
  language, query strategy, route and query, plus trusted-keyword bypass.
- OpenTelemetry adapter tests prove the closed payload-free attribute set,
  original stage timing, cache status, model-route fingerprint and hashed scope
  fingerprint.
- Micrometer adapter tests prove the original stage duration is recorded, that
  stage, outcome and cache status are the timer's only dimensions, that an absent
  cache status becomes its own value rather than a dropped tag, that failures
  count by code while the code stays off the timer, and that no meter carries an
  organization, operation or fingerprint tag.
- Context token tests prove the breakdown reaches the event with the ceiling it
  was fitted to, that no other stage carries one, that meters accumulate tokens
  by channel and separate how much context was refused from how many answers
  were affected, and that neither truncation counter moves when the context
  fitted. The span assertion checks the exact key set and then that every added
  key is numeric — a substring guard would reject `query_tokens` for its name
  while a type check proves a count cannot hold the text it measured.
- Assembler budget tests prove a budget too small for the grounding reports what
  it evicted, that the reported number is exactly the gap between what retrieval
  selected and what the model saw, and that consolidating two copies of one
  grounding reports nothing dropped, so deduplication cannot be mistaken for
  truncation.
- The ingestion pipeline integration test proves `PARSE` and `CHUNK` reach a sink
  contributed as an ordinary bean — so the composition is exercised rather than
  the emit call alone — that chunking consumes exactly the blocks parsing
  produced, and that both carry the one job identifier. The engine unit test
  covers only the counts those events report: a timing assertion was tried and
  removed because on a small fixture, starting the chunk clock before parsing
  still satisfied every bound the test could state.
- The whole-export allowlist test executes the runbook's release gate, which had
  never been run. It walks the exported span — name, attributes, status, every
  event and event attribute, instrumentation scope and resource — on a success
  and a failure path, through the sanitizing exporter in the position it occupies
  in production, and asserts realistic payload text appears nowhere. A companion
  case exports an unmodelled attribute and asserts the gate fails, so the gate is
  not passing by never looking.
- Task-decorator tests prove a task on a fresh virtual thread sees the submitting
  thread's context, that an undecorated task does not — so the fixture would have
  caught a no-op — that capture happens at decoration rather than at execution,
  and that the scope closes so one job's context cannot leak into the next task.
- Provider-cost tests prove input and output tokens stay in separate series and
  that a stage calling no provider records no meter at all.
- Indexing telemetry tests prove the job emits extract, glean, merge, embed and
  publish in order; that `GLEAN` counts eligible chunks against chunks that
  completed a round, so a token guard declining one is visible; that its duration
  is the second round's time alone rather than both rounds'; and that a profile
  with gleaning disabled emits no `GLEAN` event at all.
- Observability wiring tests prove both backends are contributed together, that
  either toggle leaves the other in place, and that with both disabled the
  remaining sinks compose to `NO_OP`.
- Event-sink composition tests prove every registered backend receives the
  event, that one failing backend neither silences the others nor hides its own
  failure, that the first failure is the one propagated with later ones
  suppressed, that two backends raising one shared failure instance do not trip
  self-suppression, and that an application with no backend emits nothing.
- Failure-tolerance tests prove the observed work still succeeds while the
  backend is down, that every dropped event is counted, that a healthy backend
  still receives the event and reports nothing, and that only the failure's class
  name is retained when its message quotes the query it could not send.
- Span sanitization tests drive Micrometer's real `OtelSpan.error` through the
  SDK and prove the exported span keeps `exception.type` and the error status
  while losing the message, the stack trace and the status description, that the
  stripped attributes still report as dropped rather than as never recorded, that
  a successful span is untouched, and that an unmodelled event attribute does not
  pass. Wiring tests prove the auto-configuration stays discoverable, wraps every
  declared exporter, and is ordered ahead of Spring Boot's unwrapped collection.
- Provider-logging tests in each app read the shipped `application.yml` and fail
  if the pin is absent, or if any `.yml`/`.yaml` profile lowers
  `org.springframework.ai.openai` or `org.springframework.ai.anthropic` — or any
  ancestor or descendant of them — back to WARN. They assert that at least one
  profile file was scanned, so an unreadable resource directory cannot make them
  pass vacuously. They cover the shipped default only.
- The API context test starts the real application and proves no `OtlpMeterRegistry`
  bean exists without a configured collector, and that `service.version` and
  `deployment.environment` reach the OpenTelemetry `Resource`. The worker has no
  bootable context test, so `TelemetryExportDefaultsTests` asserts the same
  defaults against its shipped YAML with placeholders resolved as an environment
  that sets nothing would resolve them.
- Boundary-verifier tests set levels the way an operator override would, against
  the real provider classes and a real logging backend, and prove startup fails
  when a leak site is at WARN or below, that a class set back to WARN is caught
  even under a package pinned to ERROR, that the message names both the class and
  the override to look for, that the auto-configuration stays discoverable, and
  that the application context itself fails rather than starting.
- Observation-content tests give every guarded Spring AI content flag its own
  case, so a failure names the family that is open rather than reporting that one
  is. They prove a flag arriving as an environment variable is caught, which is
  what relaxed binding makes possible and a verifier reading a plain map would
  miss; that all eight open flags are reported together rather than the first;
  that a context enabling nothing still starts, so the failure case proves more
  than that the bean throws; and that the auto-configuration stays discoverable.
- Each app reads its own classpath's `spring-configuration-metadata.json` and
  fails when Spring AI declares a `spring.ai.*.observations.*` property the
  verifier does not guard, which is how a dependency bump would otherwise open a
  content path silently. The scan asserts that metadata was actually read, so an
  empty classpath cannot make it pass vacuously; removing one flag from the
  guarded list was confirmed to fail it by name. The same tests read the shipped
  `application.yml` and every profile file for a flag declared true.
- The boundary module's own tests moved with it and are unchanged, so the split is
  a move rather than a rewrite. What proves the split worked is a dependency fact
  rather than a test: `apps/mcp` resolves exactly one OrgMemory project on its
  runtime classpath, `integrations:observability`, and no longer resolves none.
- Storage adapter auto-configuration tests prove PostgreSQL, OpenSearch and
  Neo4j stay discoverable through their registration files, that PostgreSQL owns
  the canonical ports without an opt-in, that OpenSearch and Neo4j claim no port
  until enabled, and that an enabled Neo4j without a password fails startup.
  The OpenSearch wiring assertion runs against the container because its
  publication store creates an index while the bean is built.

## Verification

```powershell
.\gradlew.bat --no-daemon :integrations:graph-rag-spring-ai:test
.\gradlew.bat --no-daemon :integrations:graph-rag-postgres:test
.\gradlew.bat --no-daemon :apps:worker:test
.\gradlew.bat --no-daemon compileJava
.\gradlew.bat --no-daemon clean test
```

The graph-core runtime dependency report remains empty. The Spring AI adapter
depends on graph-core plus `spring-ai-client-chat`; it does not add a provider
starter or Spring Boot runtime.

## Remaining

Provider-backed evaluation and load/latency evidence remain operational gaps.
The deterministic suite requires no provider key and keeps restricted,
cross-tenant, stale-generation, and model-mismatched evidence out of the final
grounding.

The exact graph node/edge response and permission-negative metadata contract is
covered at the API/service layer; a focused real-browser graph rendering and
interaction test remains a gap.

No test asserts the whole telemetry export against the payload allowlist. Span
attributes, resource attributes, instrumentation-scope attributes and the log
stream are each unchecked; only the GraphRAG adapter's own attribute set, the
exception paths and the provider logger levels are covered. Until that test
exists, "payload-free" is enforced at the points listed above rather than proven
end to end.

`PostgresAuthorizedGraphSqlTests.graphVisibilityUsesTheLatestSealedCompleteAclAfterFreshnessExpiry`
pins ADR 0015 parity: GraphRAG requires the current sealed `COMPLETE` ACL but does
not add an expiry denial absent from canonical knowledge retrieval.
