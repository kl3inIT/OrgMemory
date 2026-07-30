# Observability pipeline plan

## 0. Close the payload bypasses — code done, production evidence outstanding

Highest priority. These are live paths, independent of the pipeline work.

- [x] Pin `org.springframework.ai.openai` and `org.springframework.ai.anthropic`
      logger levels to `ERROR`. Done in the base configuration of both apps
      rather than the production profile, because a development database holds
      real uploaded documents too, and without an environment override, because
      the boundary is not a per-deployment setting. `ProviderLoggingBoundaryTests`
      in each app reads the shipped YAML and fails if a profile lowers either
      package. A fourth site was found during the sweep,
      `AnthropicChatModel:1018`, which logs a response content block.
- [x] Search retained production logs for those WARN signatures. Not required:
      the project owner confirmed on 2026-07-29 that the ZM deployment is a proof
      of concept with no real users and no customer data, so there is no
      historical exposure to scope and the fix is preventive rather than remedial.
      If that ever stops being true before the fix ships to a deployment holding
      real data, the search becomes a data-incident question again.
- [x] Decide how `OtelSpan.error` is handled. Suppressed, not tolerated:
      `ExceptionSanitizingSpanExporter` drops every event attribute except
      `exception.type` and clears the status description, as the last gate before
      egress. The runbook's wording still needs the amendment in phase 5, because
      the event itself now survives with its type.
- [x] Add a payload-free health signal for swallowed `GraphRagEventSink`
      failures. `FailureTolerantGraphRagEventSink` counts them, records the
      failure type and logs once per change of kind. Publishing that count as a
      metric belongs with the Micrometer sink in phase 2; until then the signal
      is the log line.

## 1. Silence the unconfigured exporter — done

- [x] `management.otlp.metrics.export.enabled` defaults to false. This is the
      change that stops the production symptom.
- [x] `management.logging.export.otlp.enabled: false` — the collector tails
      `json-file`. Precautionary: that exporter has the same failure mode.
- [x] `management.opentelemetry.map-environment-variables: false` in production.
      This contradicts the design's "standard configuration" paragraph, which
      assumed `OTEL_EXPORTER_OTLP_ENDPOINT` would carry the endpoint; the design
      now records why explicit configuration won.
- [x] Declare `spring.ai.chat.observations.include-error-logging: false`
      explicitly. It was already the framework default.
- [x] Add `service.version` and `deployment.environment` resource attributes.
- [x] Worker tracing sampling to 1.0; API stays at 0.1.

Proof: the API context test starts the real application and asserts no
`OtlpMeterRegistry` bean exists and that both resource attributes reach the
OpenTelemetry `Resource`. The worker has no bootable context test, so its
defaults are asserted against the shipped YAML with placeholders resolved as an
environment that sets nothing would resolve them.

Gate: `:apps:api:test --tests '*OrgMemoryApiContextLoadTests'`,
`:apps:worker:test --tests '*TelemetryExportDefaultsTests'`, `:core:test`.

Verified in production on 2026-07-29. `88c35cc` deployed to ZM; four minutes
after restart both `orgmemory-api-1` and `orgmemory-worker-1` reported zero
occurrences of `Failed to publish metrics` and zero mentions of `4318`, where the
previous behaviour produced one per service per minute. No ERROR lines and no
provider prompt-leak signatures either, so `ProviderLoggingBoundaryVerifier` also
passed against the real configuration rather than only under test.

The same check found what the code change alone had missed: neither
`ORGMEMORY_SERVICE_VERSION` nor `ORGMEMORY_DEPLOYMENT_ENVIRONMENT` was set on the
containers, so the attributes carried their local defaults and labelled
production `deployment.environment.name=local`. A misleading label is worse than
an absent one. `deploy.sh` now pins `ORGMEMORY_SERVICE_VERSION` to the released
commit in the same rewrite that pins the image tags, so the reported version
cannot drift from the running image, and the production compose sets the
environment explicitly.

That fix could not be deployed on its own: the delivery pipeline cannot deploy an
infrastructure-only commit, which is recorded against the CI/CD increment. It
reached the server on 2026-07-30 riding with `b4ea630`, the first later commit to
change application code. Verified there: both containers run
`sha-b4ea630…`, `ORGMEMORY_DEPLOYMENT_ENVIRONMENT=production`, and
`ORGMEMORY_SERVICE_VERSION` holds exactly the commit of the running image, which
is the property the single-rewrite change existed to guarantee. Phase 1's
exporter silence held across the restart: zero publish failures, zero mentions of
`4318`, zero ERROR lines and no provider prompt-leak signatures in either
service, and the startup boundary verifier raised nothing against the real
configuration.

## 2. Metrics that answer stage latency — partly done

Depends on the composite sink merged in PR #132.

- [x] Remove `@ConditionalOnMissingBean(GraphRagEventSink.class)` from
      `GraphRagObservabilityAutoConfiguration`; a sink is not an exclusive port.
      Each backend now has its own `@ConditionalOnProperty` toggle, and the class
      is ordered after the registries so `@ConditionalOnBean` can see them.
- [x] Add `MicrometerGraphRagEventSink` — a timer by stage, outcome and cache
      status, counters for consumed and produced work, and a failure counter by
      code. Metrics are not sampled, so stage p95 becomes answerable at full
      coverage.
- [x] Tests: both sinks enabled receive the event; either toggle leaves the
      other; both disabled compose to `NO_OP`.
- [x] Export `ContextTokenUsage`, which core already computes and nothing
      publishes. The event carries the rendered prompt size, the ceiling it was
      fitted to and the per-channel breakdown; meters accumulate tokens by
      channel and summarise the prompt size, so headroom is readable before
      truncation starts rather than after.
- [x] Report input-side truncation. The assembler evicts contributions to fit
      the budget in two places — the per-channel allocator and the total-budget
      loop — and neither reported anything, so an answer cut down to fit looked
      exactly like a whole one. `PreparedGrounding.droppedContributions` now
      counts both, measured after merging so deduplication is not mistaken for
      eviction, and meters count both how much context was refused and how many
      answers were affected.
- [ ] Counter for `finish_reason=length`. This is output-side truncation and the
      chat port cannot see it: `ChatModelPort` streams `Flux<String>` and the
      adapter calls `.stream().content()`, which discards the `ChatResponse`
      holding the finish reason. It lands with `GENERATE` below, where the port
      change is already required.
- [x] Separate `GLEAN` from extraction. The extractor already recorded per-round
      metrics and a gleaning outcome and the worker already held them on every
      `ExtractedChunk`; nothing published either, so gleaning working and
      gleaning silently declined by the token guard were the same picture. The
      stage counts eligible chunks against completed rounds and carries the
      second round's model time alone. Nothing is emitted when the profile
      disables gleaning, because a zero would claim a round never configured to
      run.
- [ ] `PARSE` and `CHUNK` need a sink in the ingestion pipeline, which holds
      none today.
- [ ] Deletion and rebuild: missing from the enum entirely, and the runbook
      requires a drill for it. Decide separately.
- [ ] Extraction cost. `ExtractionRoundMetrics` already carries
      `providerInputTokens` and `providerOutputTokens` per round and nothing
      publishes them, so ingestion spend is invisible while retrieval spend is
      not. Found while wiring `GLEAN`. The `TokenUsage` record added for context
      assembly does not fit — its channels are retrieval's — so this needs its
      own shape rather than a forced reuse.

### `GENERATE` and time to first token — blocked on a boundary decision

Both need the same answer and neither is a wiring task, so they are recorded
here rather than attempted.

Generation does not happen inside the GraphRAG runtime. `QueryOutputMode.ANSWER`
exists in the port, but `GraphRagRetrievalPolicy` pins `CONTEXT`
(`core/.../GraphRagRetrievalPolicy.java:52`) and the application shell generates,
so it can re-verify the evidence closure before delivery. `AssistantService`
depends on `PermissionAwareKnowledgeSearch`, which has a second, non-GraphRAG
implementation in `CanonicalHybridKnowledgeSearch`, and on `ChatModelPort`.
`GraphRagEvent` requires a non-null `operationId` that only
`GraphRagKnowledgeRetrievalService` mints and that never leaves it —
`SecureKnowledgeSearchResult` carries a `requestId` string and nothing else.

So emitting `Stage.GENERATE` from the assistant would either label
canonical-engine turns as GraphRAG stages, or require threading a GraphRAG
operation identifier through an engine-neutral interface that has no such
concept. The alternative — a separate observation surface for the assistant turn
— leaves `Stage.GENERATE` permanently unproduced, which is the gap this item
exists to close.

`CLAUDE.md` requires an independent architecture challenge before a domain
boundary decision is implemented. `finish_reason=length` needs the same answer
plus a `ChatModelPort` change, because the port streams `Flux<String>` and
`SpringAiChatModelAdapter` calls `.stream().content()`, discarding the
`ChatResponse`. Time to first token needs no port change but still needs a
destination.

Proposed for challenge, with its strongest counterargument, in
`challenge-generation-telemetry.md`.

Cardinality decision, recorded because it is easier to add a tag than to remove
one from a series that already exists: organization and operation identifiers,
fingerprints and durations are not tags. Each would grow the stored series count
with tenants or requests. They stay on the span, where each is one record rather
than a permanent series. `failureCode` tags only the failure counter, never the
timer, because the port bounds its shape and not its set of values.

The design's "tokens by organization" dashboard is settled by the same rule and
does not get an exception: token counts are not tagged by organization. One
series per tenant per channel grows for as long as the product sells, and the
cost is paid by the metrics backend forever rather than by the request that
created it. Per-organization attribution stays on the span, which already
carries the identifier, or belongs to a billing record — a product feature
rather than a side effect of telemetry. The dashboard the design asked for is
therefore a span query, not a meter.

Not done, with a reason: publishing
`FailureTolerantGraphRagEventSink.swallowedFailureCount()` as a gauge. The
wrapper is built in `core` and `apps/worker`, and `core` has no Micrometer
dependency. Adding one to the domain module is a boundary question rather than a
metrics question, so it is not being decided in passing. The log line remains the
signal until then.

Gate: module tests, `:core:test`, API context load, worker indexing tests.

## 3. Collector and dashboards — not started

- [ ] `compose.observability.yaml` behind a profile, restoring the existing
      Alloy/Loki/Tempo/Prometheus/Grafana stack.
- [ ] Join API and worker to the collector network; set
      `OTEL_EXPORTER_OTLP_ENDPOINT`. Prometheus stays off the proxy network;
      Grafana is published through Nginx Proxy Manager with Keycloak OIDC.
- [ ] Commit dashboards to the repository: infrastructure, and a separate AI
      cost and quality board covering tokens by organization, truncation rate,
      cache hit rate and TTFT.
- [ ] Extend `smoke-production.sh` to export a known signal and verify receipt,
      so a silent exporter fails deployment instead of passing it.

## 4. Trace continuity — not started

Highest regression risk; run it last, once a collector can show the result.

- [ ] Root span per worker indexing job.
- [ ] Wrap both virtual-thread executors with `ContextSnapshot` so model calls
      inside tasks keep their parent.
- [ ] Add the OpenTelemetry starter to `apps/mcp` and propagate `traceparent` on
      its API client.
- [ ] Prove: a job's child spans share the root `traceId`, and a span created
      inside a virtual-thread task has the expected parent.

## 5. Consolidate — not started

- [ ] Whole-export test: scan every span, event, resource and
      instrumentation-scope attribute, plus the log stream, on success and
      failure paths, against the allowlist. This is the runbook's release gate
      and it has never been executed.
- [ ] Reconcile `docs/specs/domains/secure-graph-rag.md` and its test matrix.
- [ ] Correct the runbook's field list — it omits `scopeFingerprint`,
      `cacheStatus` and `occurredAt`, and describes counts as bounded when only
      non-negativity is checked.
- [ ] Amend the runbook's "no exception event or stack trace" gate. The event now
      survives carrying `exception.type` alone, which is what the exporter
      enforces; the gate should say that rather than something stricter than the
      code.
- [ ] Resolve where telemetry egress lives. `integrations/graph-rag-observability`
      is named for one domain but now owns the span sanitizer, which protects
      every span in the process. Renaming it is a module-boundary change and
      needs its own challenge, so it is recorded here rather than done quietly.
- [ ] Record the payload-boundary decision under `docs/decisions/`, superseding
      nothing but documenting the challenge outcome.

## Deferred, with reasons

- Prometheus scrape endpoint. OTLP push is already wired and the worker has no
  scrape target.
- Langfuse as a destination. It accepts payload-free OTLP spans today with no
  adapter code; it earns a pipeline only if governed content capture is ever
  built.
- Closed `failureCode` taxonomy, keyed fingerprints, bucketed counts. Raised by
  the architecture challenge as hardening beyond the current gap; worth doing,
  not blocking.
