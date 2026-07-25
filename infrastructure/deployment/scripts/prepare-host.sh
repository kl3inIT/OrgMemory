#!/usr/bin/env bash
set -Eeuo pipefail

shared_network="${ORGMEMORY_SHARED_INFRA_NETWORK:-shared-infra}"
proxy_network="${ORGMEMORY_PROXY_NETWORK:-proxy-network}"
postgres_container="${ORGMEMORY_SHARED_POSTGRES_CONTAINER:-zeromail-postgres}"
npm_container="${ORGMEMORY_NPM_CONTAINER:-nginx-proxy-manager}"
runtime_root="${ORGMEMORY_RUNTIME_ROOT:-/apps/orgmemory-runtime}"
environment_file="${ORGMEMORY_ENV_FILE:-/apps/orgmemory/.env.production}"

container_on_network() {
  local container_name="$1"
  local network_name="$2"

  docker network inspect "$network_name" \
    --format '{{range .Containers}}{{println .Name}}{{end}}' \
    | grep -Fxq "$container_name"
}

docker container inspect "$postgres_container" >/dev/null
docker container inspect "$npm_container" >/dev/null
docker network inspect "$proxy_network" >/dev/null

if ! docker network inspect "$shared_network" >/dev/null 2>&1; then
  docker network create --attachable "$shared_network" >/dev/null
fi

if ! container_on_network "$postgres_container" "$shared_network"; then
  docker network connect \
    --alias postgres \
    --alias shared-postgres \
    "$shared_network" \
    "$postgres_container"
fi

if ! container_on_network "$npm_container" "$proxy_network"; then
  printf 'Nginx Proxy Manager is not attached to %s.\n' "$proxy_network" >&2
  exit 1
fi

install -d -m 0700 \
  "$runtime_root" \
  "$runtime_root/backups" \
  "$runtime_root/releases"

if [[ -e "$environment_file" ]]; then
  chmod 0600 "$environment_file"
else
  printf 'Create %s from production.env.example, then set mode 0600.\n' \
    "$environment_file"
fi

printf 'Host prepared: PostgreSQL=%s, shared network=%s, proxy network=%s\n' \
  "$postgres_container" \
  "$shared_network" \
  "$proxy_network"
