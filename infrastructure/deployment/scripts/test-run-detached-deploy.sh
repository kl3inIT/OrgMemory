#!/usr/bin/env bash
set -Eeuo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
runner="$repo_root/infrastructure/deployment/scripts/run-detached-deploy.sh"
launch_verifier="$repo_root/infrastructure/deployment/scripts/verify-detached-controller.sh"
reconciler="$repo_root/infrastructure/deployment/scripts/reconcile-detached-launch.sh"
signal_helper="$repo_root/infrastructure/deployment/scripts/signal-qualified-process.py"
ln_signal_fixture="$repo_root/infrastructure/deployment/scripts/test-fixtures/ln-signal-parent.sh"
rm_pause_fixture="$repo_root/infrastructure/deployment/scripts/test-fixtures/rm-pause-tombstone.sh"
rm_fail_fixture="$repo_root/infrastructure/deployment/scripts/test-fixtures/rm-fail-selected.sh"
touch_signal_fixture="$repo_root/infrastructure/deployment/scripts/test-fixtures/touch-signal-parent.sh"
real_ln="$(command -v ln)"
real_rm="$(command -v rm)"
real_touch="$(command -v touch)"
real_git="$(command -v git)"
intervention_latch="/tmp/orgmemory-deploy-intervention-required.test.$$"
failure_state="$(mktemp -d /tmp/orgmemory-deploy-state.test-failure.XXXXXX)"
inherited_lease_state="$(mktemp -d /tmp/orgmemory-deploy-state.test-inherited-lease.XXXXXX)"
timeout_state="$(mktemp -d /tmp/orgmemory-deploy-state.test-timeout.XXXXXX)"
hard_timeout_state="$(mktemp -d /tmp/orgmemory-deploy-state.test-hard-timeout.XXXXXX)"
invalid_environment_state="$(mktemp -d /tmp/orgmemory-deploy-state.test-invalid-environment.XXXXXX)"
cleanup_owned_state="$(mktemp -d /tmp/orgmemory-deploy-state.test-cleanup-owned.XXXXXX)"
reconciler_controller_state="$(mktemp -d /tmp/orgmemory-deploy-state.test-reconciler-controller.XXXXXX)"
reconciler_cleanup_state="$(mktemp -d /tmp/orgmemory-deploy-state.test-reconciler-cleanup.XXXXXX)"
reconciler_orphan_state="$(mktemp -d /tmp/orgmemory-deploy-state.test-reconciler-orphan.XXXXXX)"
term_claim_state="$(mktemp -d /tmp/orgmemory-deploy-state.test-term-claim.XXXXXX)"
kill_claim_state="$(mktemp -d /tmp/orgmemory-deploy-state.test-kill-claim.XXXXXX)"
signal_wrapper_directory="$(mktemp -d /tmp/orgmemory-ln-wrapper.XXXXXX)"
tombstone_race_state="$(mktemp -d /tmp/orgmemory-deploy-state.test-tombstone-race.XXXXXX)"
tombstone_wrapper_directory="$(mktemp -d /tmp/orgmemory-rm-wrapper.XXXXXX)"
tombstone_ready="/tmp/orgmemory-tombstone-ready.$$"
tombstone_release="/tmp/orgmemory-tombstone-release.$$"
post_marker_kill_state="$(mktemp -d /tmp/orgmemory-deploy-state.test-post-marker-kill.XXXXXX)"
touch_wrapper_directory="$(mktemp -d /tmp/orgmemory-touch-wrapper.XXXXXX)"
active_child_kill_state="$(mktemp -d /tmp/orgmemory-deploy-state.test-active-child-kill.XXXXXX)"
transition_state="$(mktemp -d /tmp/orgmemory-deploy-state.test-terminal-transition.XXXXXX)"
transition_flock_directory="$(mktemp -d /tmp/orgmemory-flock-wrapper.XXXXXX)"
rm_failure_active_state="$(mktemp -d /tmp/orgmemory-deploy-state.test-rm-failure-active.XXXXXX)"
finalizer_rm_failure_state="$(mktemp -d /tmp/orgmemory-deploy-state.test-finalizer-rm-failure.XXXXXX)"
rm_failure_tombstone_state="$(mktemp -d /tmp/orgmemory-deploy-state.test-rm-failure-tombstone.XXXXXX)"
cleanup_request_state="$(mktemp -d /tmp/orgmemory-deploy-state.test-cleanup-request.XXXXXX)"
linked_cleanup_state="$(mktemp -d /tmp/orgmemory-deploy-state.test-linked-cleanup.XXXXXX)"
terminal_grace_state="$(mktemp -d /tmp/orgmemory-deploy-state.test-terminal-grace.XXXXXX)"
ownerless_lease_state="$(mktemp -d /tmp/orgmemory-deploy-state.test-ownerless-lease.XXXXXX)"
reconciler_ready_state="$(mktemp -d /tmp/orgmemory-deploy-state.test-reconciler-ready.XXXXXX)"
linked_deploy_root="$(mktemp -d /tmp/orgmemory-deploy.test-linked-cleanup.XXXXXX)"
rm_fail_wrapper_directory="$(mktemp -d /tmp/orgmemory-rm-fail-wrapper.XXXXXX)"
git_fail_wrapper_directory="$(mktemp -d /tmp/orgmemory-git-fail-wrapper.XXXXXX)"
git_failure_marker="/tmp/orgmemory-git-worktree-remove-failed.test.$$"
reconciler_gate_prefix="/tmp/orgmemory-deploy-gate.detached-reconciler-$$"

cleanup() {
  git -C /apps/orgmemory worktree remove --force "$linked_deploy_root/repo" \
    >/dev/null 2>&1 || true
  local background_pid
  for background_pid in \
    "${tombstone_reconciler_pid:-}" \
    "${active_child_reconciler_pid:-}" \
    "${active_controller_pid:-}" \
    "${active_timeout_pid:-}" \
    "${active_deploy_pid:-}" \
    "${request_controller_pid:-}" \
    "${request_reconciler_pid:-}" \
    "${pidfd_target_pid:-}"; do
    [[ -n "$background_pid" ]] || continue
    kill "$background_pid" 2>/dev/null || true
  done
  rm -rf -- \
    "$failure_state" \
    "$inherited_lease_state" \
    "$timeout_state" \
    "$hard_timeout_state" \
    "$invalid_environment_state" \
    "$cleanup_owned_state" \
    "$reconciler_controller_state" \
    "$reconciler_cleanup_state" \
    "$reconciler_orphan_state" \
    "$term_claim_state" \
    "$kill_claim_state" \
    "$signal_wrapper_directory" \
    "$tombstone_race_state" \
    "$tombstone_wrapper_directory" \
    "$post_marker_kill_state" \
    "$touch_wrapper_directory" \
    "$active_child_kill_state" \
    "$transition_state" \
    "$transition_flock_directory" \
    "$rm_failure_active_state" \
    "$finalizer_rm_failure_state" \
    "$rm_failure_tombstone_state" \
    "$cleanup_request_state" \
    "$linked_cleanup_state" \
    "$terminal_grace_state" \
    "$ownerless_lease_state" \
    "$reconciler_ready_state" \
    "$linked_deploy_root" \
    "$rm_fail_wrapper_directory" \
    "$git_fail_wrapper_directory" \
    "$reconciler_gate_prefix-controller" \
    "$reconciler_gate_prefix-cleanup" \
    "$reconciler_gate_prefix-orphan"
  rm -rf -- "${tombstone_race_state}.cleanup."*
  rm -rf -- "$reconciler_gate_prefix-kill"
  rm -rf -- "$reconciler_gate_prefix-tombstone"
  rm -rf -- "$reconciler_gate_prefix-rm-active"
  rm -rf -- "$reconciler_gate_prefix-finalizer-rm"
  rm -rf -- "$reconciler_gate_prefix-rm-tombstone"
  rm -rf -- "$reconciler_gate_prefix-request"
  rm -rf -- "$reconciler_gate_prefix-linked"
  rm -rf -- "$reconciler_gate_prefix-terminal-grace"
  rm -rf -- "$reconciler_gate_prefix-ownerless"
  rm -rf -- "$reconciler_gate_prefix-ready"
  rm -rf -- "${rm_failure_tombstone_state}.cleanup."*
  rm -f -- "$cleanup_request_state.cleanup-requested"
  rm -f -- "$tombstone_ready" "$tombstone_release"
  rm -f -- "$intervention_latch"
  rm -f -- "$git_failure_marker"
}
trap cleanup EXIT

write_environment() {
  local state="$1"
  local deploy_script="$2"
  local timeout_seconds="$3"
  local kill_after_seconds="${4:-2}"
  {
    printf 'COMMIT_SHA=%s\n' 1111111111111111111111111111111111111111
    printf 'DEPLOY_INTERVENTION_LATCH=%q\n' "$intervention_latch"
    printf 'DEPLOY_KILL_AFTER_SECONDS=%q\n' "$kill_after_seconds"
    printf 'DEPLOY_TIMEOUT_SECONDS=%q\n' "$timeout_seconds"
    printf 'DOCKER_CONFIG=%q\n' "$state/docker-config"
    printf 'ORGMEMORY_EXTERNAL_GATE_DIRECTORY=%q\n' "/tmp/orgmemory-deploy-gate.detached-test-$$"
    printf 'ORGMEMORY_ENV_FILE=%q\n' "$state/production.env"
    printf 'ORGMEMORY_KEYCLOAK_CONFIGURATION_SCRIPT=%q\n' /bin/true
    printf 'ORGMEMORY_KEYCLOAK_LOGIN_THEME=%q\n' keycloak
    printf 'ORGMEMORY_KEYCLOAK_THEME_CONFIGURATION_SCRIPT=%q\n' /bin/true
    printf 'ORGMEMORY_RELEASE_API_IMAGE=%q\n' image-api
    printf 'ORGMEMORY_RELEASE_KEYCLOAK_IMAGE=%q\n' image-keycloak
    printf 'ORGMEMORY_RELEASE_MCP_IMAGE=%q\n' image-mcp
    printf 'ORGMEMORY_RELEASE_POSTGRES_IMAGE=%q\n' image-postgres
    printf 'ORGMEMORY_RELEASE_WEB_IMAGE=%q\n' image-web
    printf 'ORGMEMORY_RELEASE_WORKER_IMAGE=%q\n' image-worker
    printf 'ORGMEMORY_REPO_ROOT=%q\n' "$repo_root"
    printf 'ORGMEMORY_SMOKE_SCRIPT=%q\n' /bin/true
    printf 'ORGMEMORY_TEAM_DEV_COORDINATION_SCRIPT=%q\n' /bin/true
    printf 'TRUSTED_DEPLOY_SCRIPT=%q\n' "$deploy_script"
  } >"$state/launch.env"
  chmod 0600 "$state/launch.env"
  : >"$state/production.env"
  chmod 0600 "$state/production.env"
  cp "$repo_root/infrastructure/deployment/scripts/signal-qualified-process.py" \
    "$state/signal-qualified-process.py"
  chmod 0700 "$state/signal-qualified-process.py"
  mkdir --mode=0700 "$state/docker-config"
}

failure_deploy="$failure_state/deploy"
# shellcheck disable=SC2016  # Generated fixture expands the environment variable at runtime.
printf '%s\n' \
  '#!/usr/bin/env bash' \
  'printf "detached failure exercised\\n"' \
  'printf "environment=%s\\n" "${ORGMEMORY_ENV_FILE:-unset}"' \
  'exit 23' >"$failure_deploy"
chmod 0700 "$failure_deploy"
write_environment "$failure_state" "$failure_deploy" 5
"$runner" "$failure_state"
read -r failure_status <"$failure_state/status"
[[ "$failure_status" == 23 ]]
[[ "$(stat -c '%a' "$failure_state")" == 700 ]]
[[ "$(stat -c '%a' "$failure_state/controller-started")" == 600 ]]
[[ "$(stat -c '%a' "$failure_state/status")" == 600 ]]
[[ "$(readlink "$failure_state/ownership")" == controller.* ]]
[[ ! -e "$failure_state/docker-config" ]]
grep -Fx 'detached failure exercised' "$failure_state/deploy.log" >/dev/null
grep -Fx "environment=$failure_state/production.env" "$failure_state/deploy.log" >/dev/null
"$launch_verifier" "$failure_state"
printf '256\n' >"$failure_state/status"
if "$launch_verifier" "$failure_state" >/dev/null 2>&1; then
  printf 'Terminal launch verification accepted an out-of-range status.\n' >&2
  exit 1
fi
printf '23\n' >"$failure_state/status"
mkdir --mode=0700 "$failure_state/docker-config"
if "$launch_verifier" "$failure_state" >/dev/null 2>&1; then
  printf 'Terminal launch verification accepted retained registry credentials.\n' >&2
  exit 1
fi
rm -rf -- "$failure_state/docker-config"
"$launch_verifier" "$failure_state"
printf '23\nextra\n' >"$failure_state/status"
if "$launch_verifier" "$failure_state" >/dev/null 2>&1; then
  printf 'Terminal launch verification accepted multiline status data.\n' >&2
  exit 1
fi
printf '23\n\n' >"$failure_state/status"
if "$launch_verifier" "$failure_state" >/dev/null 2>&1; then
  printf 'Terminal launch verification accepted trailing blank status lines.\n' >&2
  exit 1
fi
printf '23\n' >"$failure_state/status"
chmod 0755 "$failure_state"
if "$launch_verifier" "$failure_state" >/dev/null 2>&1; then
  printf 'Terminal launch verification accepted an insecure state directory.\n' >&2
  exit 1
fi
chmod 0700 "$failure_state"
mv "$failure_state/status" "$failure_state/status-target"
ln -s status-target "$failure_state/status"
if "$launch_verifier" "$failure_state" >/dev/null 2>&1; then
  printf 'Terminal launch verification accepted a symlinked status.\n' >&2
  exit 1
fi
rm -f -- "$failure_state/status"
mv "$failure_state/status-target" "$failure_state/status"
ln -s missing-credential-directory "$failure_state/docker-config"
if "$launch_verifier" "$failure_state" >/dev/null 2>&1; then
  printf 'Terminal launch verification accepted a dangling credential symlink.\n' >&2
  exit 1
fi
rm -f -- "$failure_state/docker-config"
"$launch_verifier" "$failure_state"

transition_pid="$BASHPID"
transition_starttime="$(cut -d ' ' -f 22 "/proc/$transition_pid/stat")"
touch "$transition_state/controller-started" "$transition_state/ownership.lease"
chmod 0600 "$transition_state/controller-started" "$transition_state/ownership.lease"
ln -s "controller.$transition_pid.$transition_starttime" "$transition_state/ownership"
mkdir --mode=0700 "$transition_state/docker-config"
# shellcheck disable=SC2016  # Generated fixture expands this variable at runtime.
printf '%s\n' \
  '#!/usr/bin/env bash' \
  'set -Eeuo pipefail' \
  'rm -rf -- "$ORGMEMORY_TRANSITION_STATE/docker-config"' \
  'printf "%s\\n" "$ORGMEMORY_TRANSITION_STATUS" >"$ORGMEMORY_TRANSITION_STATE/status.tmp"' \
  'chmod 0600 "$ORGMEMORY_TRANSITION_STATE/status.tmp"' \
  'mv -f -- "$ORGMEMORY_TRANSITION_STATE/status.tmp" "$ORGMEMORY_TRANSITION_STATE/status"' \
  'if [[ "$ORGMEMORY_TRANSITION_CREDENTIAL_SYMLINK" == true ]]; then' \
  '  ln -s missing-credential-directory "$ORGMEMORY_TRANSITION_STATE/docker-config"' \
  'fi' \
  'exit "$ORGMEMORY_TRANSITION_FLOCK_RC"' >"$transition_flock_directory/flock"
chmod 0700 "$transition_flock_directory/flock"
PATH="$transition_flock_directory:$PATH" \
  ORGMEMORY_TRANSITION_STATE="$transition_state" \
  ORGMEMORY_TRANSITION_STATUS=23 \
  ORGMEMORY_TRANSITION_CREDENTIAL_SYMLINK=false \
  ORGMEMORY_TRANSITION_FLOCK_RC=0 \
  "$launch_verifier" "$transition_state"
[[ "$(<"$transition_state/status")" == 23 ]]
[[ ! -e "$transition_state/docker-config" ]]

rm -f -- "$transition_state/status"
mkdir --mode=0700 "$transition_state/docker-config"
if PATH="$transition_flock_directory:$PATH" \
  ORGMEMORY_TRANSITION_STATE="$transition_state" \
  ORGMEMORY_TRANSITION_STATUS=256 \
  ORGMEMORY_TRANSITION_CREDENTIAL_SYMLINK=true \
  ORGMEMORY_TRANSITION_FLOCK_RC=1 \
  "$launch_verifier" "$transition_state" >/dev/null 2>&1; then
  printf 'Active lease verification accepted an invalid concurrent terminal state.\n' >&2
  exit 1
fi

rm -f -- "$transition_state/status" "$transition_state/docker-config"
mkdir --mode=0700 "$transition_state/docker-config"
PATH="$transition_flock_directory:$PATH" \
  ORGMEMORY_TRANSITION_STATE="$transition_state" \
  ORGMEMORY_TRANSITION_STATUS=23 \
  ORGMEMORY_TRANSITION_CREDENTIAL_SYMLINK=false \
  ORGMEMORY_TRANSITION_FLOCK_RC=1 \
  "$launch_verifier" "$transition_state"
[[ "$(<"$transition_state/status")" == 23 ]]
[[ ! -e "$transition_state/docker-config" ]]

inherited_deploy="$inherited_lease_state/deploy"
printf '%s\n' '#!/usr/bin/env bash' 'exit 0' >"$inherited_deploy"
chmod 0700 "$inherited_deploy"
write_environment "$inherited_lease_state" "$inherited_deploy" 5
(
  exec 198>"$inherited_lease_state/ownership.lease"
  flock -n 198
  "$runner" "$inherited_lease_state"
)
[[ "$(<"$inherited_lease_state/status")" == 0 ]]
[[ ! -e "$inherited_lease_state/docker-config" ]]

timeout_deploy="$timeout_state/deploy"
printf '%s\n' \
  '#!/usr/bin/env bash' \
  "trap 'printf terminated >\"$timeout_state/terminated\"; exit 143' TERM" \
  'while true; do sleep 0.1; done' >"$timeout_deploy"
chmod 0700 "$timeout_deploy"
write_environment "$timeout_state" "$timeout_deploy" 1
"$runner" "$timeout_state"
read -r timeout_status <"$timeout_state/status"
[[ "$timeout_status" == 124 ]]
[[ "$(readlink "$timeout_state/ownership")" == controller.* ]]
[[ -f "$timeout_state/terminated" ]]
[[ ! -e "$timeout_state/docker-config" ]]

hard_timeout_deploy="$hard_timeout_state/deploy"
printf '%s\n' \
  '#!/usr/bin/env bash' \
  "trap 'printf term-ignored >\"$hard_timeout_state/term-ignored\"' TERM" \
  'while true; do sleep 0.1; done' >"$hard_timeout_deploy"
chmod 0700 "$hard_timeout_deploy"
write_environment "$hard_timeout_state" "$hard_timeout_deploy" 1 1
"$runner" "$hard_timeout_state"
read -r hard_timeout_status <"$hard_timeout_state/status"
[[ "$hard_timeout_status" == 137 ]]
[[ "$(readlink "$hard_timeout_state/ownership")" == controller.* ]]
[[ -f "$hard_timeout_state/term-ignored" ]]
[[ -f "$hard_timeout_state/controller-started" ]]
[[ ! -e "$hard_timeout_state/docker-config" ]]
grep -F 'status=137' "$intervention_latch" >/dev/null

mkdir --mode=0700 "$invalid_environment_state/docker-config"
printf 'credential material\n' >"$invalid_environment_state/docker-config/config.json"
set +e
"$runner" "$invalid_environment_state" >/dev/null 2>&1
invalid_environment_exit=$?
set -e
[[ "$invalid_environment_exit" == 65 ]]
read -r invalid_environment_status <"$invalid_environment_state/status"
[[ "$invalid_environment_status" == 65 ]]
[[ "$(readlink "$invalid_environment_state/ownership")" == controller.* ]]
[[ ! -e "$invalid_environment_state/docker-config" ]]

write_environment "$cleanup_owned_state" "$failure_deploy" 20
printf 'credential material\n' >"$cleanup_owned_state/docker-config/config.json"
ln -s cleanup "$cleanup_owned_state/ownership"
set +e
"$runner" "$cleanup_owned_state" >/dev/null 2>&1
cleanup_owned_status=$?
set -e
[[ "$cleanup_owned_status" -eq 75 ]]
[[ "$(readlink "$cleanup_owned_state/ownership")" == cleanup ]]
[[ ! -e "$cleanup_owned_state/controller-started" ]]
[[ ! -e "$cleanup_owned_state/status" ]]
[[ -e "$cleanup_owned_state/docker-config/config.json" ]]

ln -s "$ln_signal_fixture" "$signal_wrapper_directory/ln"
write_environment "$term_claim_state" "$failure_deploy" 20
printf 'credential material\n' >"$term_claim_state/docker-config/config.json"
set +e
PATH="$signal_wrapper_directory:$PATH" \
  ORGMEMORY_REAL_LN="$real_ln" \
  ORGMEMORY_OWNERSHIP_SIGNAL=TERM \
  "$runner" "$term_claim_state" >/dev/null 2>&1
term_claim_exit=$?
set -e
[[ "$term_claim_exit" -eq 143 ]]
[[ "$(readlink "$term_claim_state/ownership")" == controller.* ]]
[[ ! -e "$term_claim_state/controller-started" ]]
[[ ! -e "$term_claim_state/docker-config" ]]
[[ "$(<"$term_claim_state/status")" == 143 ]]

write_environment "$kill_claim_state" "$failure_deploy" 20
printf 'credential material\n' >"$kill_claim_state/docker-config/config.json"
set +e
PATH="$signal_wrapper_directory:$PATH" \
  ORGMEMORY_REAL_LN="$real_ln" \
  ORGMEMORY_OWNERSHIP_SIGNAL=KILL \
  "$runner" "$kill_claim_state" >/dev/null 2>&1
kill_claim_exit=$?
set -e
[[ "$kill_claim_exit" -eq 137 ]]
[[ "$(readlink "$kill_claim_state/ownership")" == controller.* ]]
[[ ! -e "$kill_claim_state/controller-started" ]]
[[ -e "$kill_claim_state/docker-config/config.json" ]]
mkdir --mode=0700 "$reconciler_gate_prefix-kill"
"$reconciler" "$kill_claim_state" "$reconciler_gate_prefix-kill" 2 "$intervention_latch" 5
[[ ! -e "$kill_claim_state" ]]
[[ ! -e "$reconciler_gate_prefix-kill" ]]

write_environment "$post_marker_kill_state" "$failure_deploy" 20 1
mkdir -p "$post_marker_kill_state/docker-config"
printf '{"auths":{"ghcr.io":{}}}\n' >"$post_marker_kill_state/docker-config/config.json"
ln -s "$touch_signal_fixture" "$touch_wrapper_directory/touch"
set +e
PATH="$touch_wrapper_directory:$PATH" \
  ORGMEMORY_REAL_TOUCH="$real_touch" \
  ORGMEMORY_MARKER_SIGNAL=KILL \
  "$runner" "$post_marker_kill_state" >/dev/null 2>&1
post_marker_kill_exit=$?
set -e
[[ "$post_marker_kill_exit" -eq 137 ]]
[[ -f "$post_marker_kill_state/controller-started" ]]
[[ ! -f "$post_marker_kill_state/status" ]]
[[ -e "$post_marker_kill_state/docker-config/config.json" ]]
rm -f -- "$intervention_latch"
"$reconciler" "$post_marker_kill_state" "$reconciler_gate_prefix-kill" 2 "$intervention_latch" 5
[[ -f "$post_marker_kill_state/status" ]]
[[ "$(<"$post_marker_kill_state/status")" == 137 ]]
[[ ! -e "$post_marker_kill_state/docker-config" ]]
[[ -f "$intervention_latch" ]]

active_child_deploy="$active_child_kill_state/deploy"
printf '%s\n' \
  '#!/usr/bin/env bash' \
  "exec 9>'$active_child_kill_state/deploy.lock'" \
  "printf '%s %s\\n' \"\$\$\" \"\$PPID\" >'$active_child_kill_state/child-processes'" \
  'while true; do sleep 0.1; done' >"$active_child_deploy"
chmod 0700 "$active_child_deploy"
write_environment "$active_child_kill_state" "$active_child_deploy" 20 1
printf '{"auths":{"ghcr.io":{}}}\n' >"$active_child_kill_state/docker-config/config.json"
"$runner" "$active_child_kill_state" >/dev/null 2>&1 &
active_controller_pid=$!
for _ in {1..100}; do
  [[ -f "$active_child_kill_state/child-processes" ]] && break
  sleep 0.05
done
[[ -f "$active_child_kill_state/child-processes" ]]
read -r active_deploy_pid active_timeout_pid <"$active_child_kill_state/child-processes"
ln -s missing-status-target "$active_child_kill_state/status"
if "$launch_verifier" "$active_child_kill_state" >/dev/null 2>&1; then
  printf 'Active launch verification accepted a dangling status symlink.\n' >&2
  exit 1
fi
rm -f -- "$active_child_kill_state/status"
"$launch_verifier" "$active_child_kill_state"
kill -KILL "$active_controller_pid"
wait "$active_controller_pid" 2>/dev/null || true
kill -KILL "$active_timeout_pid"
rm -f -- "$intervention_latch"
"$reconciler" "$active_child_kill_state" "$reconciler_gate_prefix-kill" 1 "$intervention_latch" 5 &
active_child_reconciler_pid=$!
sleep 0.2
kill -0 "$active_child_reconciler_pid"
[[ ! -f "$active_child_kill_state/status" ]]
[[ -e "$active_child_kill_state/docker-config/config.json" ]]
kill -KILL "$active_deploy_pid"
wait "$active_child_reconciler_pid"
[[ "$(<"$active_child_kill_state/status")" == 137 ]]
[[ ! -e "$active_child_kill_state/docker-config" ]]
[[ -f "$intervention_latch" ]]

ln -s "$rm_fail_fixture" "$rm_fail_wrapper_directory/rm"
finalizer_success_deploy="$finalizer_rm_failure_state/deploy"
printf '%s\n' '#!/usr/bin/env bash' 'exit 0' >"$finalizer_success_deploy"
chmod 0700 "$finalizer_success_deploy"
write_environment "$finalizer_rm_failure_state" "$finalizer_success_deploy" 20 1
printf 'credential material\n' >"$finalizer_rm_failure_state/docker-config/config.json"
rm -f -- "$intervention_latch"
set +e
PATH="$rm_fail_wrapper_directory:$PATH" \
  ORGMEMORY_REAL_RM="$real_rm" \
  ORGMEMORY_RM_FAIL_PATTERN=docker-config \
  "$runner" "$finalizer_rm_failure_state"
finalizer_rm_failure_exit=$?
set -e
[[ "$finalizer_rm_failure_exit" -ne 0 ]]
[[ ! -f "$finalizer_rm_failure_state/status" ]]
[[ -e "$finalizer_rm_failure_state/docker-config/config.json" ]]
[[ -f "$intervention_latch" ]]
mkdir --mode=0700 "$reconciler_gate_prefix-finalizer-rm"
"$reconciler" "$finalizer_rm_failure_state" \
  "$reconciler_gate_prefix-finalizer-rm" 1 "$intervention_latch" 5
[[ "$(<"$finalizer_rm_failure_state/status")" == 137 ]]
[[ ! -e "$finalizer_rm_failure_state/docker-config" ]]

write_environment "$rm_failure_active_state" "$failure_deploy" 20 1
printf 'credential material\n' >"$rm_failure_active_state/docker-config/config.json"
ln -s controller.99999999.1 "$rm_failure_active_state/ownership"
touch "$rm_failure_active_state/controller-started"
mkdir --mode=0700 "$reconciler_gate_prefix-rm-active"
rm -f -- "$intervention_latch"
set +e
PATH="$rm_fail_wrapper_directory:$PATH" \
  ORGMEMORY_REAL_RM="$real_rm" \
  ORGMEMORY_RM_FAIL_PATTERN=docker-config \
  timeout 1 "$reconciler" "$rm_failure_active_state" \
    "$reconciler_gate_prefix-rm-active" 1 "$intervention_latch" 5
rm_failure_active_exit=$?
set -e
[[ "$rm_failure_active_exit" -eq 124 ]]
[[ ! -f "$rm_failure_active_state/status" ]]
[[ -e "$rm_failure_active_state/docker-config/config.json" ]]
[[ -f "$intervention_latch" ]]
"$reconciler" "$rm_failure_active_state" \
  "$reconciler_gate_prefix-rm-active" 1 "$intervention_latch" 5
[[ "$(<"$rm_failure_active_state/status")" == 137 ]]
[[ ! -e "$rm_failure_active_state/docker-config" ]]

write_environment "$rm_failure_tombstone_state" "$failure_deploy" 20 1
printf 'credential material\n' >"$rm_failure_tombstone_state/docker-config/config.json"
ln -s cleanup "$rm_failure_tombstone_state/ownership"
mkdir --mode=0700 "$reconciler_gate_prefix-rm-tombstone"
set +e
PATH="$rm_fail_wrapper_directory:$PATH" \
  ORGMEMORY_REAL_RM="$real_rm" \
  ORGMEMORY_RM_FAIL_PATTERN=.cleanup. \
  timeout 1 "$reconciler" "$rm_failure_tombstone_state" \
    "$reconciler_gate_prefix-rm-tombstone" 1 "$intervention_latch" 5
rm_failure_tombstone_exit=$?
set -e
[[ "$rm_failure_tombstone_exit" -eq 124 ]]
[[ ! -e "$rm_failure_tombstone_state" ]]
[[ -n "$(compgen -G "${rm_failure_tombstone_state}.cleanup.*")" ]]
"$reconciler" "$rm_failure_tombstone_state" \
  "$reconciler_gate_prefix-rm-tombstone" 1 "$intervention_latch" 5
[[ -z "$(compgen -G "${rm_failure_tombstone_state}.cleanup.*" || true)" ]]

request_deploy="$cleanup_request_state/deploy"
printf '%s\n' '#!/usr/bin/env bash' 'sleep 1' >"$request_deploy"
chmod 0700 "$request_deploy"
write_environment "$cleanup_request_state" "$request_deploy" 10 1
mkdir --mode=0700 "$reconciler_gate_prefix-request"
"$runner" "$cleanup_request_state" >/dev/null 2>&1 &
request_controller_pid=$!
for _ in {1..100}; do
  [[ -f "$cleanup_request_state/controller-started" ]] && break
  sleep 0.05
done
[[ -f "$cleanup_request_state/controller-started" ]]
touch "$cleanup_request_state.cleanup-requested"
"$reconciler" "$cleanup_request_state" \
  "$reconciler_gate_prefix-request" 1 "$intervention_latch" 5 &
request_reconciler_pid=$!
wait "$request_controller_pid"
wait "$request_reconciler_pid"
[[ ! -e "$cleanup_request_state" ]]
[[ ! -e "$cleanup_request_state.cleanup-requested" ]]
[[ ! -e "$reconciler_gate_prefix-request" ]]

write_environment "$terminal_grace_state" "$failure_deploy" 20 1
rm -rf -- "$terminal_grace_state/docker-config"
printf '0\n' >"$terminal_grace_state/status"
mkdir --mode=0700 "$reconciler_gate_prefix-terminal-grace"
"$reconciler" "$terminal_grace_state" \
  "$reconciler_gate_prefix-terminal-grace" 1 "$intervention_latch" 5
[[ ! -e "$terminal_grace_state" ]]
[[ ! -e "$reconciler_gate_prefix-terminal-grace" ]]

write_environment "$reconciler_ready_state" "$failure_deploy" 20 1
mkdir --mode=0700 "$reconciler_gate_prefix-ready"
ready_marker="$reconciler_ready_state/reconciler-ready.1"
missing_ready_marker="$reconciler_ready_state/reconciler-ready.2"
ORGMEMORY_RECONCILER_READY_MARKER="$ready_marker" \
  setsid -f "$reconciler" "$reconciler_ready_state" \
    "$reconciler_gate_prefix-ready" 20 "$intervention_latch" 5 \
    </dev/null >/dev/null 2>&1
ready_live=false
for _ in {1..100}; do
  if [[ -f "$ready_marker" ]]; then
    IFS=' ' read -r ready_pid ready_starttime ready_extra <"$ready_marker" || true
    if [[ -z "${ready_extra:-}" && "${ready_pid:-}" =~ ^[1-9][0-9]*$ &&
      "$(cut -d ' ' -f 22 "/proc/$ready_pid/stat" 2>/dev/null || true)" == "$ready_starttime" ]]; then
      ready_live=true
      break
    fi
  fi
  sleep 0.1
done
[[ "$ready_live" == true ]]
ORGMEMORY_RECONCILER_READY_MARKER="$missing_ready_marker" \
  setsid -f /definitely/missing/orgmemory-reconciler \
    </dev/null >/dev/null 2>&1
for _ in {1..10}; do
  [[ -e "$missing_ready_marker" ]] && exit 1
  sleep 0.1
done
touch "$reconciler_ready_state.cleanup-requested"
for _ in {1..100}; do
  [[ ! -e "$reconciler_ready_state" ]] && break
  sleep 0.1
done
[[ ! -e "$reconciler_ready_state" ]]
[[ ! -e "$reconciler_gate_prefix-ready" ]]

rm -f -- "$intervention_latch"
write_environment "$ownerless_lease_state" "$failure_deploy" 20 1
mkdir --mode=0700 "$reconciler_gate_prefix-ownerless"
(
  exec 198>"$ownerless_lease_state/ownership.lease"
  flock -n 198
  "$reconciler" "$ownerless_lease_state" \
    "$reconciler_gate_prefix-ownerless" 1 "$intervention_latch" 5 &
  ownerless_reconciler_pid=$!
  for _ in {1..30}; do
    [[ -f "$intervention_latch" ]] && break
    sleep 0.1
  done
  grep -F 'pre-active-lease-exceeded-launch-timeout' "$intervention_latch" >/dev/null
  exec 198>&-
  wait "$ownerless_reconciler_pid"
)
[[ ! -e "$ownerless_lease_state" ]]
[[ ! -e "$reconciler_gate_prefix-ownerless" ]]
rm -f -- "$intervention_latch"

ln -s "$repo_root/infrastructure/deployment/scripts/test-fixtures/git-fail-worktree-remove-once.sh" \
  "$git_fail_wrapper_directory/git"

if [[ "$(git -C /apps/orgmemory rev-parse --show-toplevel 2>/dev/null || true)" == "$repo_root" ]]; then
  git -C /apps/orgmemory worktree add --detach "$linked_deploy_root/repo" HEAD >/dev/null
  write_environment "$linked_cleanup_state" "$failure_deploy" 20 1
  printf '%s\n' "$linked_deploy_root/repo" >"$linked_cleanup_state/deploy-checkout"
  chmod 0600 "$linked_cleanup_state/deploy-checkout"
  ln -s cleanup "$linked_cleanup_state/ownership"
  mkdir --mode=0700 "$reconciler_gate_prefix-linked"
  (
    exec 198>"$linked_cleanup_state/ownership.lease"
    flock -n 198
    PATH="$git_fail_wrapper_directory:$PATH" \
      ORGMEMORY_REAL_GIT="$real_git" \
      ORGMEMORY_GIT_FAILURE_MARKER="$git_failure_marker" \
      "$reconciler" "$linked_cleanup_state" \
        "$reconciler_gate_prefix-linked" 1 "$intervention_latch" 5 &
    inherited_reconciler_pid=$!
    sleep 0.1
    [[ -e "$linked_cleanup_state" ]]
    exec 198>&-
    wait "$inherited_reconciler_pid"
  )
  [[ -f "$git_failure_marker" ]]
  [[ ! -e "$linked_cleanup_state" ]]
  [[ ! -e "$linked_deploy_root" ]]
  if git -C /apps/orgmemory worktree list --porcelain |
    grep -Fxq "worktree $linked_deploy_root/repo"; then
    exit 1
  fi
fi

ln -s controller.99999999.1 "$reconciler_controller_state/ownership"
touch "$reconciler_controller_state/controller-started"
mkdir --mode=0700 "$reconciler_gate_prefix-controller"
"$reconciler" "$reconciler_controller_state" "$reconciler_gate_prefix-controller" 1 "$intervention_latch" 5
[[ -d "$reconciler_controller_state" ]]
[[ "$(<"$reconciler_controller_state/status")" == 137 ]]
[[ -f "$intervention_latch" ]]

ln -s cleanup "$reconciler_cleanup_state/ownership"
mkdir --mode=0700 "$reconciler_gate_prefix-cleanup"
"$reconciler" "$reconciler_cleanup_state" "$reconciler_gate_prefix-cleanup" 1 "$intervention_latch" 5
[[ ! -e "$reconciler_cleanup_state" ]]
[[ ! -e "$reconciler_gate_prefix-cleanup" ]]

mkdir --mode=0700 "$reconciler_gate_prefix-orphan"
"$reconciler" "$reconciler_orphan_state" "$reconciler_gate_prefix-orphan" 1 "$intervention_latch" 5
[[ ! -e "$reconciler_orphan_state" ]]
[[ ! -e "$reconciler_gate_prefix-orphan" ]]

ln -s cleanup "$tombstone_race_state/ownership"
ln -s "$rm_pause_fixture" "$tombstone_wrapper_directory/rm"
mkdir --mode=0700 "$reconciler_gate_prefix-tombstone"
PATH="$tombstone_wrapper_directory:$PATH" \
  ORGMEMORY_REAL_RM="$real_rm" \
  ORGMEMORY_TOMBSTONE_READY="$tombstone_ready" \
  ORGMEMORY_TOMBSTONE_RELEASE="$tombstone_release" \
  "$reconciler" "$tombstone_race_state" "$reconciler_gate_prefix-tombstone" 1 "$intervention_latch" 5 &
tombstone_reconciler_pid=$!
for _ in {1..100}; do
  [[ -e "$tombstone_ready" ]] && break
  sleep 0.05
done
[[ -e "$tombstone_ready" ]]
[[ ! -e "$tombstone_race_state" ]]
# A redundant reconciler must recover a tombstone even if the mover is killed or
# paused after the atomic rename and before credential deletion.
"$reconciler" "$tombstone_race_state" "$reconciler_gate_prefix-tombstone" 1 "$intervention_latch" 5
[[ -z "$(compgen -G "${tombstone_race_state}.cleanup.*" || true)" ]]
set +e
"$runner" "$tombstone_race_state" >/dev/null 2>&1
tombstone_losing_controller_exit=$?
set -e
[[ "$tombstone_losing_controller_exit" -eq 75 ]]
[[ ! -e "$tombstone_race_state" ]]
touch "$tombstone_release"
wait "$tombstone_reconciler_pid"
[[ ! -e "$reconciler_gate_prefix-tombstone" ]]

sleep 20 &
pidfd_target_pid=$!
pidfd_target_starttime="$(cut -d ' ' -f 22 "/proc/$pidfd_target_pid/stat")"
"$signal_helper" "$pidfd_target_pid" "$pidfd_target_starttime" TERM
wait "$pidfd_target_pid" 2>/dev/null || true

sleep 20 &
pidfd_target_pid=$!
set +e
"$signal_helper" "$pidfd_target_pid" 1 TERM
pidfd_mismatch_exit=$?
set -e
[[ "$pidfd_mismatch_exit" -eq 3 ]]
kill -0 "$pidfd_target_pid"
kill -TERM "$pidfd_target_pid"
wait "$pidfd_target_pid" 2>/dev/null || true
pidfd_target_pid=""

printf 'Detached deployment watchdog contract passed.\n'
