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
intervention_latch="${ORGMEMORY_DEPLOY_INTERVENTION_LATCH:-$runtime_root/deployment-intervention-required}"
model_file="${ORGMEMORY_OPENFGA_MODEL_FILE:-$repo_root/integrations/authorization-openfga/src/main/openfga/model.fga}"
keycloak_configuration_script="${ORGMEMORY_KEYCLOAK_CONFIGURATION_SCRIPT:-$repo_root/infrastructure/deployment/scripts/configure-keycloak-mcp.sh}"
keycloak_theme_configuration_script="${ORGMEMORY_KEYCLOAK_THEME_CONFIGURATION_SCRIPT:-$keycloak_configuration_script}"
smoke_script="${ORGMEMORY_SMOKE_SCRIPT:-$repo_root/infrastructure/deployment/scripts/smoke-production.sh}"
coordination_script="${ORGMEMORY_TEAM_DEV_COORDINATION_SCRIPT:-$repo_root/infrastructure/deployment/scripts/team-dev-coordination.sh}"
lock_file="$runtime_root/deploy.lock"
release_stamp="$(date -u +%Y%m%dT%H%M%SZ)"
release_environment="$runtime_root/releases/$release_stamp.env"
previous_environment="$runtime_root/releases/$release_stamp.previous.env"
previous_commit_file="$runtime_root/releases/$release_stamp.previous-commit"
current_commit_file="$runtime_root/current-commit"
had_previous_release=false
maintenance_acquired=false
previous_keycloak_login_theme="keycloak"
desired_keycloak_login_theme="${ORGMEMORY_KEYCLOAK_LOGIN_THEME:-orgmemory-shadcn}"
keycloak_theme_reconcile_attempted=false
maintenance_session="deploy-$commit_sha"

if [[ -s "$current_commit_file" ]]; then
  had_previous_release=true
fi

exec 9>"$lock_file"
if ! flock -n 9; then
  printf 'Another OrgMemory deployment is already running.\n' >&2
  exit 75
fi

if [[ "$intervention_latch" != "$runtime_root/deployment-intervention-required" ]]; then
  printf 'Deployment intervention latch path is invalid: %s\n' "$intervention_latch" >&2
  exit 65
fi
if [[ -e "$intervention_latch" ]]; then
  printf 'Production deployment requires operator intervention: %s\n' "$intervention_latch" >&2
  exit 78
fi

if [[ ! -f "$environment_file" ]]; then
  printf 'Missing production environment file: %s\n' "$environment_file" >&2
  exit 1
fi

release_maintenance() {
  if [[ "$maintenance_acquired" == "true" ]]; then
    "$coordination_script" release maintenance "$maintenance_session" || true
    maintenance_acquired=false
  fi
}

mark_intervention_required() {
  local reason="$1"
  local temporary_latch="$intervention_latch.$$"
  umask 077
  printf 'commit=%s reason=%s\n' "$commit_sha" "$reason" >"$temporary_latch"
  mv -f -- "$temporary_latch" "$intervention_latch"
}

rollback_abort() {
  local reason="$1"
  trap - ERR
  mark_intervention_required "$reason"
  printf 'Rollback failed; operator intervention is required: %s\n' \
    "$intervention_latch" >&2
  exit 70
}

rollback_failure() {
  local failed_line="$1"
  rollback_abort "rollback-failed-line-$failed_line"
}

shared_state_changed() {
  local previous_commit
  if [[ "${ORGMEMORY_FORCE_SHARED_MAINTENANCE:-}" == "true" ]]; then
    return 0
  fi
  if [[ "${ORGMEMORY_FORCE_SHARED_MAINTENANCE:-}" == "false" ]]; then
    return 1
  fi
  if [[ ! -s "$current_commit_file" ]]; then
    return 0
  fi
  previous_commit="$(tr -d '[:space:]' < "$current_commit_file")"
  if [[ ! "$previous_commit" =~ ^[0-9a-f]{40}$ ]] || \
     ! git -C "$repo_root" cat-file -e "$previous_commit^{commit}" 2>/dev/null; then
    return 0
  fi
  ! git -C "$repo_root" diff --quiet \
    "$previous_commit" "$commit_sha" -- \
    core/src/main/resources/db/migration \
    integrations/authorization-openfga/src/main/openfga/model.fga
}

if [[ "$(stat -c '%a' "$environment_file")" != "600" ]]; then
  printf 'Production environment file must have mode 0600: %s\n' \
    "$environment_file" >&2
  exit 1
fi

install -m 0600 "$environment_file" "$previous_environment"
if [[ "$had_previous_release" == "true" ]]; then
  install -m 0600 "$current_commit_file" "$previous_commit_file"
fi

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

replace_environment_value() {
  local path="$1"
  local key="$2"
  local value="$3"
  local temporary_file
  temporary_file="$(mktemp)"
  awk -v key="$key" -v value="$value" '
    BEGIN { replaced = 0 }
    index($0, key "=") == 1 {
      print key "=" value
      replaced = 1
      next
    }
    { print }
    END { if (!replaced) print key "=" value }
  ' "$path" >"$temporary_file"
  install -m 0600 "$temporary_file" "$path"
  rm -f "$temporary_file"
}

resolve_local_registry_digest() {
  local repository="$1"
  local repo_digests="$2"
  python3 - "$repository" "$repo_digests" <<'PY'
import json
import re
import sys

repository, payload = sys.argv[1:]
matches = [
    value for value in json.loads(payload)
    if re.fullmatch(re.escape(repository) + r"@sha256:[0-9a-f]{64}", value)
]
if len(matches) != 1:
    raise SystemExit(f"expected one local registry digest for {repository}, got {matches}")
print(matches[0])
PY
}

snapshot_previous_image_digest() {
  local key="$1"
  local component="$2"
  local service="$3"
  local destination_environment="${4:-$previous_environment}"
  local reference
  local repository="ghcr.io/kl3init/orgmemory-${component}"
  local -a container_ids=()
  local container_id
  local container_exit_code
  local container_state
  local running_image_id
  local repo_digests
  local immutable_reference
  reference="$(read_environment_value "$key")"
  if [[ "$reference" != "$repository@sha256:"* && \
        "$reference" != "$repository:sha-"* ]]; then
    printf 'Previous production image is not a supported release reference for %s: %s\n' \
      "$component" "$reference" >&2
    return 65
  fi

  mapfile -t container_ids < <(
    docker compose \
      --file "$compose_file" \
      --env-file "$environment_file" \
      ps --all --quiet "$service"
  )
  if [[ "${#container_ids[@]}" -eq 0 && "$service" == "postgres-bootstrap" ]]; then
    if [[ "$reference" != "$repository@sha256:"* ]]; then
      printf 'Previous one-shot service %s has no retained container and is not pinned by exact digest: %s\n' \
        "$service" "$reference" >&2
      return 65
    fi
    if ! repo_digests="$(
      docker image inspect "$reference" --format '{{json .RepoDigests}}'
    )"; then
      printf 'Previous one-shot image is not available locally for rollback: %s\n' \
        "$reference" >&2
      return 65
    fi
    if ! immutable_reference="$(
      resolve_local_registry_digest "$repository" "$repo_digests"
    )"; then
      return 65
    fi
    if [[ "$reference" != "$immutable_reference" ]]; then
      printf 'Previous environment digest does not match local image for %s: %s != %s\n' \
        "$service" "$reference" "$immutable_reference" >&2
      return 65
    fi
    replace_environment_value "$destination_environment" "$key" "$immutable_reference"
    return 0
  fi
  if [[ "${#container_ids[@]}" -ne 1 || -z "${container_ids[0]}" ]]; then
    printf 'Expected exactly one existing container for previous service %s, got %s\n' \
      "$service" "${#container_ids[@]}" >&2
    return 65
  fi
  container_id="${container_ids[0]}"
  container_state="$(
    docker container inspect "$container_id" \
      --format '{{.State.Status}} {{.State.Running}} {{.State.Restarting}}'
  )"
  if [[ "$service" == "postgres-bootstrap" ]]; then
    container_exit_code="$(
      docker container inspect "$container_id" --format '{{.State.ExitCode}}'
    )"
    if [[ "$container_state" != "exited false false" || "$container_exit_code" != 0 ]]; then
      printf 'One-shot service %s did not complete successfully: state=%s exit=%s\n' \
        "$service" "$container_state" "$container_exit_code" >&2
      return 65
    fi
  elif [[ "$container_state" != "running true false" ]]; then
    printf 'Service %s is not stably running: %s\n' "$service" "$container_state" >&2
    return 65
  fi
  running_image_id="$(docker container inspect "$container_id" --format '{{.Image}}')"
  if [[ ! "$running_image_id" =~ ^sha256:[0-9a-f]{64}$ ]]; then
    printf 'Previous service %s returned an invalid image id: %s\n' \
      "$service" "$running_image_id" >&2
    return 65
  fi
  repo_digests="$(docker image inspect "$running_image_id" --format '{{json .RepoDigests}}')"
  if ! immutable_reference="$(
    resolve_local_registry_digest "$repository" "$repo_digests"
  )"; then
    return 65
  fi
  if [[ "$reference" == "$repository@sha256:"* && "$reference" != "$immutable_reference" ]]; then
    printf 'Previous environment digest does not match service %s: %s != %s\n' \
      "$service" "$reference" "$immutable_reference" >&2
    return 65
  fi
  replace_environment_value "$destination_environment" "$key" "$immutable_reference"
}

verify_running_image_set() {
  snapshot_previous_image_digest ORGMEMORY_API_IMAGE api api "$environment_file"
  snapshot_previous_image_digest ORGMEMORY_WORKER_IMAGE worker worker "$environment_file"
  snapshot_previous_image_digest ORGMEMORY_MCP_IMAGE mcp mcp "$environment_file"
  snapshot_previous_image_digest ORGMEMORY_WEB_IMAGE web web "$environment_file"
  snapshot_previous_image_digest ORGMEMORY_KEYCLOAK_IMAGE keycloak keycloak "$environment_file"
  snapshot_previous_image_digest \
    ORGMEMORY_POSTGRES_IMAGE postgres-rag postgres-bootstrap "$environment_file"
}

validate_login_theme_in_image() {
  local image="$1"
  local theme="$2"
  local artifact
  case "$theme" in
    keycloak|keycloak.v2)
      return 0
      ;;
    orgmemory-shadcn)
      artifact=/opt/keycloak/providers/orgmemory-keycloak-theme.jar
      ;;
    *)
      if [[ ! "$theme" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*[A-Za-z0-9]$ || "$theme" == *..* ]]; then
        printf 'Previous Keycloak login theme is not a safe path segment: %s\n' "$theme" >&2
        return 65
      fi
      artifact="/opt/keycloak/themes/$theme/login/theme.properties"
      ;;
  esac
  if ! docker run --rm \
    --network none \
    --read-only \
    --cap-drop ALL \
    --entrypoint test \
    "$image" -r "$artifact"; then
    printf 'Candidate Keycloak image cannot render rollback theme %s (%s missing).\n' \
      "$theme" "$artifact" >&2
    return 65
  fi
}

# Pins everything this deployment derives from the released commit: the image set, and the
# version telemetry reports. They are written together because they must not disagree — a
# service.version that lags the running image is worse than none at all.
pin_release_values() {
  local temporary_file
  temporary_file="$(mktemp)"

  awk \
    -v api="$release_api_image" \
    -v worker="$release_worker_image" \
    -v mcp="$release_mcp_image" \
    -v web="$release_web_image" \
    -v keycloak="$release_keycloak_image" \
    -v postgres="$release_postgres_image" \
    -v sha="$commit_sha" '
    BEGIN {
      pinned["ORGMEMORY_API_IMAGE"] = api
      pinned["ORGMEMORY_WORKER_IMAGE"] = worker
      pinned["ORGMEMORY_MCP_IMAGE"] = mcp
      pinned["ORGMEMORY_WEB_IMAGE"] = web
      pinned["ORGMEMORY_KEYCLOAK_IMAGE"] = keycloak
      pinned["ORGMEMORY_POSTGRES_IMAGE"] = postgres
      pinned["ORGMEMORY_SERVICE_VERSION"] = sha
    }
    {
      split($0, parts, "=")
      if (parts[1] in pinned) {
        print parts[1] "=" pinned[parts[1]]
        seen[parts[1]] = 1
      } else {
        print
      }
    }
    END {
      for (key in pinned) {
        if (!seen[key]) {
          print key "=" pinned[key]
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
  local exit_code="${1:-$?}"
  trap 'rollback_failure "$LINENO"' ERR
  trap '' HUP INT TERM

  printf 'Deployment failed; restoring the previous immutable image set.\n' >&2
  # Never retain a commit marker while rollback is incomplete. Restore it only
  # after previous images, theme and smoke have all been verified.
  if ! rm -f -- "$current_commit_file"; then
    rollback_abort "commit-marker-invalidation-failed"
  fi
  if [[ "$had_previous_release" == "true" && \
        "$keycloak_theme_reconcile_attempted" == "true" ]]; then
    if ! ORGMEMORY_ENV_FILE="$environment_file" \
      ORGMEMORY_KEYCLOAK_LOGIN_THEME="$previous_keycloak_login_theme" \
        "$keycloak_theme_configuration_script" --restore-login-theme; then
      printf '%s\n' \
        'Keycloak login theme restoration failed; refusing to start the previous image set.' \
        "Candidate containers and environment remain in place for operator recovery; backup: $previous_environment" >&2
      rollback_abort "keycloak-theme-restore-failed"
    fi
  fi
  if ! install -m 0600 "$previous_environment" "$environment_file"; then
    rollback_abort "environment-restore-failed"
  fi
  if [[ "$had_previous_release" == "true" ]]; then
    if ! docker compose \
      --file "$compose_file" \
      --env-file "$environment_file" \
      up --pull never -d --wait --wait-timeout 240 --remove-orphans; then
      rollback_abort "compose-restore-failed"
    fi
    if ! verify_running_image_set; then
      rollback_abort "restored-image-set-invalid"
    fi
    if ! ORGMEMORY_ENV_FILE="$environment_file" \
    ORGMEMORY_KEYCLOAK_LOGIN_THEME="$previous_keycloak_login_theme" \
    ORGMEMORY_REQUIRE_PUBLIC_SMOKE="${public_smoke:-true}" \
      "$smoke_script"; then
      rollback_abort "restored-runtime-smoke-failed"
    fi
    if ! verify_running_image_set; then
      rollback_abort "restored-image-set-unstable"
    fi
  else
    if ! docker compose \
      --file "$compose_file" \
      --env-file "$environment_file" \
      down --remove-orphans; then
      rollback_abort "candidate-shutdown-failed"
    fi
  fi
  if [[ "$had_previous_release" == "true" ]]; then
    if ! install -m 0600 "$previous_commit_file" "$current_commit_file"; then
      rollback_abort "commit-marker-restore-failed"
    fi
  else
    if ! rm -f -- "$current_commit_file"; then
      rollback_abort "commit-marker-removal-failed"
    fi
  fi
  trap - ERR
  exit "$exit_code"
}

await_external_gate() {
  local gate_directory="${ORGMEMORY_EXTERNAL_GATE_DIRECTORY:-}"
  local rejection_marker
  local attempt
  [[ -n "$gate_directory" ]] || return 0
  case "$gate_directory" in
    /tmp/orgmemory-deploy-gate.*)
      ;;
    *)
      printf 'Refusing unsafe external deployment gate path: %s\n' "$gate_directory" >&2
      return 65
      ;;
  esac
  rejection_marker="$gate_directory.rejected"
  if [[ -f "$rejection_marker" ]]; then
    rm -f -- "$rejection_marker"
    printf 'External rendered-login deployment gate was cancelled before readiness.\n' >&2
    return 1
  fi
  umask 077
  mkdir --mode=0700 "$gate_directory"
  : >"$gate_directory/ready"
  for ((attempt = 0; attempt < 300; attempt += 1)); do
    if [[ -f "$rejection_marker" ]]; then
      rm -rf -- "$gate_directory"
      rm -f -- "$rejection_marker"
      printf 'External rendered-login deployment gate rejected the candidate.\n' >&2
      return 1
    fi
    if [[ -L "$gate_directory/decision" ]]; then
      case "$(readlink "$gate_directory/decision")" in
        approved)
          rm -rf -- "$gate_directory"
          return 0
          ;;
        rejected)
          rm -rf -- "$gate_directory"
          printf 'External rendered-login deployment gate rejected the candidate.\n' >&2
          return 1
          ;;
        *)
          rm -rf -- "$gate_directory"
          printf 'External rendered-login deployment gate received an invalid decision.\n' >&2
          return 1
          ;;
      esac
    fi
    sleep 1
  done
  rm -rf -- "$gate_directory"
  rm -f -- "$rejection_marker"
  printf 'External rendered-login deployment gate timed out.\n' >&2
  return 1
}

release_api_image="${ORGMEMORY_RELEASE_API_IMAGE:-}"
release_worker_image="${ORGMEMORY_RELEASE_WORKER_IMAGE:-}"
release_mcp_image="${ORGMEMORY_RELEASE_MCP_IMAGE:-}"
release_web_image="${ORGMEMORY_RELEASE_WEB_IMAGE:-}"
release_keycloak_image="${ORGMEMORY_RELEASE_KEYCLOAK_IMAGE:-}"
release_postgres_image="${ORGMEMORY_RELEASE_POSTGRES_IMAGE:-}"

validate_release_image() {
  local component="$1"
  local reference="$2"
  if [[ ! "$reference" =~ ^ghcr\.io/kl3init/orgmemory-${component}@sha256:[0-9a-f]{64}$ ]]; then
    printf 'Invalid immutable image reference for %s: %s\n' "$component" "$reference" >&2
    exit 65
  fi
}

validate_release_image api "$release_api_image"
validate_release_image worker "$release_worker_image"
validate_release_image mcp "$release_mcp_image"
validate_release_image web "$release_web_image"
validate_release_image keycloak "$release_keycloak_image"
validate_release_image postgres-rag "$release_postgres_image"

if [[ "$had_previous_release" == "true" ]]; then
  snapshot_previous_image_digest ORGMEMORY_API_IMAGE api api
  snapshot_previous_image_digest ORGMEMORY_WORKER_IMAGE worker worker
  snapshot_previous_image_digest ORGMEMORY_MCP_IMAGE mcp mcp
  snapshot_previous_image_digest ORGMEMORY_WEB_IMAGE web web
  snapshot_previous_image_digest ORGMEMORY_KEYCLOAK_IMAGE keycloak keycloak
  snapshot_previous_image_digest ORGMEMORY_POSTGRES_IMAGE postgres-rag postgres-bootstrap
  previous_keycloak_login_theme="$(
    ORGMEMORY_ENV_FILE="$environment_file" \
      "$keycloak_theme_configuration_script" --print-login-theme
  )"
fi

for release_image in \
  "$release_api_image" \
  "$release_worker_image" \
  "$release_mcp_image" \
  "$release_web_image" \
  "$release_keycloak_image" \
  "$release_postgres_image"; do
  docker pull "$release_image"
done

if [[ "$had_previous_release" == "true" ]]; then
  validate_login_theme_in_image \
    "$release_keycloak_image" "$previous_keycloak_login_theme"
fi

trap rollback ERR
trap 'rollback 129' HUP
trap 'rollback 130' INT
trap 'rollback 143' TERM
trap release_maintenance EXIT

# A rollback target that uses a stock theme must be reconciled while the current
# image still runs; the old stock image may not contain the current custom theme.
if [[ "$had_previous_release" == "true" && \
      "$previous_keycloak_login_theme" != "$desired_keycloak_login_theme" && \
      "$desired_keycloak_login_theme" =~ ^keycloak(\.v2)?$ ]]; then
  keycloak_theme_reconcile_attempted=true
  ORGMEMORY_ENV_FILE="$environment_file" \
  ORGMEMORY_KEYCLOAK_LOGIN_THEME="$desired_keycloak_login_theme" \
    "$keycloak_theme_configuration_script" --login-theme-only
fi

pin_release_values

openfga_store_id="$(read_environment_value ORGMEMORY_OPENFGA_STORE_ID)"
openfga_model_id="$(read_environment_value ORGMEMORY_OPENFGA_AUTHORIZATION_MODEL_ID)"
openfga_model_sha256="$(read_environment_value ORGMEMORY_OPENFGA_MODEL_SHA256)"
release_model_sha256="$(sha256sum "$model_file" | awk '{ print $1 }')"
public_smoke="${ORGMEMORY_REQUIRE_PUBLIC_SMOKE:-$(read_environment_value ORGMEMORY_REQUIRE_PUBLIC_SMOKE)}"

if [[ -z "$openfga_store_id" || -z "$openfga_model_id" ]]; then
  printf 'OpenFGA identifiers are missing. Run bootstrap-openfga.sh first.\n' >&2
  exit 78
fi

if shared_state_changed; then
  "$coordination_script" acquire \
    maintenance \
    "$maintenance_session" \
    deployment \
    deployment-host \
    "$commit_sha" \
    1800
  maintenance_acquired=true
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

# The published-batch AGE repair is an explicit one-shot operation. Stop the
# previous writer before its preflight and keep the normal API down until the
# repair exits successfully; service dependencies only order new containers and
# cannot fence a worker from the previous release.
"${compose[@]}" stop --timeout 45 worker api
"${compose[@]}" --profile ops run --rm --no-deps age-reconcile

"${compose[@]}" up \
  -d \
  --wait \
  --wait-timeout 240 \
  --remove-orphans

verify_running_image_set

keycloak_theme_reconcile_attempted=true
ORGMEMORY_ENV_FILE="$environment_file" \
  "$keycloak_configuration_script"

ORGMEMORY_ENV_FILE="$environment_file" \
ORGMEMORY_KEYCLOAK_LOGIN_THEME="$desired_keycloak_login_theme" \
ORGMEMORY_REQUIRE_PUBLIC_SMOKE="${public_smoke:-true}" \
  "$smoke_script"

# Re-check after reconciliation and smoke so a non-healthchecked worker that
# immediately stopped or entered Docker's restart state cannot be committed.
verify_running_image_set

await_external_gate

printf '%s\n' "$commit_sha" > "$current_commit_file"
release_maintenance
trap - ERR
trap - HUP INT TERM
printf 'Deployed OrgMemory commit %s.\n' "$commit_sha"
