# Secure LightRAG Grounding Delivery

## Goal

Deliver the complete permission-scoped LightRAG `MIX` context to the in-app
Assistant without introducing a second retrieval pipeline or weakening the
request authorization snapshot.

`graph-rag-core` remains the owner of query planning, graph expansion, fusion,
reranking, token budgeting, context rendering, and reference numbering.
The application shell owns current authorization resolution, evidence-closure
verification, audit, and streaming delivery. Spring AI remains a model-facing
adapter.

## Current Gap

`GraphRagKnowledgeRetrievalService` executes the LightRAG engine in `CONTEXT`
mode but consumes only chunk references and chunk scores. The selected entity
and relation context is discarded. `AssistantPromptFactory` then constructs a
second chunk-only prompt with a character budget unrelated to the engine's
token budget.

Consequently, GraphRAG influences chunk selection but the final answer does not
receive the complete context assembled by LightRAG.

## Design

The query result exposes provider-neutral structured selections in addition to
its rendered parity outputs:

- selected entities with per-contribution evidence provenance;
- selected relations with per-contribution evidence provenance;
- selected chunks with content, provenance, metadata, and retrieval signals;
- the existing authorization fingerprint, publication generation, references,
  and raw query trace.

The application shell derives the complete evidence closure from all selected
entity, relation, and chunk contributions. It verifies that closure against the
same OpenFGA model and canonical evidence state used by the request. A changed
scope or evidence set retries once and then fails closed.

After verification, the shell passes the verified structured subset back to the
pure-Java context renderer. It does not duplicate merge, token allocation,
context formatting, or citation numbering. The resulting
`VerifiedGroundingBundle` carries the rendered context, exactly matching
citations, snapshot metadata, token-budget usage, and a sanitized trace.

`AssistantService` sends that bundle through the existing provider-neutral
`ChatModelPort`. The Spring AI adapter uses `ChatClient.stream()` for final
generation. The production Assistant path does not use
`RetrievalAugmentationAdvisor`, `VectorStoreDocumentRetriever`, or any other
second retrieval path.

## Spring AI Boundary

This increment keeps the direct `ChatClient` call behind `ChatModelPort` and
does not add a custom advisor. The verified grounding is already rendered
before the model boundary, so an advisor would not add authorization,
retrieval, ranking, token-budget, or citation correctness.

Spring AI abstractions are used where they provide concrete adapter value:

- `ChatClient` and `ChatModel` for final model delivery;
- `EmbeddingModel` for provider-backed embeddings;
- native structured-output options and `BeanOutputConverter` for typed model
  effects;
- document readers behind the ingestion parser boundary;
- sanitized model and client observations.

The main retrieval path does not use Spring AI's modular RAG coordinator,
`VectorStore`, chat memory, semantic response cache, or document splitter.
Those abstractions either duplicate LightRAG ownership or do not carry the
tenant, authorization-generation, and evidence-snapshot identity required by
OrgMemory.

If a concrete cross-cutting requirement later justifies an advisor, it will be
a package-private `StreamAdvisor` inside `integrations:ai-openai-compatible`.
It may inject already-verified rendered grounding and sanitized observation
metadata only. It must not retrieve, authorize, rerank, allocate the token
budget, or number citations. A direct `StreamAdvisor` is preferred over
`BaseAdvisor` to avoid its unnecessary `boundedElastic` hop and to observe
stream cancellation/error through Reactor lifecycle operators.

## Reranking Policy

Reranking is an operator policy, not an end-user Assistant option.

- typed configuration controls enabled state, minimum score, and provider;
- reranking defaults off until a provider is configured;
- enabling it without a provider fails startup;
- a transient provider failure falls back to the already-authorized retrieval
  order and emits sanitized telemetry;
- the reranker route/configuration contributes to trace and cache identity;
- changing reranker model does not require re-embedding.

The current increment establishes the configuration and runtime boundary. It
does not add an Assistant UI toggle.

## Security Invariants

- Entity and relation descriptions are untrusted evidence, not instructions.
- Every contributing Knowledge Asset belongs to the verified evidence closure.
- No pre-merged description can survive after one of its contributions becomes
  unauthorized.
- Closure bounds fail closed rather than silently truncating authorization
  checks.
- Context and citations are rendered from one verified structured subset.
- Rechecks complete before the model stream is subscribed.
- Raw query text, keyword plans, embedding inputs, authorization fingerprints,
  document text, and actor data are not exported through telemetry.
- Product retrieval remains pinned to `MIX` plus `CONTEXT`; `BYPASS` cannot be
  selected by a request.

## Independent Review

Claude Fable 5 reviewed the proposal read-only on 2026-07-25 and scored it
8/10.

Its strongest counterargument was that rebuilding context in the application
shell would violate Decision 0013 by forking LightRAG rendering and causing
conformance tests to prove a different artifact from production. The accepted
decision is therefore:

- the shell owns authorization and the verified subset;
- `graph-rag-core` owns deterministic merge, token allocation, context
  rendering, and references for both conformance fixtures and production.

Fable also required contribution-level provenance rather than asset-level
labels on pre-merged descriptions, bounded closure verification, observable
rerank fallback, and tests proving that denied entity/relation contributions
cannot influence the final model prompt.

In a second, high-effort review, Fable scored adding a custom Spring AI advisor
in this increment 4/10. It found the strongest future benefit to be a standard
Spring AI interception point for observation metadata and advisor ordering.
The accepted trade-off is to defer that seam until a concrete cross-cutting
need exists, because the current adapter already performs the required model
delivery and an advisor would otherwise add an untyped context side channel
and a tempting location for retrieval or authorization logic to leak.

Rejected alternatives:

- passing the current rendered context directly without closing and rechecking
  every contributing evidence item;
- rebuilding graph context inside `AssistantPromptFactory`;
- introducing a Spring AI RAG advisor or vector retriever on the Assistant
  path;
- exposing rerank or LightRAG modes to ordinary Assistant users;
- mixing conversation persistence and retrieval-cache activation into this
  correctness slice.
