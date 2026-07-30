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
- [x] `PARSE` and `CHUNK`. The ingestion pipeline held no sink at all, so an
      upload that spent minutes parsing was indistinguishable from one that spent
      milliseconds — the revision status moved through `PARSING` and `CHUNKING`,
      but a status says where a job is, not how long it stayed there. Both are
      emitted under the same `jobId` the graph indexing stages use, so one upload
      is one operation across two processors. The engine measures each window and
      carries both out, because its caller makes one `process` call and cannot
      see where parsing ended.
- [ ] Deletion and rebuild: missing from the enum entirely, and the runbook
      requires a drill for it. Decide separately.
- [x] Extraction cost. `ProviderTokenUsage` is its own record rather than a
      forced reuse of `TokenUsage`: one is a budget the deployment estimated,
      the other a bill a vendor counted, and the difference between them is
      itself worth seeing. `EXTRACT` carries round zero and `GLEAN` round one,
      so the gleaning round is never billed twice. It rides on the existing
      stage event rather than a second one — an extra event with a zero duration
      would have dragged the stage timer's p95 down toward zero.

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
boundary decision is implemented.

Corrected 2026-07-30: `finish_reason=length` does **not** need a
`ChatModelPort` change. That reasoning was right about this repository's port,
which does stream `Flux<String>` and does discard the `ChatResponse`, and wrong
about the system: Spring AI observes inside `ChatModel`, below the port, and
already emits `gen_ai.response.finish_reasons` along with token usage and
generation latency. Both model factories wire an `ObservationRegistry`, so this
is live today and merely unread.

What remains of this item is time to first token, which Spring AI cannot supply
because its streaming observation covers the whole stream — and the destination
question, which is the boundary decision itself. Re-ask the challenge against
that smaller scope.

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

## 3. Collector and dashboards — moved

Superseded by `docs/increments/active/2026-07-30-observability-platform/`.

It stopped being the last phase of an application-instrumentation cycle and
became its own: server topology, a stack shared with another product, a network
boundary and a published surface, none of which is application code and all of
which needs the owner's decision before touching production.

Research on 2026-07-30 also found that Spring AI already emits generation
latency, per-call token usage and finish reason — most of what the `GENERATE`
decision below was scoped to build — and separately found that the
`spring.ai.chat.client.observations.*` family is undeclared and unguarded. Both
belong with the collector, because the collector is what would show them.

## 4. Trace continuity — done

- [x] Root span per worker indexing job. The worker serves no request, so nothing
      else opened a trace and every stage span was a root of its own.
- [x] Both virtual-thread executors carry the submitting thread's context. Done
      through a `GraphRagTaskDecorator` port rather than by putting
      `context-propagation` on the classpath of two domain modules, which would
      have decided a module boundary in passing — the same question already
      deferred for the swallowed-failure gauge, answered the same way. Absent an
      implementation the executors behave exactly as before, and the concurrency
      is unchanged: same executors, same lifetimes, same bounds.
- [x] `apps/mcp` starts its own trace and propagates `traceparent`. A bare
      `RestClient.builder()` carries no `RestClientCustomizer`, and that
      customizer is what writes the header — so the gateway and the API were
      recording two unrelated traces per call. Boot 4.1 documentation is explicit
      that the auto-configured builder is required for propagation;
      `RestClientBuilderConfigurer.configure` sets a request factory of its own,
      so the gateway's factory is applied afterwards.
- [x] Adding the starter to `apps/mcp` meant giving it the same OTLP defaults as
      the other two services. Without them a third service would have begun
      exporting metrics to `localhost:4318` every minute — reintroducing exactly
      the production symptom this increment opened to fix.
- [x] Proved: context reaches a fresh virtual thread, an undecorated task shows
      the fixture would have caught a no-op, capture happens at decoration rather
      than execution, and the scope closes so one job cannot leak into the next.

## 5. Consolidate — not started

- [x] Whole-export test. `WholeExportAllowlistTests` walks the span name,
      attributes, status, every event and event attribute, instrumentation scope
      and resource, on success and failure paths, through the sanitizing exporter
      in its production position. A companion case exports an unmodelled
      attribute and asserts the gate fails, because a gate that has never
      rejected anything is indistinguishable from one that never looks.
- [x] Reconcile `docs/specs/domains/secure-graph-rag.md` and its test matrix.
- [x] Correct the runbook's field list. It omitted `scopeFingerprint`,
      `cacheStatus` and `occurredAt` — an allowlist that does not name everything
      exported is not an allowlist — and described counts as bounded when only
      non-negativity is checked.
- [x] Amend the runbook's "no exception event or stack trace" gate. It demanded
      something no code enforced and would have failed a correct export. The
      event survives carrying `exception.type` alone.
- [x] Resolve where telemetry egress lives. Settled 2026-07-30, and the answer was
      not a rename. The boundary moved to `integrations/observability`, which
      depends on no OrgMemory module and arrives through the app convention
      plugin. Investigating it found that `apps/mcp` held no payload boundary at
      all, because taking one meant taking the graph domain with it. See
      [decision 0019](../../../decisions/0019-the-payload-boundary-is-its-own-module.md).
- [x] Record the payload-boundary decision:
      `docs/decisions/0018-telemetry-carries-counts-never-payload.md`.

## Deferred, with reasons

- Prometheus scrape endpoint. OTLP push is already wired and the worker has no
  scrape target.
- Langfuse as a destination. It accepts payload-free OTLP spans today with no
  adapter code; it earns a pipeline only if governed content capture is ever
  built.
- Closed `failureCode` taxonomy, keyed fingerprints, bucketed counts. Raised by
  the architecture challenge as hardening beyond the current gap; worth doing,
  not blocking.
