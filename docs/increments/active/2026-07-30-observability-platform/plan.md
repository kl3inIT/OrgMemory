# Observability platform plan

Read `design.md` for why each of these is shaped the way it is. This file is the
execution order and the state of each item.

Phases 1 through 5 are done and running in production. Phase 6 — making a
silent exporter fail the deployment — is what remains.

## 0. State this increment inherits

Carried from `2026-07-29-observability-pipeline`, which stays active for the
decisions still recorded against it:

- Application instrumentation is done and deployed. Every declared stage emits;
  production runs `sha-c4608b0` with the exporter silent, telemetry identity
  correct, and no provider prompt-leak signatures.
- Nothing reads any of it. There is no collector.
- Phase 3 of that plan — collector, dashboards, smoke coverage — is superseded by
  this increment. Its remaining decisions (`GENERATE`, deletion/rebuild stage,
  telemetry-egress module naming) stay there.

Server inventory, Zero Mail's configuration, and the Spring AI research are
recorded in `design.md` so they survive without re-inspection.

## 1. Close the payload hole before anything can read telemetry — done

Highest priority and independent of the collector. Today nothing exports, so the
gap was latent for the one flag that reaches a span; for the rest it was live all
along, because they write to the application log.

- [x] Verify the real property names against
      `spring-configuration-metadata.json` in the Spring AI jars before writing
      any of them. Done, and it was worth doing: the reference names two of the
      eight, and the classpath declares `spring.ai.image.observations.log-prompt`
      and `spring.ai.tools.observations.include-content` as well. The ChatClient
      family has no `include-error-logging`, unlike its identically named sibling.
- [x] Declare the `spring.ai.chat.client.observations.*` family explicitly in
      both `apps/api` and `apps/worker`, beside the `spring.ai.chat.observations`
      block that already exists.
- [x] Fail startup when any observation content flag resolves true. Built as
      `ObservationContentBoundaryVerifier` rather than folded into
      `ProviderLoggingBoundaryVerifier`: one reads a resolved logger level, the
      other a resolved property, and a single verifier would have to explain both
      in one failure message. The list covers all eight flags, including the
      image, tool-calling and vector-store families no path exercises today.
- [x] Test it per flag, so each negative case fails for its own reason. Sixteen
      cases in the module, plus a per-app test that reads the application's own
      classpath metadata and fails when Spring AI declares an observation property
      the verifier does not guard. Dropping one flag from the list was confirmed
      to fail that test by name.
- [x] Decide whether the sanitizer should filter span attributes as well as
      exception events. **It should not**, and the reason is a correction to this
      increment's own design: five of the six live flags write to the application
      log rather than to a span, so attribute filtering would not have closed the
      hole that prompted the question. Full reasoning in `design.md`.

Gate: `:apps:api:test`, `:apps:worker:test`,
`:integrations:graph-rag-observability:test`,
`:integrations:observability:test`.

## 2. Time to first token — done

The only part of the prior increment's `GENERATE` scope that Spring AI does not
already supply. Independent of the collector and of the boundary question,
because it measures the stream this application already returns.

- [x] Measure first emission on the assistant's streaming path, from the arriving
      question rather than from the model call, because permission-scoped
      retrieval runs while the user waits. Both ends are held by mutation-checked
      tests: starting the clock at the model call, and recording the last token
      instead of the first, each fail exactly one test.
- [x] Settle the destination first. It is a Micrometer observation on the
      assistant's own surface, with the payload boundary in an
      `AssistantTurnEvent` record the convention reads through rather than in the
      mutable context. `Stage.GENERATE` is removed. See
      [decision 0020](../../../decisions/0020-assistant-generation-is-observed-on-its-own-payload-free-surface.md).
- [x] Keep OrgMemory's own metric name. The GenAI convention's
      `gen_ai.client.operation.time_to_first_chunk` measures from the model call
      and excludes retrieval, so it is a different interval; both are wanted once
      Spring AI emits the convention's one.

## 3. Stand up an OrgMemory-owned stack — done

Rewritten on 2026-07-30. The plan was to rename Zero Mail's stack and preserve
its five volumes. The owner then confirmed Zero Mail needs no observability at
all — its application containers had been stopped for five days — and that
losing its trace and log history was acceptable. That removes both reasons the
design had for reusing rather than replacing: there is no second stack to double
memory, and no dashboards worth inheriting wholesale.

- [x] Capture the starting state before touching anything. It found what the
      design had missed: `zeromail-postgres` belongs to compose project
      `postgres`, runs `orgmemory-postgres-rag`, and **is OrgMemory's production
      database**. A `docker compose -p zero-mail down -v` would not have hit it,
      but anyone working from the container *names* would have. Teardown was done
      by explicit container and volume name for that reason.
- [x] Remove the nine exited observability containers and their five volumes.
      `zeromail-postgres` and `zeromail-9router` were confirmed running and out
      of scope first.
- [x] Write the stack fresh under `infrastructure/observability/`, on the latest
      release of every component, with a neutral compose project (`observability`)
      and neutral container names.
- [x] Attach Alloy to the pre-existing `shared-infra` network so applications
      reach it without `compose.production.yaml` changing.
- [x] Verify by pushing a real OTLP trace through Alloy and reading it back out
      of Tempo, rather than by reading readiness endpoints.

## 4. Point the applications at the collector — done

- [x] Metrics pipeline in Alloy, `--enable-feature=exemplar-storage` on
      Prometheus, host and container exporters scraped down the same path.
- [x] Telemetry settings moved into their own compose anchor. `apps/mcp`
      inherited only the OIDC anchor, so it carried neither a collector endpoint
      nor a service version — its spans would have been labelled `local` and sent
      nowhere, which reads identically to an application with nothing to say.
      One anchor now, and no service can drift from it.
- [x] Use the Spring property names, not `OTEL_*`. `application-prod.yml` sets
      `management.opentelemetry.map-environment-variables: false`, so
      `OTEL_EXPORTER_OTLP_ENDPOINT` is read by nothing here. The earlier plan said
      to set exactly that; it would have left the exporter as silent as before,
      with no error. Names read from the Boot 4.1 configuration metadata.
- [x] Put `apps/mcp` on `shared-infra`. It was the only deployable that was not.
- [x] Create a read-only monitoring role and enable `postgres-exporter`.
- [x] Verified by observation, not by configuration: Tempo reports
      `orgmemory-api`, `orgmemory-mcp`, `orgmemory-worker`; Prometheus carries
      those three as jobs beside `node`, `cadvisor` and `postgres`; span
      `service.version` equals the running image tag exactly.

## 5. Dashboards — done

- [x] Inherit five boards from the stack this replaced: JVM, Spring Boot
      statistics, node-exporter, cAdvisor, PostgreSQL. All five apply unchanged
      because both products are Spring Boot on the same host.
- [x] `OrgMemory — GraphRAG pipeline`: stage latency and throughput, failures by
      bounded code, outcome and cache mix, context tokens by channel, assembled
      prompt size, and the two truncation meters kept apart — one answers how
      many answers were affected, the other how much evidence was refused.
- [x] `OrgMemory — AI cost and quality`: Spring AI's own token and duration
      meters, plus per-stage attribution Spring AI does not give. Metric names
      verified against what Prometheus actually holds: Micrometer exports timers
      in **milliseconds**, so a panel written against `_seconds_bucket` would be
      empty with no error.
- [x] `OrgMemory — Assistant`: time to first token by engine, the share of turns
      that never emitted a token, and outcome mix where `no_evidence` is not
      counted as a failure.
- [x] `finish_reason` is documented as a trace query rather than faked as a
      panel: Spring AI records it as a span attribute and exports no counter.
- [x] `gen_ai.system` referenced nowhere. It is deprecated upstream in favour of
      `gen_ai.provider.name` and Spring AI has not migrated
      (`spring-projects/spring-ai#6668`, unresolved).
- [x] Exemplars and trace-to-logs wired on the datasources; every board
      provisioned read-only from the repository.

## 6. Make a silent exporter fail the deployment — needs phase 4

The point of the whole increment. Until this exists, a collector that stops
receiving looks exactly like a system with nothing to report.

- [ ] Extend `smoke-production.sh` to export a known signal and assert it arrives.
- [ ] Fail the deployment when it does not.
- [ ] Note the delivery constraint recorded against the CI/CD increment: a commit
      touching only `infrastructure/deployment/**` builds no images and therefore
      cannot deploy, and the skipped run still reports success. Anything here that
      is infrastructure-only either rides with an application change or waits for
      that repair.

## Open decisions — owner's, analysed and not taken

1. ~~**Grafana exposure.**~~ Settled 2026-07-30, and inspecting the host decided
   it: Keycloak's database is the same PostgreSQL the product uses, so a
   published Grafana would have depended on the thing it exists to diagnose.
   Loopback plus an SSH tunnel, local administrator, no Keycloak. See
   [decision 0021](../../../decisions/0021-grafana-authenticates-through-keycloak-gated-by-a-role.md).
2. ~~**`GENERATE` telemetry boundary.**~~ Settled 2026-07-30. The counterargument
   was answered rather than overruled: the new surface carries the boundary in a
   record the convention reads through, so it is structural there too.
   `Stage.GENERATE` is removed. See
   [decision 0020](../../../decisions/0020-assistant-generation-is-observed-on-its-own-payload-free-surface.md).
   The challenge brief was never run by an independent reviewer; that gap is
   recorded in the decision.
3. **Deletion and rebuild stage.** The hardening runbook requires a
   deletion-then-rebuild drill. An earlier claim here that no deletion path
   exists was too strong — `ConnectorIngestionService.retire` and
   `ProcessingStatusIndex.delete` exist, and the AGE suite covers revision
   removal. The open question is narrower: does retirement reach all five read
   paths the drill names — content, lexical, vector, graph and citation — or
   stop at content? Establish that before adding a stage.
4. ~~**Telemetry-egress module naming.**~~ Settled 2026-07-30 and larger than the
   name: the boundary now lives in `integrations/observability`, which depends on
   no OrgMemory module and reaches every application through the app convention
   plugin. Investigating it found that `apps/mcp` had no payload boundary at all,
   because taking it meant taking the graph domain. See
   [decision 0019](../../../decisions/0019-the-payload-boundary-is-its-own-module.md).

## Deferred, with reasons

- **A second, OrgMemory-owned stack.** Cleanest separation, roughly 4.3 GB of
  additional memory limits on a host with 10.3 GB free, and duplicate dashboards.
- **Prometheus OTLP receiver instead of Alloy for metrics.** Would give metrics a
  different door from traces and bypass the only place able to relabel or drop
  before storage.
- **Langfuse as a destination.** Accepts payload-free OTLP today with no adapter;
  it earns a pipeline only if governed content capture is ever built.
- **Fixing the unparented HTTP span on streaming calls.** Caused by asynchronous
  behaviour in the provider SDKs and documented upstream by Spring AI. Recorded
  so the gap is recognised rather than re-investigated.
