#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "$#" -ne 1 || ! "$1" =~ ^[0-9a-f]{40}$ ]]; then
  printf 'Usage: %s <full-commit-sha>\n' "$0" >&2
  exit 64
fi

commit_sha="$1"
repo_root="${ORGMEMORY_REPO_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)}"
compose_file="${ORGMEMORY_COMPOSE_FILE:-$repo_root/infrastructure/deployment/compose.production.yaml}"
environment_file="${ORGMEMORY_ENV_FILE:-$repo_root/.env.production}"
runtime_root="${ORGMEMORY_RUNTIME_ROOT:-/apps/orgmemory-runtime}"
model_file="${ORGMEMORY_OPENFGA_MODEL_FILE:-$repo_root/integrations/authorization-openfga/src/main/openfga/model.fga}"
keycloak_configuration_script="${ORGMEMORY_KEYCLOAK_CONFIGURATION_SCRIPT:-$repo_root/infrastructure/deployment/scripts/configure-keycloak-mcp.sh}"
smoke_script="${ORGMEMORY_SMOKE_SCRIPT:-$repo_root/infrastructure/deployment/scripts/smoke-production.sh}"
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

update_openfga_model_configuration() {
  local model_id="$1"
  local model_sha256="$2"
  local temporary_file
  temporary_file="$(mktemp)"

  awk -v model_id="$model_id" -v model_sha256="$model_sha256" '
    BEGIN {
      values["ORGMEMORY_OPENFGA_AUTHORIZATION_MODEL_ID"] = model_id
      values["ORGMEMORY_OPENFGA_MODEL_SHA256"] = model_sha256
    }
    {
      split($0, parts, "=")
      if (parts[1] in values) {
        print parts[1] "=" values[parts[1]]
        seen[parts[1]] = 1
      } else {
        print
      }
    }
    END {
      for (key in values) {
        if (!seen[key]) {
          print key "=" values[key]
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

openfga_store_id="$(read_environment_value ORGMEMORY_OPENFGA_STORE_ID)"
openfga_model_id="$(read_environment_value ORGMEMORY_OPENFGA_AUTHORIZATION_MODEL_ID)"
openfga_model_sha256="$(read_environment_value ORGMEMORY_OPENFGA_MODEL_SHA256)"
release_model_sha256="$(sha256sum "$model_file" | awk '{ print $1 }')"
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
"${compose[@]}" up -d openfga
"${compose[@]}" run --rm --no-deps openfga-ready

if [[ "$openfga_model_sha256" != "$release_model_sha256" ]]; then
  model_write_json="$(
    "${compose[@]}" --profile ops run --rm --no-deps openfga-model-write
  )"
  new_openfga_model_id="$(
    MODEL_WRITE_JSON="$model_write_json" python3 - <<'PY'
import json
import os

payload = json.loads(os.environ["MODEL_WRITE_JSON"])
model_id = payload.get("authorization_model_id") or payload.get("id")
if not model_id:
    raise SystemExit("OpenFGA CLI response did not contain an authorization model id")
print(model_id)
PY
  )"
  update_openfga_model_configuration \
    "$new_openfga_model_id" \
    "$release_model_sha256"
fi

install -m 0600 "$environment_file" "$release_environment"

"${compose[@]}" up \
  -d \
  --wait \
  --wait-timeout 240 \
  --remove-orphans

ORGMEMORY_ENV_FILE="$environment_file" \
  "$keycloak_configuration_script"

ORGMEMORY_ENV_FILE="$environment_file" \
ORGMEMORY_REQUIRE_PUBLIC_SMOKE="${public_smoke:-true}" \
  "$smoke_script"

printf '%s\n' "$commit_sha" > "$current_commit_file"
trap - ERR
printf 'Deployed OrgMemory commit %s.\n' "$commit_sha"
