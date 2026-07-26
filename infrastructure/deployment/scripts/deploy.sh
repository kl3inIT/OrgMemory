#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "$#" -ne 1 || ! "$1" =~ ^[0-9a-f]{40}$ ]]; then
  printf 'Usage: %s <full-commit-sha>\n' "$0" >&2
  exit 64
fi

commit_sha="$1"
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
compose_file="$repo_root/infrastructure/deployment/compose.production.yaml"
environment_file="${ORGMEMORY_ENV_FILE:-$repo_root/.env.production}"
runtime_root="${ORGMEMORY_RUNTIME_ROOT:-/apps/orgmemory-runtime}"
lock_file="$runtime_root/deploy.lock"
release_stamp="$(date -u +%Y%m%dT%H%M%SZ)"
release_environment="$runtime_root/releases/$release_stamp.env"
previous_environment="$runtime_root/releases/$release_stamp.previous.env"
current_commit_file="$runtime_root/current-commit"
had_previous_release=false

if [[ -s "$current_commit_file" ]]; then
  had_previous_release=true
fi

exec 9>"$lock_file"
if ! flock -n 9; then
  printf 'Another OrgMemory deployment is already running.\n' >&2
  exit 75
fi

if [[ ! -f "$environment_file" ]]; then
  printf 'Missing production environment file: %s\n' "$environment_file" >&2
  exit 1
fi

if [[ "$(stat -c '%a' "$environment_file")" != "600" ]]; then
  printf 'Production environment file must have mode 0600: %s\n' \
    "$environment_file" >&2
  exit 1
fi

install -m 0600 "$environment_file" "$previous_environment"

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
        if key.strip() != wanted:
            continue
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
            value = value[1:-1]
        print(value)
        break
PY
}

replace_image_references() {
  local temporary_file
  temporary_file="$(mktemp)"

  awk -v sha="$commit_sha" '
    BEGIN {
      images["ORGMEMORY_API_IMAGE"] = "ghcr.io/kl3init/orgmemory-api:sha-" sha
      images["ORGMEMORY_WORKER_IMAGE"] = "ghcr.io/kl3init/orgmemory-worker:sha-" sha
      images["ORGMEMORY_MCP_IMAGE"] = "ghcr.io/kl3init/orgmemory-mcp:sha-" sha
      images["ORGMEMORY_WEB_IMAGE"] = "ghcr.io/kl3init/orgmemory-web:sha-" sha
      images["ORGMEMORY_KEYCLOAK_IMAGE"] = "ghcr.io/kl3init/orgmemory-keycloak:sha-" sha
      images["ORGMEMORY_POSTGRES_IMAGE"] = "ghcr.io/kl3init/orgmemory-postgres-rag:sha-" sha
    }
    {
      split($0, parts, "=")
      if (parts[1] in images) {
        print parts[1] "=" images[parts[1]]
        seen[parts[1]] = 1
      } else {
        print
      }
    }
    END {
      for (key in images) {
        if (!seen[key]) {
          print key "=" images[key]
        }
      }
    }
  ' "$environment_file" > "$temporary_file"

  install -m 0600 "$temporary_file" "$environment_file"
  rm -f "$temporary_file"
}

rollback() {
  local exit_code="$?"
  trap - ERR

  printf 'Deployment failed; restoring the previous immutable image set.\n' >&2
  install -m 0600 "$previous_environment" "$environment_file"
  if [[ "$had_previous_release" == "true" ]]; then
    docker compose \
      --file "$compose_file" \
      --env-file "$environment_file" \
      pull
    docker compose \
      --file "$compose_file" \
      --env-file "$environment_file" \
      up -d --remove-orphans
  else
    docker compose \
      --file "$compose_file" \
      --env-file "$environment_file" \
      down --remove-orphans
  fi
  exit "$exit_code"
}

trap rollback ERR

replace_image_references
install -m 0600 "$environment_file" "$release_environment"

openfga_store_id="$(read_environment_value ORGMEMORY_OPENFGA_STORE_ID)"
openfga_model_id="$(read_environment_value ORGMEMORY_OPENFGA_AUTHORIZATION_MODEL_ID)"
public_smoke="${ORGMEMORY_REQUIRE_PUBLIC_SMOKE:-$(read_environment_value ORGMEMORY_REQUIRE_PUBLIC_SMOKE)}"

if [[ -z "$openfga_store_id" || -z "$openfga_model_id" ]]; then
  printf 'OpenFGA identifiers are missing. Run bootstrap-openfga.sh first.\n' >&2
  exit 78
fi

compose=(
  docker compose
  --file "$compose_file"
  --env-file "$environment_file"
)

"${compose[@]}" config --quiet
"${compose[@]}" pull
"${compose[@]}" run --rm postgres-bootstrap
"${compose[@]}" --profile ops run --rm postgres-backup
"${compose[@]}" up \
  -d \
  --wait \
  --wait-timeout 240 \
  --remove-orphans

ORGMEMORY_ENV_FILE="$environment_file" \
  "$repo_root/infrastructure/deployment/scripts/configure-keycloak-mcp.sh"

ORGMEMORY_ENV_FILE="$environment_file" \
ORGMEMORY_REQUIRE_PUBLIC_SMOKE="${public_smoke:-true}" \
  "$repo_root/infrastructure/deployment/scripts/smoke-production.sh"

printf '%s\n' "$commit_sha" > "$current_commit_file"
trap - ERR
printf 'Deployed OrgMemory commit %s.\n' "$commit_sha"
