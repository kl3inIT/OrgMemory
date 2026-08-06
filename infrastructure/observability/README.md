# Observability stack

Grafana, Prometheus, Loki, Tempo and Alloy, plus host and container exporters.
Separate from `compose.production.yaml` so it can be started and stopped without
touching the product.

## Run

```bash
cd /apps/orgmemory/infrastructure/observability
cp observability.env.example observability.env   # fill in, chmod 600
docker compose -f compose.observability.yaml --env-file observability.env up -d
```

Every service in this stack joins `shared-infra`, and applications reach the
collector at `observability-alloy:4318` on that network. The product deployment
and this independently operated stack are the two halves of the wiring. A
product deploy does not recreate observability containers: after changing this
Compose topology, explicitly apply this file from the same merged release, then
confirm the shared-network DNS checks and trace check below. Never `localhost` —
Alloy binds `127.0.0.1` on the host, and inside a container that address is the
container itself.

## Reach Grafana

**https://grafana.zeromail.vn**, through Nginx Proxy Manager, signing in with
Keycloak. Access is gated by the `observability` realm role — a user Keycloak
authenticates who lacks it is refused rather than admitted as a Viewer of
telemetry that spans every organization. Grant it with:

```bash
docker exec orgmemory-keycloak-1 /opt/keycloak/bin/kcadm.sh   add-roles -r orgmemory --uusername <user> --rolename observability --config <kcadm.config>
```

The local administrator stays enabled as break-glass, for the case where the
incident that sent you here also took Keycloak's database — which is the same
PostgreSQL the product uses. Its password is in `observability.env` on the host.
See [decision 0021](../../docs/decisions/0021-grafana-authenticates-through-keycloak-gated-by-a-role.md).

**Do not reach it over an SSH tunnel.** `GF_SERVER_ROOT_URL` is the public
hostname, and Grafana builds plugin asset URLs from it, so the Drilldown apps
fail to load at `localhost` with a bare "Plugin failed to load" and no clue why.

## What is wired to what

```
apps ──OTLP──▶ Alloy ──▶ Tempo          (traces)
                     ├──▶ Prometheus     (metrics, remote write, exemplars on)
                     └──▶ Loki           (logs, tailed from the json-file driver)

metric point ──exemplar──▶ trace ──▶ log lines ──▶ trace
```

The links are the point. Three signals with one login is three silos; exemplars,
`tracesToLogsV2` and Loki derived fields are what make it one tool.

## Things that will bite

- **Tempo 3.0 removed `ingester`, `compactor`, `ingester_client` and
  `metrics_generator_client`.** A config carried over from 2.x fails to parse.
  Verify before deploying: `docker run --rm -v $PWD/tempo/tempo.yml:/etc/tempo/tempo.yml
  grafana/tempo:3.0.2 -config.file=/etc/tempo/tempo.yml -config.verify=true`.
  Silence and exit 0 mean valid.
- **Tempo runs as uid 10001 and declares no `VOLUME`**, so a named volume mounted
  anywhere it does not already own comes up root-owned and Tempo cannot create
  its block directory. The `user:` line in the compose file is load-bearing.
- **cAdvisor moved registry.** `gcr.io/cadvisor/cadvisor` stops at `v0.55.1`;
  newer images are `ghcr.io/google/cadvisor`. A config copied from an older stack
  is pinned five minors back with nothing to say so.
- **`loki.source.docker` is not used on purpose.** It tails the Docker logs API,
  whose stream dies and is never re-established while Alloy keeps reporting
  healthy — the same failure shape as an exporter pushing to nowhere. See the
  comment in `alloy/config.alloy`.
- **Changing a dashboard's `uid` in place does not provision.** Grafana tracks a
  provisioned board by file path, so a new uid in an existing file collides with
  the row already there and every reload logs `failed to save dashboard ...
  deprecatedInternalID ... is already in use` while quietly serving the old
  board. The API refuses to delete it too, because the file still exists. Move
  the file away, restart so `disableDeletion: false` removes the row, move it
  back, restart again.
- **A panel's metric name is worth checking against the server, not against
  memory.** Micrometer's OTLP export does not use the suffixes a Prometheus
  scrape would: the meter is `jvm_threads_live`, not `jvm_threads_live_threads`,
  and durations are `_milliseconds_*`, not `_seconds_*`. A wrong name is not an
  error anywhere — the panel is simply empty forever. Compare exactly:

  ```bash
  curl -s localhost:9090/api/v1/label/__name__/values
  ```

  Some absences are correct. `orgmemory_graph_rag_model_tokens_total` and
  `orgmemory_graph_rag_stage_failures_total` exist in code and appear the first
  time extraction runs or a stage fails; a counter with no occurrence has no
  series. The inherited PostgreSQL board asks for `pg_stat_bgwriter_*` counters
  this exporter no longer publishes, and the node board asks for collectors that
  are off by default, so both carry some permanently empty panels.
- **`cadvisor` is the largest single CPU consumer on the host** — around 8% of a
  core, above every application. That is normal for cAdvisor and worth knowing
  before someone reads the container CPU board and starts an investigation.
- **`postgres-exporter` sits behind the `database` profile** because it needs a
  monitoring role in the production database. Create the role, put its DSN in
  `observability.env`, then `--profile database up -d`.

## Verify it actually works

Readiness is not evidence. Push a trace through the real path and read it back:

```bash
TRACE_ID=$(head -c 16 /dev/urandom | od -An -tx1 | tr -d ' \n')
docker run --rm --network shared-infra curlimages/curl:latest -s -o /dev/null -w '%{http_code}\n' \
  -X POST http://observability-alloy:4318/v1/traces -H 'Content-Type: application/json' \
  -d "{\"resourceSpans\":[{\"resource\":{\"attributes\":[{\"key\":\"service.name\",\"value\":{\"stringValue\":\"smoke\"}}]},\"scopeSpans\":[{\"spans\":[{\"traceId\":\"$TRACE_ID\",\"spanId\":\"$(head -c 8 /dev/urandom | od -An -tx1 | tr -d ' \n')\",\"name\":\"smoke\",\"kind\":2,\"startTimeUnixNano\":\"$(date +%s)000000000\",\"endTimeUnixNano\":\"$(date +%s)000000000\"}]}]}]}"

docker run --rm --network observability_observability-internal curlimages/curl:latest \
  -s "http://observability-tempo:3200/api/traces/$TRACE_ID"
```

Tempo and Loki are distroless: there is no shell and no `wget` inside them, so
query them from a throwaway `curl` container rather than `docker exec`.
