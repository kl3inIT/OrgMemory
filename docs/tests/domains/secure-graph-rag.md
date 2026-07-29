# Secure GraphRAG Test Evidence

Source: `components/graph-rag-core/src/test`,
`components/graph-rag-testkit/src/test`, `integrations/graph-rag-*/src/test`,
`apps/worker/src/test/java/com/orgmemory/worker/graph`,
`core/src/test/java/com/orgmemory/core/knowledge`, and
`apps/web/test/e2e`.

Reconciled: `2026-07-29-graph-rag-observability-wiring (fd495d0)`.

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
- Event-sink composition tests prove every registered backend receives the
  event, that one failing backend neither silences the others nor hides its own
  failure, and that an application with no backend emits nothing.
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

`PostgresAuthorizedGraphSqlTests.graphVisibilityUsesTheLatestSealedCompleteAclAfterFreshnessExpiry`
pins ADR 0015 parity: GraphRAG requires the current sealed `COMPLETE` ACL but does
not add an expiry denial absent from canonical knowledge retrieval.
