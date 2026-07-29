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

## 1. Silence the unconfigured exporter — not started

- [ ] `management.otlp.metrics.export.enabled` defaults to false; enabling it
      requires an explicit endpoint.
- [ ] `management.logging.export.otlp.enabled: false` — Alloy tails `json-file`.
- [ ] `management.opentelemetry.map-environment-variables: false` in production
      so host `OTEL_*` cannot enable export implicitly.
- [ ] Declare `spring.ai.chat.observations.include-error-logging: false`
      explicitly rather than relying on the framework default.
- [ ] Add `service.version` and `deployment.environment` resource attributes.
- [ ] Worker tracing sampling to 1.0; API stays at 0.1.

Gate: `compileJava`, `:core:test`.

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
- [ ] Export `ContextTokenUsage`, which core already computes and nothing
      publishes, plus a counter for `finish_reason=length` truncation.
- [ ] Time to first token on the streaming assistant path.
- [ ] Close the stage gap the LightRAG comparison surfaced. `Stage` declares
      fourteen and production emits ten: `PARSE` and `CHUNK` need a sink in the
      ingestion pipeline, `GLEAN` needs separating from extraction, and
      `GENERATE` needs emitting where retrieval currently stops at
      `ASSEMBLE_CONTEXT`. Decide deletion and rebuild separately — it is missing
      from the enum entirely and the runbook requires a drill for it.

Cardinality decision, recorded because it is easier to add a tag than to remove
one from a series that already exists: organization and operation identifiers,
fingerprints and durations are not tags. Each would grow the stored series count
with tenants or requests. They stay on the span, where each is one record rather
than a permanent series. `failureCode` tags only the failure counter, never the
timer, because the port bounds its shape and not its set of values. The design's
"tokens by organization" dashboard therefore needs its own decision when the
token metrics land.

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
