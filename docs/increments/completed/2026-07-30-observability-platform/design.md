# Observability platform design

## Why this is a separate increment

`2026-07-29-observability-pipeline` instrumented the application: it closed the
payload bypasses, silenced the unconfigured exporter, and wired thirteen of
fourteen stages with their latency, token cost and truncation signals. Phases 0,
1, 2, 4 and 5 of that increment are done and verified.

None of it is read by anything. There is no collector. Spans and meters are
produced and dropped, which is the same failure class the increment opened to
fix — an exporter reporting healthy while pushing to nowhere — in a different
shape.

That last phase is not more application code. It is server topology, a shared
runtime, network boundaries and a published surface, and it needs the project
owner's decision before it touches production. It also grew: research on
2026-07-30 found that Spring AI already emits most of what one open decision
was scoped to build, and separately found a gap in the payload boundary this
repository believed was closed. Those belong together with the collector work
because they are what the collector will show.

The prior increment stays active for the decisions still recorded against it.

## Findings that shape this design

### The server already holds a full stack, stopped

Inspected over SSH on 2026-07-30. Every component exists as an exited container
under the `zero-mail` compose project, with its data volumes intact:

| Container | Image | Volume |
|---|---|---|
| `zeromail-grafana` | `grafana/grafana:13.0.1` | `zeromail_grafana_data` |
| `zeromail-prometheus` | `prom/prometheus:v3.12.0` | `zeromail_prometheus_data` |
| `zeromail-loki` | `grafana/loki:3.7.2` | `zeromail_loki_data` |
| `zeromail-tempo` | `grafana/tempo:2.10.5` | `zeromail_tempo_data` |
| `zeromail-alloy` | `grafana/alloy:v1.16.1` | `zeromail_alloy_data` |
| `zeromail-node-exporter`, `zeromail-cadvisor`, `zeromail-postgres-exporter`, `zeromail-redis-exporter` | upstream | — |

Definition: `/apps/zero-mail/docker/docker-compose.observability.yml`, config
under `/apps/zero-mail/docker/observability/`. Host capacity at inspection:
16 GB total, 10.3 GB available, 90 GB free disk. The stack's declared memory
limits total roughly 4.3 GB.

Nothing here needs to be invented. The question is ownership, not construction.

### What Zero Mail got right, and this design keeps

Read from its compose and Alloy configuration rather than assumed:

- **Alloy is the only door.** Applications send OTLP to one address; Alloy
  routes onward. An application never learns that Tempo exists.
- **Logs are read from disk, not shipped over the network.** Alloy tails the
  Docker `json-file` driver directly. Cheaper, and a log line survives the
  process that wrote it. The prior increment already disabled OTLP log export on
  this reasoning, independently.
- **Nothing is published.** Every port binds `127.0.0.1`. Only Grafana joins a
  proxy network.
- **Bounded.** Prometheus retains 14 days or 8 GB, whichever comes first; every
  container declares a memory limit. Without these an observability stack
  becomes the outage.
- **Dashboards are code**, provisioned read-only from the repository, so a change
  made in the browser cannot survive a restart and pretend to be the source.
- **Off by default.** A separate compose file that must be named explicitly.

Two of its seven dashboards — `jvm-micrometer.json` and
`spring-boot-statistics.json` — apply to OrgMemory unchanged, because both
products are Spring Boot and emit the same meter names.

### Metrics arrive by a different route here

Zero Mail's Prometheus **scrapes** `/actuator/prometheus` on each application.
OrgMemory cannot be scraped the same way: `apps/worker` sets
`spring.main.web-application-type: none` and serves no HTTP at all. The prior
increment recorded this when it deferred a scrape endpoint and chose OTLP push.

Zero Mail's Alloy has **no metrics pipeline** — its configuration routes traces
to Tempo and logs to Loki and nothing else. So the platform needs a metrics path
added, not merely re-pointed.

Prometheus already runs with `--web.enable-remote-write-receiver`, so Alloy can
forward without Prometheus changing. Enabling Prometheus's own OTLP receiver and
letting applications push straight past Alloy was considered and rejected: it
would give metrics a different door from traces for no gain, and lose the one
place where relabelling and filtering can happen before storage.

### Spring AI already emits most of what one open decision was scoped to build

Verified in the repository, not inferred from documentation:
`OpenAiCompatibleChatModelFactory` and `AnthropicMessagesChatModelFactory` both
call `.observationRegistry(observations)`. Spring AI's model observations are
live in this application today; nothing reads them.

Per the Spring AI observability reference, each model call produces
`gen_ai.client.operation` carrying:

- metrics `gen_ai_client_operation_seconds_{count,sum,max}` and
  `gen_ai_client_token_usage_total`, the latter labelled
  `gen_ai_token_type=input|output|total`;
- span attributes including `gen_ai.response.finish_reasons`,
  `gen_ai.usage.input_tokens`, `gen_ai.usage.output_tokens`,
  `gen_ai.request.model` and `gen_ai.request.stream`.

The prior increment's plan claimed `finish_reason=length` required changing
`ChatModelPort`, because that port streams `Flux<String>` and
`SpringAiChatModelAdapter` calls `.stream().content()`, discarding the
`ChatResponse`. That reasoning was correct about OrgMemory's own port and wrong
about the system: Spring AI observes **inside** `ChatModel`, below the port, and
sees the response the port discards.

So of the three items behind the `GENERATE` decision, generation latency,
per-call token usage and finish reason are already emitted. **Only time to first
token remains**, and Spring AI cannot supply it — its observation for a
streaming call covers the whole stream.

This does not make the `GENERATE` boundary question moot; it makes it much
cheaper, and it means the question should be re-asked against a smaller scope
once the collector shows what already exists.

`ProviderTokenUsage`, added by the prior increment on `EXTRACT` and `GLEAN`, is
not duplicated by this. Spring AI attributes cost to a **model**; that record
attributes it to a **pipeline stage**. Both are wanted and neither derives the
other.

### A hole in the payload boundary this repository believed was closed

The applications set `spring.ai.chat.observations.log-prompt`, `log-completion`
and `include-error-logging` to false, with a comment explaining that they are
declared rather than left to default so the posture reads as one block.

The Spring AI reference documents a **second, differently named** family for the
ChatClient layer — `spring.ai.chat.client.observations.*`. It is not declared
anywhere in this repository. It defaults to false, so nothing leaks today; it is
exactly the assumption the existing comment says it does not want to rely on.

Worse, neither structural guard covers this path:

- `ProviderLoggingBoundaryVerifier` inspects **logger levels** and nothing else.
- `ExceptionSanitizingSpanExporter` filters **exception events**; the domain spec
  states plainly that span attributes are not filtered.

So a deployment that sets any of these flags true captures prompt and completion
text, and nothing in the process stops it. Decision 0018 claims the boundary is
structural rather than conventional. On this path it was conventional.

Two risks documented in the Spring AI reference do **not** apply here, verified
by search: OrgMemory uses no Spring AI `VectorStore` — whose
`db.vector.query.content` attribute is the query text — and no tool calling,
whose `spring.ai.tool.call.arguments` is gated behind
`spring.ai.tools.observations.include-content`. Both should be re-checked if
either is ever adopted.

**Correction, 2026-07-30, made while closing this.** The paragraph above
originally said these flags put text *onto spans*. That was read from the
reference rather than from the code, and it is wrong about where the text goes.
Opened from the 2.0.0 jars, each flag registers a component, and there are two
shapes:

- `spring.ai.chat.observations.log-prompt`, `log-completion`, the identically
  named pair under `spring.ai.chat.client.observations`, and
  `spring.ai.image.observations.log-prompt` each register a handler
  (`ChatModelPromptContentObservationHandler` and its siblings) that writes the
  text to the **application log at INFO**. `include-error-logging` registers
  `ErrorLoggingObservationHandler`, which logs the throwable.
- `spring.ai.tools.observations.include-content` registers an observation filter
  that adds the arguments to the **span** as `spring.ai.tool.call.arguments`.

The finding survives the correction — the ChatClient family was undeclared and
unguarded, and `SpringAiChatModelAdapter` builds every call through
`ChatClientBuilderConfigurer`, so it is a live path rather than a latent one —
but the destination changes what fixes it.

### What closes it, and what does not

`ObservationContentBoundaryVerifier` reads all eight flags from the resolved
`Environment` and refuses to start when any is true. The property is the whole
control — each capturing component exists only because its property is true — so
the property is the right thing to check, and reading the `Environment` rather
than the file is what makes it a boundary instead of a default. Each application
additionally reads its own classpath's `spring-configuration-metadata.json` and
fails when Spring AI declares an observation property the list has never heard
of, so a dependency bump cannot open a ninth path quietly.

**The sanitizer keeps filtering exception events only.** This was the open
question in phase 1, and the correction above answers it: five of the six live
flags never reach a span, so span-attribute filtering would not have closed the
hole that prompted the question. The sixth does reach a span, and the verifier
refuses to start with it on. Generic attribute filtering would mean an allowlist
applied to spans this repository does not construct — it would have to enumerate
Spring AI's own useful attributes to avoid dropping them, and that enumeration
would need revisiting on every upgrade. `WholeExportAllowlistTests` continues to
assert the whole exported span for OrgMemory's own spans, where the allowlist is
knowable. Recorded here rather than left implicit: an unfiltered attribute path
nobody decided to leave open is worse than one that was.

### Two upstream facts that will age

- `gen_ai.system` is deprecated in OpenTelemetry semantic conventions 1.37 in
  favour of `gen_ai.provider.name`. Spring AI still emits the old name;
  `spring-projects/spring-ai#6668`, opened 2026-07-23, is unresolved with no
  milestone. Dashboards should reference it in as few places as possible.
- The Spring AI reference notes that for **streaming** calls the HTTP span is not
  parented under the chat-model span, because of asynchronous behaviour in the
  provider SDKs. OrgMemory's assistant path is streaming, so this affects it.
  Nothing in this repository can fix it; it is recorded so a broken-looking trace
  is recognised rather than re-investigated.

## Decisions

### The stack becomes shared infrastructure, not Zero Mail's

The owner chose separation on 2026-07-30. Naming a stack after one product while
two depend on it is the same defect as `integrations/graph-rag-observability`
owning a process-wide span sanitizer: the name misdescribes the blast radius, and
somebody eventually acts on the name.

There is precedent in this repository to follow rather than invent. The shared
PostgreSQL cutover created a neutral `shared-infra` network and attached the
retained container to it under neutral aliases. The observability stack takes the
same shape.

Data volumes are preserved through the rename. Losing fourteen days of Zero Mail
history to a naming change would be a self-inflicted incident.

### Grafana's exposure is deferred to the owner

Publishing Grafana through Nginx Proxy Manager with Keycloak OIDC is what the
prior increment's plan assumed. It adds a public surface and an authentication
path. The alternative — reachable only over an SSH tunnel — has no public
surface and no OIDC client to misconfigure, at the cost of convenience.

Recorded as an open decision rather than chosen here, because it is an exposure
decision on a production host.

### Exemplars are in scope

Zero Mail does not have them. They are the single highest-value addition: a slow
point on a latency chart becomes a link to the trace of the request that caused
it. Without them a chart says something is slow and leaves the reader to search.

This requires `--enable-feature=exemplar-storage` on Prometheus and an exemplar
configuration on the Grafana datasource pointing at Tempo. OTLP push carries
exemplars natively, so the application side needs nothing.

## Rejected alternatives

**Stand up a second, OrgMemory-owned stack.** Cleanest separation, and it doubles
roughly 4.3 GB of memory limits on a host with 10.3 GB available while serving a
proof of concept. Rejected on capacity and on duplication of dashboards that
already work.

**Leave the stack named `zeromail-*` and just join it.** Fastest, and the owner
declined it. It would also repeat, on the server, the exact naming defect this
increment's sibling recorded in the repository.

**Give metrics their own door into Prometheus via its OTLP receiver.** Rejected:
traces and metrics would enter by different paths, and the one place able to
relabel or drop before storage would be bypassed.

**Build a `GENERATE` stage now.** Deferred. Most of what it was scoped to produce
already exists; the boundary question should be re-asked once that is visible.
