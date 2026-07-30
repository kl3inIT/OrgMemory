# Observability platform plan

Read `design.md` for why each of these is shaped the way it is. This file is the
execution order and the state of each item.

Phase 1 is done. Phase 2 needs no decision from the owner; phase 3 onward does.

## 0. State this increment inherits

Carried from `2026-07-29-observability-pipeline`, which stays active for the
decisions still recorded against it:

- Application instrumentation is done and deployed. Thirteen of fourteen stages
  emit; production runs `sha-c4608b0` with the exporter silent, telemetry
  identity correct, and no provider prompt-leak signatures.
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

## 2. Time to first token

The only part of the prior increment's `GENERATE` scope that Spring AI does not
already supply. Independent of the collector and of the boundary question,
because it measures the stream this application already returns.

- [ ] Measure first emission on the assistant's streaming path. `AssistantService`
      already wraps the `Flux` it returns, so the measurement needs no port
      change; the destination does.
- [ ] Do not publish it until the `GENERATE` boundary decision below is taken.
      TTFT is a number, but where it is published is that decision, and putting it
      somewhere convenient first would settle the question by accident.

## 3. Rename the stack to shared infrastructure — needs the owner

Do not start before the owner confirms. This changes a running production host.

- [ ] Capture current state first: image digests, volume names, the compose
      project name, and Nginx Proxy Manager host entries. A rename without a
      recorded starting point cannot be rolled back.
- [ ] Move the definition out of `/apps/zero-mail/docker/` into its own
      directory, with a neutral compose project name and neutral container names.
- [ ] **Preserve the five data volumes through the rename.** `zeromail_grafana_data`,
      `zeromail_prometheus_data`, `zeromail_loki_data`, `zeromail_tempo_data` and
      `zeromail_alloy_data` hold real history. Compose renames volumes with the
      project by default; pin them by external name so it cannot.
- [ ] Create a neutral network for applications to join, following the
      `shared-infra` precedent rather than inventing a second pattern.
- [ ] Bring the stack back up and prove Zero Mail's own dashboards still resolve
      against the restored volumes before attaching anything new.

## 4. Give metrics a path — needs phase 3

- [ ] Add a metrics pipeline to the Alloy configuration: OTLP receiver, batch
      processor, remote-write to Prometheus. Alloy routes traces and reads logs
      today and handles no metrics at all.
- [ ] Prometheus needs no change for ingest — `--web.enable-remote-write-receiver`
      is already set — but does need `--enable-feature=exemplar-storage`.
- [ ] Attach `apps/api`, `apps/worker` and `apps/mcp` to the collector network and
      set `OTEL_EXPORTER_OTLP_ENDPOINT` to Alloy's service name. Not `localhost`:
      Alloy binds `127.0.0.1` on the host, and inside a container that address is
      the container itself. That mistake is the original production symptom.
- [ ] Flip `ORGMEMORY_OTLP_METRICS_ENABLED` to true only for services that can now
      reach a collector, and only after the endpoint resolves.
- [ ] Keep Prometheus, Loki and Tempo off any proxy network.

## 5. Dashboards — needs phase 4

- [ ] Reuse `jvm-micrometer.json` and `spring-boot-statistics.json` unchanged;
      both products are Spring Boot and emit the same meter names.
- [ ] New board for GraphRAG stages: latency by stage, failure rate by code, cache
      hit rate, context truncation rate, dropped contributions.
- [ ] New board for AI cost and quality, built on Spring AI's own meters —
      `gen_ai_client_token_usage_total` by `gen_ai_token_type`,
      `gen_ai_client_operation_seconds`, and `gen_ai.response.finish_reasons` from
      spans — plus `ProviderTokenUsage` for per-stage attribution Spring AI does
      not give.
- [ ] Reference `gen_ai.system` in as few places as possible. It is deprecated
      upstream in favour of `gen_ai.provider.name`, and Spring AI has not
      migrated (`spring-projects/spring-ai#6668`, unresolved, no milestone).
- [ ] Wire exemplars on the Grafana datasource so a latency point links to its
      trace, and trace-to-logs so a trace links to its lines.
- [ ] Commit every dashboard and provision it read-only.

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

1. **Grafana exposure.** Published through Nginx Proxy Manager with Keycloak
   OIDC, or reachable only over an SSH tunnel. A public surface and an OIDC
   client against convenience. Blocks phase 5's usefulness, not its work.
2. **`GENERATE` telemetry boundary.** Re-ask against the smaller scope: Spring AI
   already emits duration, token usage and finish reason, so the question is now
   only where TTFT goes and whether a stage exists at all. Full analysis remains
   in `2026-07-29-observability-pipeline/challenge-generation-telemetry.md`; its
   central counterargument stands — `GraphRagEventSink` is where the payload
   boundary is enforced, and generation is where prompts exist.
3. **Deletion and rebuild stage.** The hardening runbook requires a
   deletion-then-rebuild drill, and a search of `graph-rag-core`, `core` and
   `apps/worker` found no deletion path. Establish whether the drill is
   executable before adding a stage nothing can emit.
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
