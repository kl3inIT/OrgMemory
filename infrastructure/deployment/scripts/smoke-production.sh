#!/usr/bin/env bash
set -Eeuo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
compose_file="$repo_root/infrastructure/deployment/compose.production.yaml"
environment_file="${ORGMEMORY_ENV_FILE:-$repo_root/.env.production}"

compose=(
  docker compose
  --file "$compose_file"
  --env-file "$environment_file"
)

retry() {
  local attempts="$1"
  local delay_seconds="$2"
  shift 2

  local attempt
  for ((attempt = 1; attempt <= attempts; attempt++)); do
    if timeout 15s "$@"; then
      return 0
    fi
    if ((attempt < attempts)); then
      sleep "$delay_seconds"
    fi
  done
  return 1
}

retry 5 2 "${compose[@]}" exec -T web sh -ec \
  "wget -q -T 5 -O - http://127.0.0.1:8080/healthz | grep -Fxq ok"

retry 5 2 "${compose[@]}" exec -T api sh -ec \
  "wget -q -T 5 -O - http://127.0.0.1:8080/actuator/health/readiness | grep -q '\"status\":\"UP\"'"

retry 5 2 "${compose[@]}" exec -T mcp sh -ec \
  "wget -q -T 5 -O - http://127.0.0.1:8090/actuator/health/readiness | grep -q '\"status\":\"UP\"'"

retry 5 2 "${compose[@]}" exec -T keycloak bash -ec \
  "{ printf 'GET /health/ready HTTP/1.0\r\n\r\n' >&0; grep -q '\"status\" *: *\"UP\"'; } 0<>/dev/tcp/127.0.0.1/9000"

if [[ "${ORGMEMORY_REQUIRE_PUBLIC_SMOKE:-false}" == "true" ]]; then
  web_status="$(curl --fail --silent --show-error \
    --connect-timeout 5 \
    --max-time 15 \
    --retry 5 \
    --retry-all-errors \
    --retry-delay 2 \
    --output /dev/null \
    --write-out '%{http_code}' \
    https://om.kl3in.tech/healthz)"
  [[ "$web_status" == "200" ]]

  issuer="$(curl --fail --silent --show-error \
    --connect-timeout 5 \
    --max-time 15 \
    --retry 5 \
    --retry-all-errors \
    --retry-delay 2 \
    https://auth.kl3in.tech/realms/orgmemory/.well-known/openid-configuration \
    | python3 -c 'import json,sys; print(json.load(sys.stdin)["issuer"])')"
  [[ "$issuer" == "https://auth.kl3in.tech/realms/orgmemory" ]]
fi

printf 'OrgMemory production smoke passed.\n'
