# Secure GraphRAG Test Evidence

Source: `components/graph-rag-core/src/test`,
`components/graph-rag-testkit/src/test`, `integrations/graph-rag-*/src/test`,
`apps/worker/src/test/java/com/orgmemory/worker/graph`,
`core/src/test/java/com/orgmemory/core/knowledge`, and
`apps/web/test/e2e`.

Reconciled: `2026-07-29-observability-pipeline (f17256b)`.

## Automated

- Graph-core unit tests cover extraction-result invariants, internal retrieval
  plans, deterministic ranking/interleaving, and context budgets.
- Graph-testkit security tests prove permission-scoped contribution,
  adjacency, degree, weight, seed, replacement, and removal behavior.
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
  reads after delete, losing-batch non-disclosure, and staged-row discard.
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
- Boundary-verifier tests set levels the way an operator override would, against
  the real provider classes and a real logging backend, and prove startup fails
  when a leak site is at WARN or below, that a class set back to WARN is caught
  even under a package pinned to ERROR, that the message names both the class and
  the override to look for, that the auto-configuration stays discoverable, and
  that the application context itself fails rather than starting.
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
