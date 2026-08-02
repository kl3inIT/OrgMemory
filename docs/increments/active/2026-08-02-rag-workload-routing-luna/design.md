# RAG Workload Routing And Luna Evaluation

## Problem

OrgMemory already resolves separate workloads for Assistant answer generation,
keyword planning, and graph extraction, but only Assistant and Prompt routes are
editable in the administration surface. Keyword planning inherits the Assistant
deployment route, and route identity does not carry provider reasoning effort.
Consequently a small JSON planning call can use the same expensive reasoning
model and defaults as the final answer.

Graph extraction is different from both query-time workloads. Every graph job
pins an immutable `GraphProcessingProfile`, but that profile currently identifies
only the gateway, model, prompt, and algorithm settings. Adding a reasoning
option at model construction without adding it to the profile identity would let
the same persisted profile execute with different semantics.

## Reference evidence

- Pinned LightRAG `v1.5.4` defines independent `EXTRACT`, `KEYWORD`, and `QUERY`
  roles. It recommends fast non-thinking models for extraction and keyword
  planning and a stronger reasoning model for final answers:
  `tmp/upstream-lightrag-v1.5.4/docs/RoleSpecificLLMConfiguration.md:1-16`.
- The same guide supports role-specific provider options, including OpenAI
  reasoning effort, rather than one global value:
  `tmp/upstream-lightrag-v1.5.4/docs/RoleSpecificLLMConfiguration.md:46-80` and
  `:250-301`.
- The pinned Onyx reference disables reasoning for query expansion and short
  indexing summaries:
  `tmp/onyx/backend/onyx/secondary_llm_flows/query_expansion.py:137-142` and
  `tmp/onyx/backend/onyx/indexing/indexing_pipeline.py:119-122`.
- OpenAI's current GPT-5.6 guidance positions Luna for cost-sensitive,
  high-volume work and documents `none` through `max` reasoning effort. Spring
  AI 2.0.0 exposes `OpenAiChatOptions.builder().reasoningEffort(String)`.

## Decision under challenge

Introduce an explicitly OpenAI-specific reasoning-effort coordinate on chat
routes, with `NONE`, `LOW`, `MEDIUM`, `HIGH`, `XHIGH`, and `MAX`; absence means
the provider default and is omitted on the wire. OpenAI-compatible gateways must
declare support before a value can be selected. Native Anthropic routes reject
an OpenAI reasoning value rather than ignore it or invent a cross-provider
mapping.

Evaluate these candidate routes, but retain the current deployment defaults in
the infrastructure PR. Activation is a later configuration/release change only
after the relevant live gate passes:

| Workload | Model | Reasoning | Lifecycle |
| --- | --- | --- | --- |
| Assistant answer | `gpt-5.6-sol` | provider default | query-time |
| Keyword planning candidate | `gpt-5.6-luna` | `none` | query-time |
| Graph extraction candidate | `gpt-5.6-luna` | `none` | pinned at enqueue |

Make Assistant Answer and Keyword Planning resolve their selected organization
route at request time rather than capturing deployment models in the singleton
GraphRAG runtime. Make Keyword Planning organization-editable through the route
API only after this dispatch path and its cache identity are organization-aware.
Show Assistant Answer, Keyword Planning, and Graph Extraction together under
Admin -> Language Models -> RAG pipeline. Keep Graph Extraction read-only in
this increment: a generic organization override currently pins neither an
organization gateway-profile identity nor its future credential availability,
so it cannot safely guarantee execution of already queued work after another
route edit. Its card states that deployment changes affect only new jobs and do
not trigger reindexing.

Add reasoning effort to the immutable graph extraction profile through explicit
schema-version codecs. Restore and execute existing schema-v1 profiles with the
option absent while preserving their exact historical canonical form and SHA.
New jobs resolve a schema-v2 profile; completed or queued schema-v1 jobs keep
their pinned identity.

## Evaluation and activation

The candidate is not considered production-selected merely because the gateway
advertises its model id. Run a fixed representative corpus with a recorded
minimum sample count and repetitions against the current baseline and Luna.
Record structured-output validity, explicit keyword recall and graph-yield
tolerances, p95 latency, and provider-failure thresholds; retain redacted
aggregate evidence only.

Activation rules:

- Keyword Luna may activate when every response satisfies the keyword contract
  and has no material recall regression.
- Graph Luna may activate when every response satisfies extraction validation,
  entity/relation yield is acceptable, and latency improves without a higher
  provider-failure rate.
- If Graph Luna fails its gate, retain `gpt-5.4-mini` for Graph while still
  shipping the route/reasoning infrastructure and independently approved
  Keyword route.
- No automatic fallback occurs at runtime, because it would make one job's
  model identity and quality non-deterministic.

The fixed bilingual synthetic evaluation ran on ZM on 2026-08-02 with four
cases and three repetitions per route. Keyword Luna passed: 12/12 valid,
mean recall equal to the `gpt-5.6-sol` baseline at 0.875, zero provider
failures, and p95 latency 1,423.9 ms versus 2,611.9 ms. Graph Luna failed:
11/12 valid and p95 latency 4,124.2 ms versus 3,233.2 ms for
`gpt-5.4-mini`. Therefore Keyword may activate in the later configuration
change while Graph stays on `gpt-5.4-mini`. The redacted aggregate is recorded
in [evaluation-result.json](evaluation-result.json).

## Security and operational invariants

- Credentials stay encrypted and write-only; reasoning effort contains no
  secret or customer payload.
- Unsupported protocol/effort combinations fail closed.
- Route cache identity includes reasoning effort.
- Keyword cache identity continues to include the complete route.
- Graph profile fingerprint and canonical SHA include reasoning effort for new
  profiles; old canonical bytes remain restorable.
- Changing Answer or Keyword is immediate for later requests. Changing Graph
  deployment configuration affects only later enqueues; it neither mutates old
  jobs nor starts a rebuild.
- There is no Reindex action in this increment.

## Strongest counterargument

Keep Graph on `gpt-5.4-mini` and Keyword on the Assistant route. Luna support is
new, gateway compatibility and structured-output quality are not proven by a
catalog listing, and adding reasoning effort to a persisted graph profile
creates schema compatibility work. A smaller operational change would avoid
that risk.

The proposal accepts the compatibility work because silently omitting reasoning
from profile identity is a correctness bug once reasoning is configurable. It
still keeps independent activation gates, so a failed Luna evaluation does not
force a risky Graph switch.

## Out of scope

- Graph organization overrides or credential-lifecycle redesign.
- Corpus-wide or per-document reindex controls.
- Embedding-model changes.
- Automatic model fallback or provider retry across models.
- VLM, reranker, and summarization role routing.

## Architecture challenge

Status: **accepted with changes**. The review required request-time
organization routing, explicit schema-version codecs, OpenAI-specific capability
validation, read-only Graph enforcement, and evaluation before Luna activation.
See [challenge-verdict.md](challenge-verdict.md).
