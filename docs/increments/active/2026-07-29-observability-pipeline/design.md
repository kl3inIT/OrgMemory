# Observability pipeline and payload boundary

Date: 2026-07-29

## Outcome

Give production a telemetry pipeline that reaches a real collector, stop the
payload paths that bypass the GraphRAG event boundary, and stop the exporter
failure that has been logging every minute since 2026-07-25.

## Production evidence

`orgmemory-api-1` and `orgmemory-worker-1` log every minute:

```text
i.m.registry.otlp.OtlpMeterRegistry : Failed to publish metrics to OTLP receiver
(context: url=http://localhost:4318/v1/metrics, resource-attributes={service.name=orgmemory-api})
```

The mechanism is a transitive default, not a misconfiguration:

- `apps/api/build.gradle.kts:23` and `apps/worker/build.gradle.kts:15` add
  `spring-boot-starter-opentelemetry`, which resolves
  `io.micrometer:micrometer-registry-otlp:1.17.0`.
- `OtlpMetricsExportAutoConfiguration` is `@ConditionalOnEnabledMetricsExport("otlp")`,
  and `OnMetricsExportEnabledCondition` treats exporters as enabled unless a
  property disables them. Metrics export is opt-out.
- Micrometer's `OtlpConfig.url()` defaults to `http://localhost:4318/v1/metrics`.
- No repository configuration sets an OTLP metrics endpoint.

OTLP **tracing** behaves oppositely and is not involved: `OtlpTracingConfigurations`
gates its connection-details bean on
`management.opentelemetry.tracing.export.otlp.endpoint`, so no endpoint means no
exporter and no log noise.

`localhost:4318` inside the API container is that container, so the export never
had a destination. The ZM host does run a Grafana stack —
`zeromail-alloy`, `zeromail-prometheus`, `zeromail-loki`, `zeromail-tempo`,
`zeromail-grafana`, all `Exited` since 2026-07-25 — but `zeromail-alloy` publishes
4317/4318 to the host loopback on network `zeromail-internal`, which OrgMemory
containers do not join. Its shutdown is coincident, not causal.

Readiness stayed `UP` throughout, which is correct: exporter delivery is not a
readiness signal.

## Payload boundary: challenge and finding

`docs/runbooks/graph-rag-production-hardening.md` states that telemetry never
carries query, prompt, completion, evidence text, document title/URI, embedding
values, actor identity, ACL subjects, or exception messages. A proposal to
replace that flat rule with three configurable tiers was put through an
independent architecture challenge (Codex, `gpt-5.6-sol`, ultra effort). The
challenge brief and full verdict were produced during the review; their
substance is recorded here because that scratch location is not versioned.

### Proposal

- Tier 0: current allowlist, unchanged, plus a counter for redactor near-misses.
- Tier 1: opt-in per deployment — infrastructure exception class and message
  through a pattern scrubber, hashed actor id.
- Tier 2: opt-in per organization, time-boxed, consent recorded — query and
  completion text, modelled on Onyx.
- Never at any tier: evidence/chunk text, document title/URI, ACL subjects,
  embedding values.

Supporting argument: no comparable open-source system chose "never". Dify, Onyx,
Sentry MCP, LibreChat and RAGFlow all permit content egress; OpenTelemetry's
GenAI conventions and Spring AI make it opt-in and default-off. The operational
cost of the strict rule was argued from the four-day undiagnosed OTLP warning.

### Strongest counterargument

An absolute rule is auditable in one line and cannot be misconfigured. Three
tiers create three states to audit, and one environment variable set wrong at
Tier 2 leaks customer content. The current design enforces the rule in the type
system, which configuration discipline cannot match.

### Repository evidence produced by the challenge

The review rejected the proposal and produced two verified findings that
outweigh it. Both were independently confirmed against the resolved sources.

**Provider libraries log raw prompts.** `log-prompt: false` disables Spring AI's
observation handlers, not the provider libraries' own logging:

```text
org/springframework/ai/openai/OpenAiChatModel.java:222
  logger.warn("No choices returned for prompt: " + prompt);
org/springframework/ai/anthropic/AnthropicChatModel.java:554
  logger.warn("No content blocks returned for prompt: " + prompt);
org/springframework/ai/anthropic/AnthropicChatModel.java:1219
  logger.warn("Failed to parse tool arguments JSON: " + argumentsJson, e);
```

OrgMemory prompts carry exactly the forbidden categories —
`SpringAiQueryAnswerModel` sends the query and grounded prompt,
`SpringAiExtractionModel` sends chunk content,
`SpringAiDescriptionSummaryModel` sends evidence-derived descriptions. Production
root logging is INFO, so WARN is emitted, and Compose retains stdout in
`json-file` logs on the host.

**Error spans carry exception events and messages.**
`io/micrometer/tracing/otel/bridge/OtelSpan.java:170-179` calls
`recordException(throwable)` and sets the status description to
`throwable.getMessage()`. The runbook's release gate — "confirm error spans
contain no exception event or stack trace" — cannot pass with the current wiring.

The review also corrected the proposal's own evidence: the runbook omits three
fields the record actually carries (`scopeFingerprint`, `cacheStatus`,
`occurredAt`); "bounded counts" is only a non-negativity check; and Onyx's
tracing configuration is environment-wide in `MULTI_TENANT` mode
(`provider_config.py:123-128`), so it is not a precedent for per-organization
consent.

## Decision

**Keep the payload-free policy. Reject Tier 1 and Tier 2.**

The governing principle is not that OrgMemory is unique — permission-aware
retrieval is not unique, and Onyx applies access filters too. It is:

> Query and completion content must not enter an ordinary observability store
> unless that store models the same authorization, retention, revocation,
> deletion, and operator-access semantics as the governed evidence and its
> derivatives.

A completion inherits the effective permissions of every contributing evidence
item. An organization-level consent flag is coarser than per-asset ACLs, consent
expiry cannot retract exported data, and a trace viewer cannot enforce current
revocation. That is sufficient to reject Tier 2. Tier 1 is rejected because an
arbitrary exception message is payload, and a pattern scrubber cannot prove that
customer text, provider bodies, filenames, SQL, URLs, or tool arguments were
removed.

The Tier 0 redactor counter is also rejected: the sink has no redactor because
payload cannot reach it. Counting near-misses would require building a raw
candidate path, weakening the boundary it was meant to observe.

**Rejected alternative:** enabling `spring.ai.chat.observations.log-prompt` and
exporting to Langfuse. Langfuse remains supported as an OTLP destination for
payload-free spans; it needs no adapter code, only an endpoint and headers.

**Accepted alternative for operability:** richer diagnostics belong on a separate
egress path with its own schema (sealed exception family, dependency alias,
status family, retry count, stack fingerprint rather than stack text),
credentials, retention, and access control — not inside `GraphRagEventSink`.

The policy is unchanged but the repository currently claims more than it
enforces. The bypasses above must close before any release describes the
deployment as payload-free.

Nothing has leaked yet. The project owner confirmed on 2026-07-29 that the ZM
deployment is a proof of concept carrying no real users and no customer data, so
the retained logs hold no exposure to scope and closing the bypasses is
preventive. That is a statement about today's deployment, not about the design:
the same code against a deployment holding real evidence would have been writing
customer text to the host since it shipped.

## What LightRAG instruments, and what it does not

The semantic port came from LightRAG, so its tracing was checked directly against
the pinned `v1.5.4` checkout rather than from recollection. An earlier reading in
this increment recorded that it has no observability at all; that was wrong.

Its only instrumentation is Langfuse, and only for the OpenAI-compatible binding.
`lightrag/llm/openai.py:44-66` swaps `openai.AsyncOpenAI` for
`langfuse.openai.AsyncOpenAI` when `LANGFUSE_PUBLIC_KEY` and
`LANGFUSE_SECRET_KEY` are both present at import time. That drop-in captures the
full request and response, so enabling it exports prompts and completions
verbatim. `git grep -ln langfuse -- 'lightrag/llm/*.py'` returns that one file:
Anthropic, Gemini, Bedrock, Ollama and the rest are not traced at all. There is
no masking, redaction or scrubbing anywhere in the tree, no OpenTelemetry, no
metrics, and no spans over LightRAG's own pipeline stages.

Two consequences.

First, the upstream does precisely what the decision above rejects, at LLM-call
granularity, and reaches it through a vendor drop-in rather than a designed
boundary. It strengthens rather than weakens the finding that
"no comparable system chose never" describes an absence of deliberation, not a
considered industry position. It is not a precedent to adopt.

Second, LightRAG is no source of stage coverage, because it instruments none.
Comparing the two pipelines instead surfaced a gap on this side:
`GraphRagEventSink.Stage` declares fourteen stages and production emits ten.
`PARSE`, `CHUNK`, `GLEAN` and `GENERATE` have no producer outside tests, so a
dashboard grouped by stage would show four permanently empty series and no
parsing, chunking, gleaning or answer-generation latency at all. Parsing and
chunking happen in the ingestion pipeline, which holds no sink; gleaning runs
inside extraction and is folded into it; generation is never reported, because
retrieval stops at `ASSEMBLE_CONTEXT`.

Deletion and rebuild are absent from both the enum and the producers. LightRAG
tracks that path through `pipeline_status`, and the hardening runbook requires a
deletion-then-rebuild drill, so it is the one stage the comparison says is
missing outright rather than merely unwired.

## Pipeline architecture

- **Push over pull.** OTLP export is already wired and the worker has no HTTP
  surface to scrape. Onyx needed a separate metrics server per Celery worker for
  exactly that reason. Accepted cost: a push failure is invisible to the
  monitoring system where a scrape failure is not; Compose healthchecks already
  cover liveness.
- **One collector.** Alloy receives metrics and traces. Langfuse was rejected as
  the primary destination because it does not accept JVM, HTTP, or database
  metrics and would require a second pipeline.
- **Explicit configuration over the conventional variable.** This paragraph
  originally said `OTEL_EXPORTER_OTLP_ENDPOINT` would carry the endpoint through
  Boot's environment post-processor. Production sets
  `management.opentelemetry.map-environment-variables: false` instead, because
  that mapping is a way for OrgMemory telemetry to acquire a destination nobody
  chose for it — and this host already runs an unrelated OpenTelemetry stack. The
  convention is worth less than knowing, from one file, where telemetry goes.
  OTLP log export is disabled explicitly because the collector already tails the
  `json-file` driver.
- **Silent when unconfigured.** No endpoint must mean no exporter and no
  recurring log line. Tracing already behaves this way; metrics must match.
- **Readiness stays independent of exporter health.**

## Trace completeness gaps

- Worker jobs have no root span, so every span in an indexing job is an orphan
  root correlated only by the `operation_id` attribute.
- `GraphRagKnowledgeRetrievalService:449` and `GraphIndexingProcessor:303` use
  raw `Executors.newVirtualThreadPerTaskExecutor()`. Stage events are emitted on
  the calling thread and keep their parent, but model calls inside those tasks
  detach. `io.micrometer:context-propagation:1.2.1` is already on the classpath.
- `apps/mcp` has no OpenTelemetry starter, so an agent trace begins at the API
  and its `RestClient` calls carry no `traceparent`.
- Resource attributes lack `service.version` and `deployment.environment`;
  image tags already carry the commit SHA.
- Sampling is 0.1 for both API and worker. A low-volume batch workload should be
  1.0.

## Exit proof

- Production emits no recurring exporter warning.
- A provider failure path produces no prompt in any log stream.
- One retrieval and one indexing job appear in the collector as connected traces
  with the expected stages, and a sanitized export shows no forbidden attribute.
- Stage latency is answerable from metrics at full coverage rather than from a
  10 % trace sample.
