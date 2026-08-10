#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "$#" -ne 1 ]]; then
  printf 'Usage: %s <state-directory>\n' "$0" >&2
  exit 64
fi
state_directory="${1%/}"
case "$state_directory" in
  /tmp/orgmemory-deploy-state.*) ;;
  *)
    printf 'Refusing unsafe detached deployment state directory: %s\n' "$state_directory" >&2
    exit 65
    ;;
esac

current_uid="$(id -u)"
[[ -d "$state_directory" && ! -L "$state_directory" ]]
[[ "$(stat -c '%u:%a' "$state_directory")" == "$current_uid:700" ]]
[[ -f "$state_directory/controller-started" &&
  ! -L "$state_directory/controller-started" ]]
[[ "$(stat -c '%u:%a' "$state_directory/controller-started")" == "$current_uid:600" ]]
[[ -L "$state_directory/ownership" ]]
[[ "$(stat -c '%u' "$state_directory/ownership")" == "$current_uid" ]]
owner="$(readlink "$state_directory/ownership" 2>/dev/null || true)"
[[ "$owner" =~ ^controller\.([1-9][0-9]*)\.([1-9][0-9]*)$ ]]
controller_pid="${BASH_REMATCH[1]}"
expected_starttime="${BASH_REMATCH[2]}"

verify_terminal_status() {
  [[ ! -L "$state_directory/status" ]] || return 1
  [[ -f "$state_directory/status" ]] || return 1
  [[ "$(stat -c '%u:%a' "$state_directory/status")" == "$current_uid:600" ]] || return 1
  deployment_status=
  IFS= read -r deployment_status <"$state_directory/status" || return 1
  [[ "$deployment_status" =~ ^(0|[1-9][0-9]{0,2})$ ]] || return 1
  (( deployment_status <= 255 )) || return 1
  [[ "$(stat -c '%s' "$state_directory/status")" -eq "$((${#deployment_status} + 1))" ]] || return 1
  [[ ! -e "$state_directory/docker-config" && ! -L "$state_directory/docker-config" ]]
}

# A fast deployment can publish its final status between the launcher's marker
# poll and this verification. A present status path is always terminal state and
# must be the exact regular-file output of a credential-clean finalizer.
if [[ -e "$state_directory/status" || -L "$state_directory/status" ]]; then
  verify_terminal_status
  exit 0
fi

# The controller and every deployed descendant inherit this lease. If it is
# released during these active checks, status was published first by the finalizer;
# validate that terminal state once more before rejecting the transition.
if [[ -r "/proc/$controller_pid/stat" ]] &&
  observed_starttime="$(cut -d ' ' -f 22 "/proc/$controller_pid/stat")" &&
  [[ "$observed_starttime" == "$expected_starttime" ]] &&
  kill -0 "$controller_pid"; then
  exec 197>"$state_directory/ownership.lease"
  if ! flock -n 197; then
    if [[ -e "$state_directory/status" || -L "$state_directory/status" ]]; then
      verify_terminal_status
    fi
    exit 0
  fi
fi

if [[ -e "$state_directory/status" || -L "$state_directory/status" ]]; then
  verify_terminal_status
  exit 0
fi

printf 'Detached deployment has neither a qualified ACTIVE lease nor terminal status: %s\n' \
  "$state_directory" >&2
exit 75
