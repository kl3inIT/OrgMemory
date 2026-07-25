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

"${compose[@]}" exec -T web \
  wget -q -O - http://127.0.0.1:8080/healthz \
  | grep -Fxq "ok"

"${compose[@]}" exec -T api \
  wget -q -O - http://127.0.0.1:8080/actuator/health/readiness \
  | grep -q '"status":"UP"'

"${compose[@]}" exec -T mcp \
  wget -q -O - http://127.0.0.1:8090/actuator/health/readiness \
  | grep -q '"status":"UP"'

"${compose[@]}" exec -T keycloak bash -ec \
  "{ printf 'GET /health/ready HTTP/1.0\r\n\r\n' >&0; grep -q '\"status\" *: *\"UP\"'; } 0<>/dev/tcp/127.0.0.1/9000"

if [[ "${ORGMEMORY_REQUIRE_PUBLIC_SMOKE:-false}" == "true" ]]; then
  web_status="$(curl --fail --silent --show-error \
    --output /dev/null \
    --write-out '%{http_code}' \
    https://om.kl3in.tech/healthz)"
  [[ "$web_status" == "200" ]]

  issuer="$(curl --fail --silent --show-error \
    https://auth.kl3in.tech/realms/orgmemory/.well-known/openid-configuration \
    | python3 -c 'import json,sys; print(json.load(sys.stdin)["issuer"])')"
  [[ "$issuer" == "https://auth.kl3in.tech/realms/orgmemory" ]]
fi

printf 'OrgMemory production smoke passed.\n'
