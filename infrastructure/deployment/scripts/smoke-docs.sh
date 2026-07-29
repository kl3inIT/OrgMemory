#!/usr/bin/env bash
set -Eeuo pipefail

repo_root="${ORGMEMORY_REPO_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)}"
compose_file="${ORGMEMORY_DOCS_COMPOSE_FILE:-$repo_root/infrastructure/deployment/compose.docs.yaml}"
environment_file="${ORGMEMORY_DOCS_ENV_FILE:-$repo_root/.env.docs.production}"
public_url="${ORGMEMORY_DOCS_PUBLIC_URL:-}"
require_public_smoke="${ORGMEMORY_DOCS_REQUIRE_PUBLIC_SMOKE:-false}"

read_environment_value() {
  local key="$1"

  python3 - "$environment_file" "$key" <<'PY'
import sys

path, wanted = sys.argv[1:]
with open(path, encoding="utf-8") as stream:
    for raw_line in stream:
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        if key.strip() == wanted:
            print(value.strip().strip("\"'"))
            break
PY
}

if [[ -z "$public_url" ]]; then
  public_url="$(read_environment_value ORGMEMORY_DOCS_PUBLIC_URL)"
fi

compose=(
  docker compose
  --file "$compose_file"
  --env-file "$environment_file"
)

wait_for_internal_health() {
  local deadline=$((SECONDS + 60))
  local remaining

  while ((SECONDS < deadline)); do
    remaining=$((deadline - SECONDS))
    if timeout "${remaining}s" "${compose[@]}" exec -T orgmemory-docs sh -ec \
      "wget -q -T 5 -O - http://127.0.0.1:3000/healthz | grep -Fxq ok"; then
      return 0
    fi
    if ((SECONDS < deadline)); then
      sleep 2
    fi
  done

  printf 'Docs container did not become healthy within 60 seconds.\n' >&2
  return 1
}

check_internal_route() {
  local route="$1"

  timeout 15s "${compose[@]}" exec -T orgmemory-docs \
    wget -q -T 5 -O /dev/null "http://127.0.0.1:3000$route"
}

check_public_route() {
  local route="$1"
  local status

  status="$(
    curl --fail --silent --show-error \
      --connect-timeout 5 \
      --max-time 15 \
      --output /dev/null \
      --write-out '%{http_code}' \
      "${public_url%/}$route"
  )"
  [[ "$status" == "200" ]]
}

check_public_root_redirect() {
  local result
  local expected_prefix

  result="$(
    curl --fail --location --silent --show-error \
      --connect-timeout 5 \
      --max-time 15 \
      --output /dev/null \
      --write-out '%{http_code} %{url_effective}' \
      "${public_url%/}/"
  )"
  expected_prefix="200 ${public_url%/}/docs/"
  [[ "$result" == "$expected_prefix"* ]]
}

wait_for_internal_health
for route in \
  "/docs/architecture-security/system-description" \
  "/api/search?query=OpenFGA" \
  "/llms.txt"; do
  check_internal_route "$route"
done

if [[ "$require_public_smoke" == "true" ]]; then
  if [[ ! "$public_url" =~ ^https://[^/]+$ ]]; then
    printf 'ORGMEMORY_DOCS_PUBLIC_URL must be an HTTPS origin.\n' >&2
    exit 64
  fi
  check_public_root_redirect
  for route in \
    "/docs/architecture-security/system-description" \
    "/api/search?query=OpenFGA" \
    "/llms.txt"; do
    check_public_route "$route"
  done
fi

printf 'OrgMemory docs smoke passed.\n'
