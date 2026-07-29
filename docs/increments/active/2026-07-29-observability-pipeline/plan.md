# Observability pipeline plan

## 0. Close the payload bypasses — not started

Highest priority. These are live paths, independent of the pipeline work.

- [ ] Pin `org.springframework.ai.openai` and `org.springframework.ai.anthropic`
      logger levels to `ERROR` in API and worker production configuration, so the
      provider WARN paths that concatenate the prompt cannot emit.
- [ ] Search retained production logs for those three WARN signatures. Treat a
      hit as a data incident, not a defect: record scope and affected
      organizations before changing anything.
- [ ] Decide how `OtelSpan.error` is handled. Either suppress exception recording
      for observations that can carry OrgMemory content, or amend the runbook
      gate that currently cannot pass.
- [ ] Add a payload-free health signal for swallowed `GraphRagEventSink`
      failures. Producers currently catch and ignore them
      (`GraphRagKnowledgeRetrievalService:149-172,565-610,630-646,772-788`,
      `GraphIndexingProcessor:249-265`), so a broken sink is invisible.

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

## 2. Metrics that answer stage latency — not started

Depends on the composite sink merged in PR #132.

- [ ] Remove `@ConditionalOnMissingBean(GraphRagEventSink.class)` from
      `GraphRagObservabilityAutoConfiguration`; a sink is not an exclusive port.
      Add a per-sink `@ConditionalOnProperty` toggle.
- [ ] Add `MicrometerGraphRagEventSink` — timers by stage, outcome and cache
      status; counters by failure code. Metrics are not sampled, so stage p95
      becomes answerable at full coverage.
- [ ] Export `ContextTokenUsage`, which core already computes and nothing
      publishes, plus a counter for `finish_reason=length` truncation.
- [ ] Time to first token on the streaming assistant path.
- [ ] Tests: both sinks enabled receive the event; both disabled compose to
      `NO_OP`.

Gate: module tests, `:core:test`.

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
