#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

if [[ "$#" -ne 5 || ! "$3" =~ ^[1-9][0-9]{0,3}$ || ! "$5" =~ ^[1-9][0-9]{0,4}$ ]]; then
  printf 'Usage: %s <state-directory> <gate-directory> <launch-timeout-seconds> <intervention-latch> <controller-timeout-seconds>\n' "$0" >&2
  exit 64
fi

state_directory="${1%/}"
gate_directory="${2%/}"
launch_timeout_seconds="$3"
intervention_latch="$4"
controller_timeout_seconds="$5"
reconciler_ready_marker="${ORGMEMORY_RECONCILER_READY_MARKER:-}"
case "$state_directory" in
  /tmp/orgmemory-deploy-state.*) ;;
  *)
    printf 'Refusing unsafe detached deployment state directory: %s\n' "$state_directory" >&2
    exit 65
    ;;
esac
case "$gate_directory" in
  /tmp/orgmemory-deploy-gate.*) ;;
  *)
    printf 'Refusing unsafe detached deployment gate directory: %s\n' "$gate_directory" >&2
    exit 65
    ;;
esac
case "$intervention_latch" in
  /apps/orgmemory-runtime/deployment-intervention-required | \
    /tmp/orgmemory-deploy-intervention-required.test.*) ;;
  *)
    printf 'Refusing unsafe intervention latch path: %s\n' "$intervention_latch" >&2
    exit 65
    ;;
esac
if [[ -n "$reconciler_ready_marker" ]]; then
  case "$reconciler_ready_marker" in
    "$state_directory"/reconciler-ready.1 | "$state_directory"/reconciler-ready.2) ;;
    *)
      printf 'Refusing unsafe reconciler ready marker: %s\n' "$reconciler_ready_marker" >&2
      exit 65
      ;;
  esac
fi

# A launcher may hold FD198 for the controller handoff. Reconciler children must
# never retain that shared open-file description or they would block FD197 cleanup.
exec 198>&-

ownership_file="$state_directory/ownership"
lease_file="$state_directory/ownership.lease"
cleanup_request="$state_directory.cleanup-requested"
controller_started_file="$state_directory/controller-started"
status_file="$state_directory/status"
signal_helper="$state_directory/signal-qualified-process.py"
deploy_checkout_file="$state_directory/deploy-checkout"
controller_elapsed=0
terminal_elapsed=0
intervention_written=false

controller_is_alive() {
  local owner="$1"
  local pid expected_starttime observed_starttime
  if [[ ! "$owner" =~ ^controller\.([1-9][0-9]*)\.([1-9][0-9]*)$ ]]; then
    return 1
  fi
  pid="${BASH_REMATCH[1]}"
  expected_starttime="${BASH_REMATCH[2]}"
  [[ -r "/proc/$pid/stat" ]] || return 1
  observed_starttime="$(cut -d ' ' -f 22 "/proc/$pid/stat" 2>/dev/null || true)"
  [[ "$observed_starttime" == "$expected_starttime" ]]
}

write_intervention_latch() {
  local reason="$1"
  [[ "$intervention_written" == true ]] && return 0
  if ! printf '%s state=%s\n' "$reason" "$state_directory" >"$intervention_latch.$BASHPID"; then
    return 1
  fi
  if ! mv -f -- "$intervention_latch.$BASHPID" "$intervention_latch"; then
    rm -f -- "$intervention_latch.$BASHPID" || true
    return 1
  fi
  intervention_written=true
}

publish_gate_rejection() {
  if [[ -d "$gate_directory" ]]; then
    ln -s rejected "$gate_directory/decision" 2>/dev/null || \
      [[ "$(readlink "$gate_directory/decision" 2>/dev/null || true)" == rejected ]]
  else
    touch "$gate_directory.rejected"
  fi
}

cleanup_tombstones_if_detached() {
  local tombstone
  [[ ! -e "$state_directory" ]] || return 1
  for tombstone in "$state_directory.cleanup" "$state_directory.cleanup."*; do
    [[ -e "$tombstone" || -L "$tombstone" ]] || continue
    if ! rm -rf -- "$tombstone"; then
      return 1
    fi
  done
  if ! rm -rf -- "$gate_directory"; then
    return 1
  fi
  if ! rm -f -- "$gate_directory.rejected" "$cleanup_request"; then
    return 1
  fi
  return 0
}

publish_dead_active_status() {
  if ! write_intervention_latch 'controller-died-after-active'; then
    return 1
  fi
  if ! rm -rf -- "$state_directory/docker-config"; then
    return 1
  fi
  if ! printf '137\n' >"$state_directory/status.$BASHPID"; then
    return 1
  fi
  if ! mv -f -- "$state_directory/status.$BASHPID" "$status_file"; then
    rm -f -- "$state_directory/status.$BASHPID" || true
    return 1
  fi
  return 0
}

acquire_cleanup_lease() {
  if ! exec 197>"$lease_file" 2>/dev/null; then
    return 1
  fi
  if ! flock -n 197; then
    exec 197>&-
    return 1
  fi
  return 0
}

release_cleanup_lease() {
  exec 197>&-
}

remove_tombstone_under_lease() {
  local tombstone="$1"
  if ! rm -rf -- "$tombstone" "$gate_directory"; then
    return 1
  fi
  if ! rm -f -- "$gate_directory.rejected" "$cleanup_request"; then
    return 1
  fi
  return 0
}

cleanup_deploy_checkout() {
  local deploy_checkout deploy_root worktree_listing
  [[ -f "$deploy_checkout_file" ]] || return 0
  if [[ "$(stat -c '%a' "$deploy_checkout_file" 2>/dev/null || true)" != 600 ]] ||
    ! IFS= read -r deploy_checkout <"$deploy_checkout_file"; then
    return 1
  fi
  case "$deploy_checkout" in
    /tmp/orgmemory-deploy.*/repo) ;;
    *) return 1 ;;
  esac
  deploy_root="${deploy_checkout%/repo}"
  if ! worktree_listing="$(git -C /apps/orgmemory worktree list --porcelain)"; then
    return 1
  fi
  if grep -Fxq "worktree $deploy_checkout" <<<"$worktree_listing"; then
    if ! git -C /apps/orgmemory worktree remove --force "$deploy_checkout"; then
      return 1
    fi
  fi
  if ! rm -rf -- "$deploy_root"; then
    return 1
  fi
  return 0
}

cleanup_under_lease() {
  local owner tombstone
  acquire_cleanup_lease || return 1
  if [[ -f "$status_file" ]]; then
    release_cleanup_lease
    return 2
  fi
  if [[ -f "$controller_started_file" ]]; then
    if ! publish_dead_active_status; then
      release_cleanup_lease
      return 1
    fi
    release_cleanup_lease
    return 2
  fi

  owner="$(readlink "$ownership_file" 2>/dev/null || true)"
  if [[ "$owner" == controller.* ]]; then
    # Acquiring the inherited lease proves the exact controller process tree no
    # longer owns this state; a live qualified PID is therefore preserved rather
    # than treated as sufficient proof that cleanup is safe.
    if controller_is_alive "$owner"; then
      release_cleanup_lease
      return 1
    fi
    if [[ "$(readlink "$ownership_file" 2>/dev/null || true)" != "$owner" ]]; then
      release_cleanup_lease
      return 1
    fi
    if ! rm -f -- "$ownership_file"; then
      release_cleanup_lease
      return 1
    fi
    owner=""
  fi
  if [[ "$owner" != cleanup ]]; then
    if ! ln -s cleanup "$ownership_file" 2>/dev/null; then
      if [[ "$(readlink "$ownership_file" 2>/dev/null || true)" != cleanup ]]; then
        release_cleanup_lease
        return 1
      fi
    fi
  fi

  if ! cleanup_deploy_checkout; then
    release_cleanup_lease
    return 1
  fi
  tombstone="${state_directory}.cleanup.${BASHPID}.${RANDOM}"
  if ! mv -T -- "$state_directory" "$tombstone" 2>/dev/null; then
    release_cleanup_lease
    return 1
  fi
  # The lease and ownership object remain inside the atomically detached
  # tombstone until recursive deletion completes.
  if ! remove_tombstone_under_lease "$tombstone"; then
    release_cleanup_lease
    return 1
  fi
  release_cleanup_lease
  return 0
}

cleanup_terminal_under_lease() {
  local tombstone
  acquire_cleanup_lease || return 1
  if [[ ! -f "$status_file" ]]; then
    release_cleanup_lease
    return 1
  fi
  if [[ -e "$state_directory/docker-config" ]]; then
    write_intervention_latch 'terminal-status-retained-registry-credentials' || true
    release_cleanup_lease
    return 1
  fi
  if ! cleanup_deploy_checkout; then
    release_cleanup_lease
    return 1
  fi
  tombstone="${state_directory}.cleanup.${BASHPID}.${RANDOM}"
  if ! mv -T -- "$state_directory" "$tombstone" 2>/dev/null; then
    release_cleanup_lease
    return 1
  fi
  if ! remove_tombstone_under_lease "$tombstone"; then
    release_cleanup_lease
    return 1
  fi
  release_cleanup_lease
  return 0
}

signal_qualified_controller() {
  local owner="$1"
  local signal_name="$2"
  local pid expected_starttime
  [[ "$owner" =~ ^controller\.([1-9][0-9]*)\.([1-9][0-9]*)$ ]] || return 1
  pid="${BASH_REMATCH[1]}"
  expected_starttime="${BASH_REMATCH[2]}"
  [[ -x "$signal_helper" ]] || return 1
  "$signal_helper" "$pid" "$expected_starttime" "$signal_name"
}

if [[ -n "$reconciler_ready_marker" ]]; then
  reconciler_pid="$BASHPID"
  reconciler_starttime="$(cut -d ' ' -f 22 "/proc/$reconciler_pid/stat")"
  reconciler_ready_identity="$reconciler_pid $reconciler_starttime"
  ready_temp="$reconciler_ready_marker.$reconciler_pid"
  if [[ -e "$reconciler_ready_marker" ]] ||
    ! printf '%s\n' "$reconciler_ready_identity" >"$ready_temp" ||
    ! chmod 0600 "$ready_temp" ||
    ! mv -T -- "$ready_temp" "$reconciler_ready_marker"; then
    rm -f -- "$ready_temp" || true
    exit 74
  fi
  cleanup_ready_marker() {
    if [[ -f "$reconciler_ready_marker" &&
      "$(<"$reconciler_ready_marker")" == "$reconciler_ready_identity" ]]; then
      rm -f -- "$reconciler_ready_marker" || true
    fi
  }
  trap cleanup_ready_marker EXIT
fi

for ((elapsed = 0; ; elapsed += 1)); do
  if cleanup_tombstones_if_detached; then
    exit 0
  fi

  if [[ -f "$status_file" ]]; then
    if [[ -e "$cleanup_request" || "$terminal_elapsed" -ge "$launch_timeout_seconds" ]]; then
      if cleanup_terminal_under_lease; then
        exit 0
      fi
    fi
    terminal_elapsed=$((terminal_elapsed + 1))
    if ((terminal_elapsed >= controller_timeout_seconds)); then
      write_intervention_latch 'terminal-cleanup-exceeded-hard-deadline' || true
    fi
    sleep 1
    continue
  fi

  owner="$(readlink "$ownership_file" 2>/dev/null || true)"
  if [[ -f "$controller_started_file" ]]; then
    if [[ -e "$cleanup_request" ]]; then
      publish_gate_rejection || true
    fi
    if cleanup_under_lease; then
      exit 0
    else
      cleanup_result=$?
      [[ "$cleanup_result" -eq 2 && ! -e "$cleanup_request" ]] && exit 0
    fi
    controller_elapsed=$((controller_elapsed + 1))
    if ((controller_elapsed >= controller_timeout_seconds)); then
      write_intervention_latch 'controller-exceeded-hard-deadline' || true
    fi
    sleep 1
    continue
  fi

  if [[ "$owner" == controller.* ]] && ! controller_is_alive "$owner"; then
    cleanup_under_lease || true
    cleanup_tombstones_if_detached && exit 0
  fi

  if [[ -e "$cleanup_request" || "$owner" == cleanup || elapsed -ge launch_timeout_seconds ]]; then
    if [[ "$owner" == controller.* ]] && controller_is_alive "$owner" && \
      ((elapsed >= launch_timeout_seconds)); then
      if ! signal_qualified_controller "$owner" TERM; then
        write_intervention_latch 'qualified-controller-term-failed' || true
      fi
      for _ in {1..100}; do
        controller_is_alive "$owner" || break
        sleep 0.1
      done
      if controller_is_alive "$owner"; then
        if ! signal_qualified_controller "$owner" KILL; then
          write_intervention_latch 'qualified-controller-kill-failed' || true
        fi
      fi
    fi
    if ! cleanup_under_lease && ((elapsed >= launch_timeout_seconds)); then
      write_intervention_latch 'pre-active-lease-exceeded-launch-timeout' || true
    fi
    cleanup_tombstones_if_detached && exit 0
  fi
  sleep 1
done
