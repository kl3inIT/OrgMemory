# Secure GraphRAG Test Evidence

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

## Verification

```powershell
.\gradlew.bat --no-daemon :integrations:graph-rag-spring-ai:test
.\gradlew.bat --no-daemon compileJava
.\gradlew.bat --no-daemon clean test
```

The graph-core runtime dependency report remains empty. The Spring AI adapter
depends on graph-core plus `spring-ai-client-chat`; it does not add a provider
starter or Spring Boot runtime.

## Remaining

PostgreSQL adapter tests must prove ACL filtering before aggregation and
ranking. Worker integration tests must prove idempotent replacement and
fail-closed handling of extraction/provider failures.
