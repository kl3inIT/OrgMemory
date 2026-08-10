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

[[ -f "$state_directory/controller-started" ]]
[[ ! -f "$state_directory/status" ]]
owner="$(readlink "$state_directory/ownership" 2>/dev/null || true)"
[[ "$owner" =~ ^controller\.([1-9][0-9]*)\.([1-9][0-9]*)$ ]]
controller_pid="${BASH_REMATCH[1]}"
expected_starttime="${BASH_REMATCH[2]}"
[[ -r "/proc/$controller_pid/stat" ]]
observed_starttime="$(cut -d ' ' -f 22 "/proc/$controller_pid/stat")"
[[ "$observed_starttime" == "$expected_starttime" ]]
kill -0 "$controller_pid"

# The controller and every deployed descendant inherit this lease. Acquiring it
# here would prove the purported ACTIVE owner is already dead.
exec 197>"$state_directory/ownership.lease"
if flock -n 197; then
  printf 'Detached deployment ACTIVE marker has no live lease: %s\n' "$state_directory" >&2
  exit 75
fi
