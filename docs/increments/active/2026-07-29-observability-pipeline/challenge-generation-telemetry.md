# Architecture challenge: where assistant generation telemetry belongs

You are an independent architecture reviewer. Attack the proposal below rather than validate
it. `CLAUDE.md` requires an independent challenge before a domain-boundary decision is
implemented; this is that challenge. Verify every claim against the code — file paths are
given so you can check rather than trust.

## The gap

`GraphRagEventSink.Stage` declares fourteen stages. Production now emits eleven. `GENERATE`
has no producer, so no answer-generation latency, time to first token, or output-side
truncation (`finish_reason=length`) is reported anywhere. A user-visible slow answer is
currently invisible past `ASSEMBLE_CONTEXT`.

## Why this is not a wiring task

Generation does not happen inside the GraphRAG runtime, and that is deliberate.

- `QueryOutputMode` declares `ANSWER`
  (`components/graph-rag-core/.../query/QueryOutputMode.java`), so the LightRAG port *can*
  generate.
- `GraphRagRetrievalPolicy` pins `CONTEXT` instead
  (`core/src/main/java/com/orgmemory/core/knowledge/GraphRagRetrievalPolicy.java:52`).
  Generation was moved into the application shell so the shell can re-verify the complete
  evidence closure against the canonical ledger before anything reaches a model.
- `AssistantService` (`core/src/main/java/com/orgmemory/core/assistant/AssistantService.java`)
  depends on `PermissionAwareKnowledgeSearch` and `ChatModelPort`.
- `PermissionAwareKnowledgeSearch` is engine-neutral and has a second, non-GraphRAG
  implementation: `CanonicalHybridKnowledgeSearch`. Which one runs is a configuration switch,
  `orgmemory.assistant.retrieval-engine`
  (`apps/api/src/main/java/com/orgmemory/api/assistant/AssistantConfiguration.java:46`).
- `GraphRagEvent` requires a non-null `operationId`. Only
  `GraphRagKnowledgeRetrievalService` mints one, and it never escapes:
  `SecureKnowledgeSearchResult` carries a `requestId` string, evidence, and grounding.

## Proposal

Give the assistant turn its own observation surface — meters and a span emitted from the
assistant layer — rather than routing it through `GraphRagEventSink`. Correlate it to
retrieval by trace context rather than by a shared `operationId`. Leave `Stage.GENERATE`
unproduced and remove it from the enum, since the enum would then describe a pipeline
OrgMemory does not run.

Rationale: the alternative labels canonical-engine turns as GraphRAG stages, which is false,
or threads a GraphRAG operation identifier through an interface that has no such concept,
which inverts the dependency the engine-neutral interface exists to prevent.

## Strongest counterargument to the proposal

A second observation surface is a second thing to secure. `GraphRagEventSink` is not merely a
telemetry convenience — it is the enforcement point of the payload boundary. Its compact
constructor structurally rejects free text: fingerprints must match `[0-9a-f]{64}`,
`failureCode` must match `[a-z0-9_]{1,64}`, and no field can carry a prompt.

Generation is the single highest-risk stage for payload leakage in the entire system: it is
where prompts and completions actually exist. Creating a *new* telemetry path for exactly
that stage, outside the record that makes leakage structurally impossible, is how the
guarantee erodes — not by a decision to weaken it, but by a second path that nobody
remembered to constrain. `ExceptionSanitizingSpanExporter` would still catch exception text,
but nothing would stop a well-meaning `span.setAttribute("completion", …)`.

The counter-counter is that the boundary can be re-enforced by construction on the new
surface too. But that is a claim about future discipline, and the existing design chose
structure over discipline on purpose.

## What to decide

1. Does assistant generation telemetry go through `GraphRagEventSink`, a new payload-free
   port with the same structural guarantees, or Micrometer/OpenTelemetry directly?
2. If a new port: what makes its payload boundary structural rather than conventional?
3. Does `Stage.GENERATE` stay in the enum? An enum value nothing can emit is the same class
   of defect as an exporter that reports healthy while pushing to nowhere.
4. Does correlation between retrieval and generation come from trace context, or does
   `SecureKnowledgeSearchResult` gain an operation identifier? The second is a change to an
   engine-neutral interface for one engine's benefit.
5. `finish_reason=length` requires `ChatModelPort` to stop discarding `ChatResponse`
   (`integrations/ai-model-gateways/.../SpringAiChatModelAdapter.java:74` calls
   `.stream().content()`). Is a richer return type worth it, or should the adapter observe
   and report on its own?

## Scope note

Time to first token needs no port change — it is measurable by instrumenting the returned
`Flux` — but it still needs a destination, so it is blocked on question 1 alone.
