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
- PostgreSQL Testcontainers tests prove tenant isolation, ACL filtering before
  lexical/vector ranking and topology expansion, relation endpoint visibility,
  generation rollback denial, atomic replacement, embedding-profile safety,
  bounded batch partitioning, and replaceable vector index strategies.
- The pinned PostgreSQL 18 image test proves real Apache AGE graph creation,
  idempotent replacement, content-free topology properties, authorized
  traversal, denied-edge exclusion, and revision removal.

## Verification

```powershell
.\gradlew.bat --no-daemon :integrations:graph-rag-spring-ai:test
.\gradlew.bat --no-daemon :integrations:graph-rag-postgres:test
.\gradlew.bat --no-daemon compileJava
.\gradlew.bat --no-daemon clean test
```

The graph-core runtime dependency report remains empty. The Spring AI adapter
depends on graph-core plus `spring-ai-client-chat`; it does not add a provider
starter or Spring Boot runtime.

## Remaining

Worker integration tests must prove idempotent replacement and fail-closed
handling of extraction/provider failures.
