#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "$#" -ne 1 ]]; then
  printf 'Usage: %s <deployment-state-directory>\n' "$0" >&2
  exit 64
fi

state_directory="$1"
case "$state_directory" in
  /tmp/orgmemory-deploy-state.*)
    ;;
  *)
    printf 'Refusing unsafe deployment state path: %s\n' "$state_directory" >&2
    exit 65
    ;;
esac

environment_file="$state_directory/launch.env"
log_file="$state_directory/deploy.log"
status_file="$state_directory/status"
status_temporary="$state_directory/status.$$"
ownership_file="$state_directory/ownership"
lease_file="$state_directory/ownership.lease"
controller_pid="$BASHPID"
controller_starttime="$(cut -d ' ' -f 22 "/proc/$controller_pid/stat")"
ownership_target="controller.$controller_pid.$controller_starttime"
deployment_status=""

# shellcheck disable=SC2329  # Invoked by the EXIT-trap finalizer below.
record_intervention() {
  local reason="$1"
  local latch="${DEPLOY_INTERVENTION_LATCH:-/apps/orgmemory-runtime/deployment-intervention-required}"
  case "$latch" in
    /apps/orgmemory-runtime/deployment-intervention-required | \
      /tmp/orgmemory-deploy-intervention-required.test.*) ;;
    *) return 1 ;;
  esac
  if ! printf '%s state=%s status=%s\n' "$reason" "$state_directory" \
    "${deployment_status:-unknown}" >"$latch.$BASHPID"; then
    return 1
  fi
  if ! mv -f -- "$latch.$BASHPID" "$latch"; then
    rm -f -- "$latch.$BASHPID" || true
    return 1
  fi
}

# shellcheck disable=SC2329  # Invoked by the EXIT trap below.
finalize() {
  local exit_code="$?"
  trap - EXIT
  set +e
  if [[ "$(readlink "$ownership_file" 2>/dev/null || true)" != "$ownership_target" ]]; then
    return
  fi
  if [[ -z "$deployment_status" ]]; then
    deployment_status="$exit_code"
  fi
  if [[ "$deployment_status" == 137 ]]; then
    if ! record_intervention hard-timeout; then
      exit 74
    fi
  fi
  if ! rm -rf -- "$state_directory/docker-config"; then
    record_intervention credential-cleanup-failed || true
    exit 74
  fi
  if ! printf '%s\n' "$deployment_status" >"$status_temporary"; then
    record_intervention terminal-status-write-failed || true
    exit 74
  fi
  if ! mv -f -- "$status_temporary" "$status_file"; then
    rm -f -- "$status_temporary" || true
    record_intervention terminal-status-publish-failed || true
    exit 74
  fi
  return 0
}
trap finalize EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

if [[ "$(readlink "/proc/$controller_pid/fd/198" 2>/dev/null || true)" == "$lease_file" ]]; then
  if ! flock -n 198; then
    printf 'Inherited detached deployment lease is not held: %s\n' "$state_directory" >&2
    exit 75
  fi
elif ! exec 198>"$lease_file" || ! flock -n 198; then
  printf 'Detached deployment cleanup already leases state: %s\n' "$state_directory" >&2
  exit 75
fi
if ! ln -s "$ownership_target" "$ownership_file" 2>/dev/null; then
  printf 'Detached deployment cleanup already owns state: %s\n' "$state_directory" >&2
  exit 75
fi

# Handshake for the remote launcher: this marker is published only after the
# credential/status finalizer is active and controller ownership is atomic.
touch "$state_directory/controller-started"

if [[ ! -f "$environment_file" || "$(stat -c '%a' "$environment_file")" != "600" ]]; then
  printf 'Detached deployment environment must exist with mode 0600: %s\n' \
    "$environment_file" >&2
  exit 65
fi

# The file is generated from fixed names and shell-escaped, previously validated
# workflow values. It never contains registry, SSH, or application credentials.
# shellcheck disable=SC1090
source "$environment_file"

required_variables=(
  COMMIT_SHA
  DEPLOY_INTERVENTION_LATCH
  DEPLOY_KILL_AFTER_SECONDS
  DEPLOY_TIMEOUT_SECONDS
  DOCKER_CONFIG
  ORGMEMORY_EXTERNAL_GATE_DIRECTORY
  ORGMEMORY_KEYCLOAK_CONFIGURATION_SCRIPT
  ORGMEMORY_KEYCLOAK_LOGIN_THEME
  ORGMEMORY_KEYCLOAK_THEME_CONFIGURATION_SCRIPT
  ORGMEMORY_RELEASE_API_IMAGE
  ORGMEMORY_RELEASE_KEYCLOAK_IMAGE
  ORGMEMORY_RELEASE_MCP_IMAGE
  ORGMEMORY_RELEASE_POSTGRES_IMAGE
  ORGMEMORY_RELEASE_WEB_IMAGE
  ORGMEMORY_RELEASE_WORKER_IMAGE
  ORGMEMORY_REPO_ROOT
  ORGMEMORY_SMOKE_SCRIPT
  ORGMEMORY_TEAM_DEV_COORDINATION_SCRIPT
  TRUSTED_DEPLOY_SCRIPT
)
for variable in "${required_variables[@]}"; do
  if [[ -z "${!variable:-}" ]]; then
    printf 'Detached deployment variable is missing: %s\n' "$variable" >&2
    exit 65
  fi
done
if [[ ! "$COMMIT_SHA" =~ ^[0-9a-f]{40}$ ||
      ! "$DEPLOY_KILL_AFTER_SECONDS" =~ ^[1-9][0-9]{0,3}$ ||
      ! "$DEPLOY_TIMEOUT_SECONDS" =~ ^[1-9][0-9]{0,3}$ ]]; then
  printf 'Detached deployment commit or timeout is invalid.\n' >&2
  exit 65
fi
case "$DEPLOY_INTERVENTION_LATCH" in
  /apps/orgmemory-runtime/deployment-intervention-required | \
    /tmp/orgmemory-deploy-intervention-required.test.*)
    ;;
  *)
    printf 'Detached deployment intervention latch path is invalid.\n' >&2
    exit 65
    ;;
esac

status=0
set +e
timeout \
  --signal=TERM \
  --kill-after="${DEPLOY_KILL_AFTER_SECONDS}s" \
  "${DEPLOY_TIMEOUT_SECONDS}s" \
  env \
    DOCKER_CONFIG="$DOCKER_CONFIG" \
    ORGMEMORY_DEPLOY_INTERVENTION_LATCH="$DEPLOY_INTERVENTION_LATCH" \
    ORGMEMORY_REPO_ROOT="$ORGMEMORY_REPO_ROOT" \
    ORGMEMORY_KEYCLOAK_CONFIGURATION_SCRIPT="$ORGMEMORY_KEYCLOAK_CONFIGURATION_SCRIPT" \
    ORGMEMORY_KEYCLOAK_THEME_CONFIGURATION_SCRIPT="$ORGMEMORY_KEYCLOAK_THEME_CONFIGURATION_SCRIPT" \
    ORGMEMORY_SMOKE_SCRIPT="$ORGMEMORY_SMOKE_SCRIPT" \
    ORGMEMORY_TEAM_DEV_COORDINATION_SCRIPT="$ORGMEMORY_TEAM_DEV_COORDINATION_SCRIPT" \
    ORGMEMORY_EXTERNAL_GATE_DIRECTORY="$ORGMEMORY_EXTERNAL_GATE_DIRECTORY" \
    ORGMEMORY_KEYCLOAK_LOGIN_THEME="$ORGMEMORY_KEYCLOAK_LOGIN_THEME" \
    ORGMEMORY_RELEASE_API_IMAGE="$ORGMEMORY_RELEASE_API_IMAGE" \
    ORGMEMORY_RELEASE_WORKER_IMAGE="$ORGMEMORY_RELEASE_WORKER_IMAGE" \
    ORGMEMORY_RELEASE_MCP_IMAGE="$ORGMEMORY_RELEASE_MCP_IMAGE" \
    ORGMEMORY_RELEASE_WEB_IMAGE="$ORGMEMORY_RELEASE_WEB_IMAGE" \
    ORGMEMORY_RELEASE_KEYCLOAK_IMAGE="$ORGMEMORY_RELEASE_KEYCLOAK_IMAGE" \
    ORGMEMORY_RELEASE_POSTGRES_IMAGE="$ORGMEMORY_RELEASE_POSTGRES_IMAGE" \
    "$TRUSTED_DEPLOY_SCRIPT" "$COMMIT_SHA" \
    >"$log_file" 2>&1
status=$?
set -e

deployment_status="$status"
exit 0
