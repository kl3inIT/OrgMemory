# GraphRAG Spring AI Extraction Adapter

## Outcome

Add a replaceable Spring AI adapter for the framework-neutral
`EntityRelationExtractor` contract, with versioned prompts and deterministic
contract tests before any worker or database wiring.

## Scope

- Add `integrations/graph-rag-spring-ai`.
- Use Spring AI 2.0.0 `ChatClient` structured output to produce the graph-core
  extraction result.
- Apply the model from the immutable extraction profile and require the
  configured provider and prompt version to match that profile.
- Treat source text as untrusted evidence and keep it out of the system
  instruction.
- Map response-local entity references, binary relations, keywords,
  orientation, descriptions, and confidence into graph-core records.
- Fail closed on malformed output, unresolved relation endpoints, profile
  mismatch, or unsupported prompt versions.
- Test through a deterministic fake `ChatModel`; no provider credentials or
  live model calls are used.

## Boundaries

This increment does not add automatic Spring configuration, AI gateway routing,
retries, gleaning, tokenization, worker execution, persistence, PostgreSQL graph
tables, or runtime endpoints. Those depend on an accepted adapter contract.

## LightRAG Reference

The extraction semantics are informed by LightRAG `v1.5.4` at commit
`9a45b64c2ee25b1d806e90db926a8af37480bb16`: structured JSON extraction,
meaningful entities, binary relation decomposition, undirected-by-default
orientation, consistent names, per-response limits, and language preservation.

OrgMemory intentionally adds response-local references, confidence, strict
profile provenance, and prompt-injection boundaries. It does not copy
LightRAG's provider wiring, continuation loop, global merged descriptions, or
delimiter protocol.

## Acceptance

- The adapter compiles against Spring AI 2.0.0 without adding Spring to
  `graph-rag-core`.
- Prompt/model/provider provenance cannot silently diverge from the request.
- Valid structured output maps to graph-core records.
- Malformed or unauthorized provenance fails before projection writes.
- Focused tests and the full Gradle suite pass without network calls.
