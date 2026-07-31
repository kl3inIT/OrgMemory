# Observability Platform Verification

## Outcome

Telemetry leaves the applications over OTLP to one collector and is readable:

```text
apps --OTLP--> Alloy --> Tempo        (traces)
                    |--> Prometheus   (metrics, remote write, exemplars on)
                    |--> Loki         (logs, tailed from the json-file driver)

metric point --exemplar--> trace --> log lines --> trace
```

The payload boundary closed first, so nothing downstream can carry a prompt or a
completion. Two startup verifiers fail the application context rather than warn,
and they reach every deployable through the app convention plugin rather than
through each application's build file.

Phase 6 — making a silent exporter fail the deployment — was **dropped by the
project owner on 2026-07-31**, not deferred. `smoke-production.sh` still contains
no reference to the collector, so a deployment whose telemetry never arrives
still reports success. The plan's phase 6 records what compensating controls
exist and what remains uncovered.

## Delivery Evidence

- [PR #151](https://github.com/kl3inIT/OrgMemory/pull/151) merged as `27b8fb4` —
  the Spring AI observation content boundary.
- [PR #152](https://github.com/kl3inIT/OrgMemory/pull/152) merged as `71d30dd` —
  the boundary moved into `integrations/observability`, which depends on no
  OrgMemory module.
- [PR #153](https://github.com/kl3inIT/OrgMemory/pull/153) merged as `fa97c3c` —
  the assistant turn observation and time to first token.
- [PR #155](https://github.com/kl3inIT/OrgMemory/pull/155) merged as `a708f44` —
  the collector stack, the application wiring, and the dashboards.
- [PR #158](https://github.com/kl3inIT/OrgMemory/pull/158) merged as `4afc68c` —
  percentile histograms for every meter a board charts as a quantile.
- [PR #159](https://github.com/kl3inIT/OrgMemory/pull/159) merged as `4acfc94` —
  the HTTP histogram ceiling raised above a streamed assistant turn.

Decisions [0018](../../../decisions/0018-telemetry-carries-counts-never-payload.md),
[0019](../../../decisions/0019-the-payload-boundary-is-its-own-module.md),
[0020](../../../decisions/0020-assistant-generation-is-observed-on-its-own-payload-free-surface.md)
and [0021](../../../decisions/0021-grafana-authenticates-through-keycloak-gated-by-a-role.md)
carry the rationale. 0021 was rewritten when the Grafana exposure decision
flipped after the owner re-opened it.

## Production Evidence

Running stack: Grafana 13.1.1, Prometheus 3.13.2, Loki 3.7.4, Tempo 3.0.2,
Alloy 1.18.0, node-exporter 1.12.1, cAdvisor 0.60.5, postgres-exporter 0.20.1.
The nine exited containers and five volumes of the stack this replaced were
removed by explicit name.

Tempo reports `orgmemory-api`, `orgmemory-mcp` and `orgmemory-worker`.
`service.version` on every span and metric equals the running image tag exactly.
Grafana serves seven provisioned boards in two folders at
`https://grafana.zeromail.vn`, signing in through Keycloak realm `orgmemory`
gated by an `observability` role.

Both startup verifiers are live in production, and the applications started,
which is itself the proof that no observation content flag resolves true.

## Verified By Real Traffic, Not By Configuration

Readiness, `up=1`, and a green deployment were each insufficient. What produced
evidence was driving authenticated assistant turns through the production API
and then asking Prometheus for exactly what a panel asks for.

Twelve turns on 2026-07-31, observed 5.65 s to 11.70 s:

| Panel | Reported | Consistent with |
| --- | --- | --- |
| turn p50 / p95 | 7725 ms / 11016 ms | the observed range |
| time to first token p50 / p95 | 6676 ms / 9584 ms | below turn |
| model call p95 | 5348 ms | below TTFT; starts after retrieval |
| graph-rag stage p95 | 3126 ms | retrieval measured separately |
| HTTP p95 | 8448 ms | no longer pinned to its own bound |
| GC pause p95 | 31.9 ms | was a 30000 ms plateau |

A metric exemplar resolves to a trace whose spans break the request down through
the security filter chain, bearer authentication, graph-rag authorize, embed,
seven parallel snapshot reads and context assembly, to the model call.

That exercise found three faults, all of which had passed CI, review and
deployment, and each of which rendered as an empty chart rather than an error:

1. `orgmemory.assistant.time_to_first_token` had never existed. The handler
   carried `@ConditionalOnBean(MeterRegistry.class)` in configuration Boot
   processes before auto-configuration, so the condition was false on every
   startup and the bean was dropped silently.
2. Twenty-eight histogram families, none with a usable bucket. A Micrometer timer
   publishes one `+Inf` bucket unless told otherwise, so every
   `histogram_quantile` returned NaN.
3. The ceiling introduced by the fix for (2) sat below the endpoint it measured.
   `/api/assistant/chat` streams for the whole turn, so a ten-second HTTP bound
   put every real chat in the overflow bucket and pinned p95 to `10000 ms`.

`ConfigurationConditionTests` and `MetricsDistributionTests` hold the first two
failure modes at build time. The third is held by asserting the HTTP ceiling
covers an assistant turn rather than by asserting a literal.

## Known Gap

The streamed generation runs on a thread the request's trace context does not
reach, so its span roots a separate trace. A measured turn spent 3.2 of 13.0
seconds in retrieval and the remaining 9.8 seconds — 75% — carries no span. Turn
duration and time to first token cover that window; the trace does not. Recorded
in the assistant spec and deferred in the plan rather than re-investigated.
