# Secure GraphRAG

## Current Contract

- `graph-rag-core` is pure Java and owns canonical graph identity, immutable
  evidence contributions, authorization-scoped read ports, atomic revision
  replacement, internal retrieval strategies, and context-budget rules.
- Canonical entities and relations do not contain merged descriptions.
  Descriptions, keywords, confidence, source revision, chunk, Knowledge Asset,
  ACL, projection, model, prompt, and extraction time remain on contributions.
- Every graph read requires an `AuthorizedGraphScope`; ranking, adjacency,
  degree, weight, aggregation, and citations can use only visible
  contributions.
- `SECURE_MIX` is the product default. Strategy selection remains internal.

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

## Not Implemented

Worker execution, retries/gleaning, token-aware extraction chunking, PostgreSQL
graph storage, entity canonicalization across chunks, runtime graph retrieval,
and the graph explorer remain separate increments.
