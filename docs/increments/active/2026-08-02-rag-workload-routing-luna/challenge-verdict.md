# Challenge Verdict - RAG Workload Routing And Luna

Date: 2026-08-02  
Commit reviewed: `da32eef9d6807ae97c4f1afd95e77b5688fedf28`  
Verdict: **accept with changes**

The fresh reviewer worked read-only against the written brief, repository, and
pinned references. No implementation began before this verdict.

## Committed recommendation

Implement organization-aware query-time routing for Assistant Answer and
Keyword Planning. Persist OpenAI reasoning effort as an explicitly
capability-gated route option, not a provider-neutral semantic. Add it to new
immutable Graph profiles while preserving exact executable schema-v1 support.
Expose Answer and Keyword as editable and Graph as server-enforced read-only.
Ship the infrastructure with current safe defaults, run fixed live evaluations,
then activate Luna independently for each passing workload.

## Must-fix items accepted

1. Replace startup-captured Assistant/Keyword models in the singleton GraphRAG
   engine with organization-aware request-time dispatch. Include the complete
   route, including OpenAI reasoning effort, in model and Keyword cache identity.
   Prove two-organization isolation and a route change in one running process.
2. Implement explicit Graph profile codecs: restore schema-v1 canonical bytes
   and SHA exactly, interpret absent effort as omitted/provider-default, emit v2
   only for new profiles, and execute queued v1 work after upgrade.
3. Name and validate reasoning as an OpenAI option. Omit an absent value on the
   wire. Reject a configured value for native Anthropic or an OpenAI-compatible
   gateway that has not declared support.
4. Keep Graph mutation denied at both service and UI boundaries. The override
   row pins a gateway profile, but queued Graph work does not pin that profile's
   protocol/endpoint/credential lifecycle.
5. Retain Keyword=`gpt-5.6-sol` and Graph=`gpt-5.4-mini` until an evaluation
   records a fixed corpus, repetitions, structured-output validity, recall/yield
   tolerances, p95 latency, and provider-failure thresholds. Activate each role
   in a later configuration/release change only if its gate passes.

## Evidence

- `apps/api/src/main/java/com/orgmemory/api/assistant/GraphRagRuntimeConfiguration.java:54-82`
  captures Assistant and Keyword deployment routes once at startup.
- `core/src/main/java/com/orgmemory/core/ai/AiRouteResolver.java:7-10` and
  `integrations/ai-model-gateways/src/main/java/com/orgmemory/integrations/ai/gateway/AiGatewayRegistry.java:37-48`
  already define organization-aware route lookup, but the current GraphRAG
  singleton does not use it.
- `core/src/main/java/com/orgmemory/core/knowledge/graph/GraphIndexJobQueue.java:62-70`
  binds Graph job identity to the profile SHA; worker execution reconstructs the
  route from that profile in
  `apps/worker/src/main/java/com/orgmemory/worker/graph/GraphIndexingProcessor.java:163-170`.
- `components/graph-rag-core/src/main/java/com/orgmemory/graphrag/processing/GraphProcessingProfile.java:31-132`
  currently accepts only one schema version and revalidates the canonical SHA.
- `core/src/main/resources/db/migration/V14__ai_model_control_plane.sql:92-121`
  stores organization route overrides, while
  `core/src/main/java/com/orgmemory/core/knowledge/graph/GraphProcessingProfileResolver.java:26-38`
  snapshots only deployment gateway/model settings for Graph.
- Pinned LightRAG treats reasoning as a provider-specific role option and warns
  that compatible endpoints may not support it:
  `tmp/upstream-lightrag-v1.5.4/docs/RoleSpecificLLMConfiguration.md:70-80,250-265,299-301`.

## Strongest counterargument

Use a typed protocol-options envelope rather than adding any universal effort
enum to `AiRoute`. Query workloads resolve the envelope per request; Graph
snapshots it into a versioned profile. This avoids false OpenAI/Anthropic
equivalence and leaves room for future provider-specific capabilities.

## Scope limit

This verdict does not approve Graph organization overrides, automatic fallback,
implicit reindex, or a Luna production default before the recorded live gate.
