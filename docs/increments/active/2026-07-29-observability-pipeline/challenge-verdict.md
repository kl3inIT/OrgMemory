# Architecture Verdict: Payload-Free Telemetry Policy for OrgMemory

Date: 2026-07-29  
Repository: `D:\OrgMemory`  
Review basis: commit `86ae6bc02a369471f2b77c87523eb6902988d1ba` on branch `fix/graph-rag-observability-wiring`

## Committed recommendation

Ship the **unchanged payload policy**.

Do not add the proposed Tier 1 exception-message and hashed-actor fields. Do not
add Tier 2 query/completion capture. Keep payload-bearing values structurally
outside `GraphRagEventSink`.

“Unchanged” here means unchanged policy, not an assertion that the present
application is already proven payload-free. The custom GraphRAG event adapter is
narrow, but Spring AI observations, Micrometer error spans, provider library
WARN paths, global OpenTelemetry resource data, and ordinary application logs
sit outside that type boundary. Those paths must be audited and hardened before
the deployment is described as globally payload-free.

For richer operational troubleshooting, use the fourth option described below:
a separate diagnostic path with its own data type, endpoint, credentials,
retention, access controls, and failure semantics.

## Verification performed

The verdict is based on direct inspection of the current repository, the
resolved dependency sources used by the build, the checked-in upstream
comparables under `tmp`, and focused tests.

The focused tests passed:

```text
.\gradlew.bat --no-daemon \
  :components:graph-rag-core:test \
  --tests "*GraphRagEventSinkTests" \
  :integrations:graph-rag-observability:test \
  --tests "*OpenTelemetryGraphRagEventSinkTests"
```

Gradle dependency inspection resolved:

```text
org.springframework.boot:spring-boot-starter-opentelemetry:4.1.0
io.micrometer:micrometer-registry-otlp:1.17.0
io.micrometer:micrometer-tracing-bridge-otel:1.7.0
org.springframework.ai:spring-ai-model:2.0.0
org.springframework.ai:spring-ai-openai:2.0.0
org.springframework.ai:spring-ai-anthropic:2.0.0
```

Context7 was attempted for current framework documentation but its monthly
quota was exhausted. Framework behavior was therefore checked against the
actual resolved source JARs plus official Spring AI, Spring Boot, Micrometer,
OpenTelemetry, and Sentry documentation.

## Claim-by-claim audit

### 1. Permission-aware evidence closure

**Challenge claim:** every contributing evidence chunk is authorization-scoped
and the complete evidence closure is re-verified against the canonical ledger
before anything reaches a model.

**Verdict:** substantially verified for the GraphRAG retrieval path.

Evidence:

- `core/src/main/java/com/orgmemory/core/knowledge/GraphRagKnowledgeRetrievalService.java:271-331`
  re-resolves the current scope, constructs the complete grounding closure,
  checks its size, verifies it through OpenFGA, canonical-rechecks it, compares
  the rechecked evidence identity, and only then calls the grounding renderer.
- `core/src/main/java/com/orgmemory/core/knowledge/GraphRagKnowledgeRetrievalService.java:666-726`
  performs the exact OpenFGA `BatchCheck` and canonical recheck.
- `core/src/main/java/com/orgmemory/core/knowledge/GraphRagKnowledgeRetrievalService.java:810-836`
  compares organization, asset, source revision, ACL snapshot, and projection
  generation identities.
- `core/src/main/java/com/orgmemory/core/knowledge/SecureKnowledgeRetrievalStore.java:92-212`
  applies organization, lifecycle, publication/model, sealed-ingestion ACL,
  current ACL, deny/allow, mapped source user/group, and classification filters.
- `core/src/test/java/com/orgmemory/core/knowledge/GraphRagKnowledgeRetrievalServiceTests.java:214-492`
  covers revocation, entity/relation/direct-chunk contributions, canonical
  recheck, model mismatch, and prevention of model egress on failure.
- `docs/guidelines/agent-safety.md:11-14` states that generated summaries,
  facts, and other derivatives inherit the permissions of every contributing
  evidence item.

The claim should still be scoped to this implemented retrieval path rather than
presented as a theorem about every possible future model invocation.

### 2. The runbook accurately describes the `GraphRagEvent` schema

**Challenge claim:** the application allowlist consists of operation and
organization UUIDs, stage/outcome, monotonic duration, bounded input/output
counts, an optional model-route fingerprint, and a bounded failure code.

**Verdict:** incomplete and partially wrong.

`components/graph-rag-core/src/main/java/com/orgmemory/graphrag/observability/GraphRagEventSink.java:63-75`
actually defines:

```text
operationId
organizationId
stage
outcome
duration
inputCount
outputCount
modelRouteFingerprint
scopeFingerprint
cacheStatus
failureCode
occurredAt
```

The runbook at
`docs/runbooks/graph-rag-production-hardening.md:23-29` omits
`scopeFingerprint`, `cacheStatus`, and the explicit occurrence time.

Specific corrections:

- Counts are only checked for non-negativity at
  `GraphRagEventSink.java:86-88`. There is no policy maximum, bucketing, or
  stage-specific bound. Java `int` is a representation bound, not the
  “bounded counts” policy described by the runbook.
- The record only rejects a negative `Duration` at
  `GraphRagEventSink.java:82-85`. Current producers generally calculate
  duration from `System.nanoTime()`, but the type itself cannot prove monotonic
  provenance.
- `failureCode` is matched against `[a-z0-9_]{1,64}` at
  `GraphRagEventSink.java:17-18,102-110`. This limits syntax and length, but it
  is not a closed error taxonomy. Any conforming arbitrary token can be passed.
- Failed outcomes require a code, but successful or cancelled outcomes are not
  forbidden from carrying one.
- Fingerprints are required to look like lowercase SHA-256 at
  `GraphRagEventSink.java:89-100`, but the type cannot prove what was hashed. A
  future producer could hash query text and create a stable equality oracle
  while still satisfying the constructor.

Current producer provenance appears intentional:

- `apps/api/src/main/java/com/orgmemory/api/graphrag/GraphRagRuntimeConfiguration.java:78-82`
  hashes the configured gateway/model route.
- `core/src/main/java/com/orgmemory/core/knowledge/GraphRagKnowledgeRetrievalService.java:621-629`
  hashes organization and knowledge-space scope.
- `core/src/main/java/com/orgmemory/core/knowledge/GraphRagKnowledgeRetrievalService.java:769-771`
  hashes the rerank provider.

That is a useful current convention, but it is not enforced by the event type.

### 3. The custom record and adapter structurally exclude direct payload text

**Challenge claim:** the compact constructor rejects anything outside the
allowlist, there is no field for free text, and the OpenTelemetry adapter can
only emit what the record permits.

**Verdict:** verified for direct fields on this one custom adapter, overstated as
an application-wide guarantee.

The custom path is narrow:

- `components/graph-rag-core/src/main/java/com/orgmemory/graphrag/observability/GraphRagEventSink.java:63-112`
  has no query, prompt, completion, evidence, title, URI, actor, ACL subject, or
  exception-message field.
- `integrations/graph-rag-observability/src/main/java/com/orgmemory/integrations/graphrag/observability/OpenTelemetryGraphRagEventSink.java:23-45`
  declares eleven explicit attribute keys.
- `OpenTelemetryGraphRagEventSink.java:54-91` emits only those event-derived
  attributes, span name, status, and timing. Failed custom events set ERROR
  status without calling `recordException`.
- `integrations/graph-rag-observability/src/test/java/com/orgmemory/integrations/graphrag/observability/OpenTelemetryGraphRagEventSinkTests.java:22-80`
  verifies the exact eleven-key attribute set for one manually constructed
  custom span.

The absolute claim is nevertheless too strong:

- `failureCode` remains an open string constrained only by character class and
  length.
- The OpenTelemetry SDK adds span identity, parent/trace context, resource
  attributes, instrumentation scope, timestamps, and status outside the record.
- Span processors and other instrumentation share the exporter and can add or
  export unrelated data.
- The custom test does not inspect resource attributes, span events, parent
  spans, Spring AI spans, HTTP spans, real application wiring, exporter
  behavior, or logs.
- The adapter starts a span with the current context rather than explicitly
  creating a root span, so it can be exported as part of a larger trace whose
  other spans have a different data policy.

The runbook itself recognizes that a whole-export test is still required:
`docs/runbooks/graph-rag-production-hardening.md:31-38` requires scanning every
attribute and event and confirming that no exception event or stack trace is
present. The narrow unit test does not satisfy that release gate.

### 4. Spring AI prompt and completion observation logging is disabled

**Challenge claim:** Spring AI prompt and completion observation logging is
explicitly disabled in API and worker configuration.

**Verdict:** verified as the repository default, but not structural and not
sufficient to prove application-wide payload-free egress.

Evidence:

- `apps/api/src/main/resources/application.yml:4-13` sets:

  ```yaml
  spring:
    ai:
      chat:
        observations:
          log-prompt: false
          log-completion: false
  ```

- `apps/worker/src/main/resources/application.yml:20-24` sets the same values.
- Spring AI 2.0's resolved auto-configuration creates the prompt/completion
  logging handlers only when the relevant property equals `true`.
- The official Spring AI observability documentation also describes prompt and
  completion content as sensitive and disabled by default:
  <https://docs.spring.io/spring-ai/reference/observability/index.html>.

However:

- External configuration can override the YAML values.
- The settings control Spring AI's content logging handlers, not every
  observation field, error event, provider-library WARN, or application log.
- The application programmatically supplies the real shared
  `ObservationRegistry` to both model implementations:
  - `integrations/ai-model-gateways/src/main/java/com/orgmemory/integrations/ai/gateway/openai/OpenAiCompatibleChatModelFactory.java:20-45`
  - `integrations/ai-model-gateways/src/main/java/com/orgmemory/integrations/ai/gateway/anthropic/AnthropicMessagesChatModelFactory.java:21-46`

Resolved Spring AI 2.0 source
`org/springframework/ai/chat/observation/DefaultChatModelObservationConvention.java:40-116`
therefore emits `gen_ai.client.operation` observations containing, among other
things:

- request and response model IDs;
- response IDs;
- stop sequences and tool names;
- temperature, token limits, penalties, and streaming status;
- finish reasons and token counts.

Those fields do not normally contain the full prompt, but raw model identifiers
already bypass the runbook's model-route fingerprint policy.

More seriously, resolved Micrometer tracing source
`io/micrometer/tracing/handler/TracingObservationHandler.java:133-137` calls
`Span.error(Throwable)` for observation errors. Resolved
`micrometer-tracing-bridge-otel:1.7.0` source
`io/micrometer/tracing/otel/bridge/OtelSpan.java:170-176` records the complete
exception event and sets the OpenTelemetry error-status description to
`throwable.getMessage()`.

That contradicts an application-wide reading of the runbook's requirement that
error spans contain no exception event or stack trace.

### 5. OrgMemory currently has no payload escape paths outside the custom sink

**Challenge implication:** the closed `GraphRagEvent` type makes the whole
application's observability payload-free.

**Verdict:** wrong.

In the resolved Spring AI 2.0 provider source:

- `org/springframework/ai/openai/OpenAiChatModel.java:219-224` logs
  `"No choices returned for prompt: " + prompt` at WARN.
- `org/springframework/ai/anthropic/AnthropicChatModel.java:551-556` logs
  `"No content blocks returned for prompt: " + prompt` at WARN.
- `AnthropicChatModel.java:1015-1019` logs unsupported content blocks.
- `AnthropicChatModel.java:1216-1221` logs the raw malformed tool-argument JSON
  plus the exception.

The application's prompts can contain exactly the categories forbidden by the
runbook:

- `integrations/graph-rag-spring-ai/src/main/java/com/orgmemory/integrations/graphrag/springai/SpringAiQueryAnswerModel.java:57-69`
  sends the raw query and verified grounded prompt.
- `integrations/graph-rag-spring-ai/src/main/java/com/orgmemory/integrations/graphrag/springai/SpringAiKeywordPlanningModel.java:35-44`
  sends a prompt containing the query.
- `integrations/graph-rag-spring-ai/src/main/java/com/orgmemory/integrations/graphrag/springai/SpringAiExtractionModel.java:47-61`
  sends chunk and extraction-conversation content.
- `integrations/graph-rag-spring-ai/src/main/java/com/orgmemory/integrations/graphrag/springai/SpringAiDescriptionSummaryModel.java:34-56`
  sends evidence-derived descriptions.

Production config permits those WARNs:

- `apps/api/src/main/resources/application-prod.yml:81-86` sets root logging to
  INFO by default.
- `apps/worker/src/main/resources/application-prod.yml:46-50` does the same.

Therefore `log-prompt:false` does not suppress these provider-library WARN
paths. The custom event type cannot protect a log statement that never touches
that type.

There is already an intentional separate diagnostic path in worker indexing:

- `apps/worker/src/main/java/com/orgmemory/worker/graph/GraphIndexingProcessor.java:202-265`
  emits a bounded machine failure code to `GraphRagEventSink`.
- `GraphIndexingProcessor.java:268-282` logs diagnostic detail separately.

That separation is architecturally useful, but the logging channel still needs
its own access, retention, and payload audit.

### 6. Exact counts and stable fingerprints are harmless

**Challenge implication:** counts and SHA-256 fingerprints are unconditionally
safe payload-free fields.

**Verdict:** overstated.

Exact counts can disclose organization or corpus topology, including authorized
asset, evidence-closure, chunk, entity, relation, or keyword cardinalities.
Stable unkeyed hashes allow cross-event correlation and dictionary testing when
the input domain is low entropy.

The current values are materially safer than text, but production policy should
consider:

- stage-specific upper bounds or buckets;
- domain-separated keyed hashes rather than bare SHA-256;
- explicit constructors for route and scope fingerprints so arbitrary producers
  cannot hash payload text;
- retention and access limits even for “payload-free” telemetry.

### 7. The comparable-systems table establishes an industry baseline

**Challenge claim:** every comparable system permits prompt/completion egress,
so OrgMemory is alone at “never.”

**Verdict:** not established. The table mixes unlike mechanisms and several
rows are overstated.

#### Dify

The cited issue <https://github.com/langgenius/dify/issues/19345> reports that a
user observed several telemetry domains and asks for documentation and opt-out
instructions. It does not demonstrate that full prompt and completion history
is exported by default. Configured Langfuse workflow tracing is different from
unconditional native telemetry.

The challenge's “Yes, by default” conclusion is unsupported by that citation.

#### Onyx

The main capture claim is verified:

- `tmp/onyx/backend/onyx/tracing/framework/span_data.py:89-146` defines
  generation span data containing input, output, and reasoning.
- `tmp/onyx/backend/onyx/tracing/langfuse_tracing_processor.py:295-321`
  sends input/output and other generation information.
- `tmp/onyx/backend/onyx/tracing/langfuse_tracing_processor.py:143-149`
  sends user and session IDs.
- `tmp/onyx/backend/onyx/tracing/masking.py:7-71` only handles a narrow set of
  private-key/authorization patterns and a 500,000-character limit. Normal
  prompt text survives.
- `tmp/onyx/backend/onyx/tracing/langfuse_tracing_processor.py:76-86` fails open:
  if masking throws, it logs a warning and returns the original unmasked value.
- Error status data at
  `tmp/onyx/backend/onyx/tracing/langfuse_tracing_processor.py:335-340` is not
  passed through the same masking helper.

Live reload is also real:

- `tmp/onyx/backend/onyx/tracing/dynamic_processor.py:104-159` reloads provider
  configuration.
- `tmp/onyx/backend/onyx/configs/app_configs.py:1618-1619` gives the cache a
  short TTL.

But the proposed “per-organization database setting modelled on Onyx” is wrong:

- `tmp/onyx/backend/onyx/tracing/provider_config.py:123-128` explicitly bypasses
  database configuration in `MULTI_TENANT` mode and uses environment-wide
  settings.
- `tmp/onyx/backend/onyx/server/manage/tracing/api.py:36-46` rejects tracing
  configuration management in multi-tenant mode.
- In non-multi-tenant mode, the database model holds one unique configuration
  row per provider rather than a SaaS organization-by-organization consent
  model.

Onyx is therefore evidence that live-reloaded capture and narrow masking exist.
It is not evidence that a per-organization Tier 2 is safe or already solved.

Onyx is also permission-aware:

- `tmp/onyx/backend/onyx/context/search/preprocessing/access_filters.py:8-22`
  applies user access filters to search.
- `tmp/onyx/backend/onyx/db/document_access.py:26-83` filters by public,
  email, and group ACL.

OrgMemory's full canonical closure recheck is stronger, but “permission-aware
retrieval is unique to OrgMemory” is not true.

#### Sentry MCP

The repository confirms conditional prompt/output capture, but the challenge
overstates it as unconditional and overstates the protection provided by its
scrubber.

- `tmp/upstream-sentry-mcp-20260726/TELEMETRY.md:258-265` says Node/stdio and
  Cloudflare telemetry are disabled when the DSN is absent.
- `tmp/upstream-sentry-mcp-20260726/packages/mcp-server/src/index.ts:279-302`
  explicitly enables Vercel AI input/output recording when Sentry is
  configured.
- `tmp/upstream-sentry-mcp-20260726/packages/mcp-core/src/telem/sentry.ts:7-28`
  has only three secret patterns and a recursion depth of twenty.
- `tmp/upstream-sentry-mcp-20260726/packages/mcp-core/src/telem/sentry.ts:34-117`
  recursively scrubs events and warns when it changes an event.

The custom hook is `beforeSend`; the source contains no `beforeSendSpan`.
Sentry's official documentation exposes `beforeSendSpan` as the span-specific
hook. Therefore the event scrubber cannot be treated as structural protection
for GenAI span input/output:
<https://docs.sentry.io/platforms/javascript/guides/tanstackstart-react/tracing/span-metrics/performance-metrics/>.

This is evidence that scrub visibility is useful, but also that a narrow
pattern scrubber is not a proof of absence.

#### LibreChat

The described backend RUM proxy validates the session and removes application
authorization headers before forwarding browser telemetry. That is a browser
RUM transport boundary, not proof that prompt/completion content is routinely
captured or a comparable design for ACL-derived model output.

#### OpenTelemetry GenAI semantic conventions

Content capture is commonly opt-in and disabled by instrumentation defaults,
but describing it as one universal semantic-convention environment variable is
too broad. Semantic conventions define names and meanings; individual
instrumentations decide which switches and defaults they implement. Official
OpenTelemetry material documents opt-in message-content capture, but it is not
a universal structural policy for every SDK and instrumentation:
<https://opentelemetry.io/blog/2024/otel-generative-ai/>.

#### Spring AI

The Spring AI row is substantially correct: prompt and completion content
logging is opt-in/default-off. However, it does not imply that model metadata,
exception events, status descriptions, or provider-library WARNs are absent.

#### RAGFlow

The inspected material supports the existence of a configured Langfuse
integration. It does not establish unconditional default prompt/completion
egress, nor does it establish governance equivalence with OrgMemory.

#### OrgMemory

The table heading must be qualified as “leaves the process through
observability.” Literally, prompts necessarily leave the application for the
configured model provider:

- `core/src/main/java/com/orgmemory/core/assistant/AssistantService.java:58-63`
  sends the verified request through `ChatModelPort`.

Even under the intended observability-only interpretation, “Never” is currently
false as an application-wide statement because the Spring AI observation,
Micrometer exception, and WARN paths described above bypass the custom sink.

### 8. The four-day OTLP warning demonstrates the operational cost of the payload policy

**Challenge claim:** the warning remained undiagnosed partly because telemetry
does not say anything useful, supporting Tier 1.

**Verdict:** the dependency/default mechanism is verified; the causal conclusion
is unsupported.

Verified mechanism:

- `apps/api/build.gradle.kts:23` and `apps/worker/build.gradle.kts:15` add
  `spring-boot-starter-opentelemetry`.
- Gradle resolves
  `micrometer-registry-otlp:1.17.0 <- spring-boot-starter-opentelemetry:4.1.0`.
- Resolved Spring Boot source
  `org/springframework/boot/micrometer/metrics/autoconfigure/export/otlp/OtlpMetricsExportAutoConfiguration.java:58-65`
  uses `@ConditionalOnEnabledMetricsExport("otlp")`.
- Resolved Spring Boot source
  `OnMetricsExportEnabledCondition.java:39-77` defaults exporters to enabled
  when no product-specific or default disable property exists.
- Resolved Micrometer source
  `io/micrometer/registry/otlp/OtlpConfig.java:54-77` defaults to
  `http://localhost:4318/v1/metrics`.
- Resolved Micrometer source
  `io/micrometer/registry/otlp/OtlpMeterRegistry.java:193-213` catches the
  publishing exception and calls `logger.warn(message, exception)`. The
  configuration context includes the destination URL and resource attributes.

Repository deployment configuration does not supply an OTLP metrics endpoint or
explicitly disable the exporter:

- `apps/api/src/main/resources/application-prod.yml:70-79`
- `apps/worker/src/main/resources/application-prod.yml:37-44`
- `infrastructure/deployment/compose.production.yaml:21-53`
- API and worker inherit the shared environment at
  `infrastructure/deployment/compose.production.yaml:329-333,376-380`.

Exception detail was therefore available in the logging call, and production
root INFO admits WARN. Docker's `json-file` logging configuration captures
stdout/stderr at `infrastructure/deployment/compose.production.yaml:3-14`.

Even the one-line warning identified:

- the failed subsystem: OTLP metrics publishing;
- the exact destination: localhost port 4318;
- the metrics endpoint path.

The repository provides a stronger explanation for delayed detection:

- `infrastructure/deployment/scripts/smoke-production.sh:31-147` checks health
  and functional HTTP behavior, but not exporter delivery or repeated log
  failures.
- `infrastructure/deployment/scripts/deploy.sh:213-226` declares deployment
  success after those smokes.
- The checked-in infrastructure contains no corresponding collector-delivery or
  log-alert verification.

The repository history makes four days temporally plausible because the starter
was introduced on 2026-07-25, but it does not prove continuous duration, who
observed the warning, or that a team belief that telemetry is useless caused the
delay.

Most importantly, the OTLP meter registry never passes through
`GraphRagEventSink`. Adding exception messages to `GraphRagEvent` would neither
enrich nor detect that metrics warning. Sending the diagnostic through the same
broken OTLP route would also be circular.

## Answers to the five questions

## 1. Is the operational-cost argument for Tier 1 sound?

No.

The incident demonstrates three operational problems:

1. a transitive exporter was enabled without an intentional endpoint;
2. deployment validation did not verify telemetry delivery or scan for exporter
   failures;
3. repeated WARNs lacked alert ownership.

It does not demonstrate that infrastructure exception detail was unavailable.
`OtlpMeterRegistry` logged the full caught exception, production log levels
admitted it, and the warning's own message contained the failing URL.

It also does not demonstrate a limitation of `GraphRagEventSink`, because the
warning originates in an independent Micrometer registry publisher. Tier 1
would not have changed that code path.

The appropriate fixes are:

- set `management.otlp.metrics.export.enabled=false` when no receiver exists;
- when enabled, require an explicit endpoint and fail deployment or startup if
  the endpoint is absent;
- add a smoke check that exports a known metric/span and verifies receipt;
- alert on repeated exporter WARNs and dropped-signal indicators;
- define responsibility for collector and exporter health.

There is a separate real issue: GraphRAG event producers often catch and ignore
sink failures:

- `core/src/main/java/com/orgmemory/core/knowledge/GraphRagKnowledgeRetrievalService.java:149-172,565-610,630-646,772-788`
- `apps/worker/src/main/java/com/orgmemory/worker/graph/GraphIndexingProcessor.java:249-265`

That should produce a local payload-free health counter or bounded log signal.
It still does not justify putting arbitrary exception messages into the remote
GraphRAG telemetry type.

## 2. Does ACL-scoped evidence justify diverging from every comparable system?

The argument is partly motivated and partly sound.

The motivated part is the assertion of uniqueness:

- permission-aware retrieval is not unique to OrgMemory;
- the comparable table does not establish that every peer exports payload by
  default;
- the rows describe different mechanisms and threat models;
- competitor behavior would not by itself establish a safe authorization or
  persistence design.

The sound part is the local authorization consequence:

- a query can contain newly supplied confidential material that is not yet a
  governed Knowledge Asset;
- a completion can quote, combine, or paraphrase material from several assets;
- the completion inherits the effective permissions of every contributor;
- an organization-level consent flag is coarser than per-asset and current
  source ACLs;
- consent expiry does not retract data already exported to a telemetry backend;
- an ordinary trace viewer cannot enforce current source revocation, deletion,
  retention, evidence-closure authorization, or operator access.

Tier 2 would therefore create a second persistence/publication plane whose
authorization semantics do not match the plane that allowed the model request.

The defensible principle is not “OrgMemory is unique, therefore content must
never exist anywhere.” It is:

> Query and completion content must not enter an ordinary observability store
> unless that store models the same authorization, retention, revocation,
> deletion, and operator-access semantics as the governed evidence and its
> derivatives.

Under the proposed design, it does not. That is enough to reject Tier 2.

## 3. If tiering is adopted, what failure is most likely, and what structural control prevents it?

The most likely failure is **tier bleed caused by stale or missing tenant
context**.

Examples:

- a shared/global observation handler sees a Tier 2 flag and captures another
  organization's request;
- a cached organization setting remains enabled after consent expires;
- asynchronous work loses the organization identity but retains capture state;
- a database setting changes while a trace is in flight;
- a global environment override silently wins over the database;
- a payload-bearing event enters the ordinary Tier 0 exporter;
- the telemetry backend retains payload after the capture lease ends;
- a scrubber misses content in a nested exception, provider body, URL, filename,
  SQL message, tool argument, alternate encoding, or unknown secret format;
- the scrubber itself fails and returns the original data.

This risk is especially high in the current application because:

- OpenAI and Anthropic model factories share the application
  `ObservationRegistry`;
- GraphRAG model construction and some model invocations do not consistently
  carry an organization-bound capture capability;
- the proposed Onyx precedent is global in multi-tenant mode, not per
  organization.

If tiering were nevertheless adopted, the structural control must be a
physically and logically distinct payload capability:

1. `GraphRagEvent` remains incapable of carrying content.
2. Payload capture uses a different sealed type and API.
3. Capture requires a non-forgeable, organization- and operation-bound
   capability containing consent version and expiry.
4. The capability is revalidated immediately before capture and again before
   egress; absent or stale context fails closed.
5. No environment variable or process-global Boolean may enable tenant payload
   capture.
6. The payload path uses a separate process or exporter, endpoint, credentials,
   network policy, queue, encryption key, storage, and RBAC.
7. The destination enforces hard TTL, deletion, tenant partitioning, access
   audit, and lease expiry.
8. Completion capture additionally carries the evidence-closure identity and
   enforces current authorization for every viewer.

For Tier 1 specifically, arbitrary exception messages should not be admitted at
all. Use a closed or sealed infrastructure taxonomy with safe structured fields
such as dependency alias, status family, timeout, retry count, errno, and stack
fingerprint. Pattern scrubbing is defense in depth, not a structural
authorization or data-absence control.

The proposed Tier 0 redactor counter also needs correction. The existing sink
does not receive payload and has no redactor. To count rejected near-misses, the
system would first need to create a raw candidate data path, weakening the
current type boundary. Prefer:

- closed construction APIs;
- semantic tests for every permitted field;
- whole-export allowlist/denylist tests;
- collector-side defense-in-depth filtering;
- local counters for invalid event construction or rejected attributes, without
  carrying the rejected value.

## 4. Is there a fourth option?

Yes. Keep the GraphRAG OTLP sink payload-free and create a separate,
deployment-controlled diagnostics path.

The ordinary remote telemetry path should retain:

- operation and organization identifiers under the approved identity policy;
- stage and outcome;
- bounded or bucketed counts;
- duration;
- closed failure codes;
- safe route/scope correlation values;
- exporter health counters.

The diagnostic path should have a different schema and governance boundary. Its
default structured envelope can contain:

- sealed exception class or error family;
- subsystem and dependency alias;
- status-code family or errno;
- timeout, retryability, and attempt count;
- deployment or build identity;
- bounded cause-chain taxonomy;
- stack fingerprint, not stack text.

It should use:

- different endpoint and credentials;
- separate network egress policy;
- short retention;
- restricted operator access;
- explicit access audit;
- encryption;
- independent availability and failure alerts.

Full stack details can remain in protected local/deployment logs where justified,
provided those logs receive their own payload audit and retention controls.
Existing worker code already separates the machine-code event from diagnostic
logging in `GraphIndexingProcessor.java:221-282`.

If a rare support case truly requires query, completion, or evidence-derived
content, it should not be “Tier 2 telemetry.” It should be an explicit,
single-operation support capture represented as a governed derived resource:

- organization- and operation-bound;
- authorized under OpenFGA or an equivalent current-policy check;
- associated with the evidence closure;
- encrypted and stored in a dedicated support store;
- accessible only to approved support principals;
- fully audited;
- subject to hard TTL and verified deletion.

That is a new governed product capability and should receive its own
authorization, persistence, and publication challenge. It is not a telemetry
configuration flag.

## 5. Which variant should ship?

Ship **unchanged** among the offered variants.

Reject all three tiers because Tier 2's organization-level consent cannot model
asset-level ACL, evidence inheritance, revocation, deletion, retention, or
operator access.

Reject Tier 0 + Tier 1 because arbitrary exception messages remain payload. A
pattern scrubber cannot prove that unknown customer text, credentials, provider
bodies, filenames, SQL, URLs, tool arguments, nested causes, or alternative
encodings were removed. The operational incident cited in the proposal also
would not have been improved by Tier 1.

Retain the current payload-free GraphRAG event policy, but do not ship a release
claiming global enforcement until the following implementation gaps are closed:

1. Explicitly disable OTLP metrics when no receiver is configured and require a
   valid endpoint when enabled.
2. Audit or suppress Spring AI's standard model observations when they violate
   the GraphRAG allowlist, including raw model IDs and exception events.
3. Prevent provider-library WARN paths from logging raw prompts, response
   blocks, or tool arguments.
4. Replace open-string `failureCode` with a closed taxonomy.
5. Make route/scope fingerprint construction domain-specific and preferably
   keyed/domain-separated.
6. Bound or bucket counts according to stage and disclosure risk.
7. Add a payload-free local health signal when custom event emission fails.
8. Add a real application/exporter integration test that scans:
   - every span and event;
   - resource and instrumentation-scope attributes;
   - Spring AI and HTTP spans;
   - success and failure paths;
   - relevant stdout/stderr logs;
   - actual collector output.
9. Retain sanitized export evidence before claiming the runbook's payload-free
   production gate has passed.

## Final decision statement

The operational incident does not justify weakening the GraphRAG telemetry
boundary. The custom event type is valuable precisely because payload cannot be
added through ordinary configuration, but the repository currently overstates
how far that protection extends.

The selected architecture is:

> Keep `GraphRagEventSink` payload-free and reject Tier 1 and Tier 2. Close the
> existing Spring AI, Micrometer, and logging bypasses. Put richer operational
> diagnostics on a separate, access-controlled egress path. Treat any future
> content capture as a governed, short-lived derived resource—not telemetry.

