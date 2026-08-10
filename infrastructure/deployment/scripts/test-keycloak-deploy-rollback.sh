#!/usr/bin/env bash
set -Eeuo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
deploy_script="$repo_root/infrastructure/deployment/scripts/deploy.sh"
model_file="$repo_root/integrations/authorization-openfga/src/main/openfga/model.fga"
tmp_root="$(mktemp -d /tmp/orgmemory-deploy-rollback-test.XXXXXX)"
commit_sha="1111111111111111111111111111111111111111"
model_sha="$(sha256sum "$model_file" | cut -d ' ' -f 1)"

cleanup() {
  case "$tmp_root" in
    /tmp/orgmemory-deploy-rollback-test.*)
      rm -rf -- "$tmp_root"
      ;;
    *)
      printf 'Refusing unsafe test cleanup: %s\n' "$tmp_root" >&2
      ;;
  esac
}
trap cleanup EXIT

write_environment() {
  local path="$1"
  cat >"$path" <<EOF
ORGMEMORY_API_IMAGE=ghcr.io/kl3init/orgmemory-api:sha-2222222222222222222222222222222222222222
ORGMEMORY_WORKER_IMAGE=ghcr.io/kl3init/orgmemory-worker:sha-2222222222222222222222222222222222222222
ORGMEMORY_MCP_IMAGE=ghcr.io/kl3init/orgmemory-mcp:sha-2222222222222222222222222222222222222222
ORGMEMORY_WEB_IMAGE=ghcr.io/kl3init/orgmemory-web:sha-2222222222222222222222222222222222222222
ORGMEMORY_KEYCLOAK_IMAGE=ghcr.io/kl3init/orgmemory-keycloak:sha-2222222222222222222222222222222222222222
ORGMEMORY_POSTGRES_IMAGE=ghcr.io/kl3init/orgmemory-postgres-rag:sha-2222222222222222222222222222222222222222
ORGMEMORY_SERVICE_VERSION=previous
ORGMEMORY_OPENFGA_STORE_ID=store
ORGMEMORY_OPENFGA_AUTHORIZATION_MODEL_ID=model
ORGMEMORY_OPENFGA_MODEL_SHA256=$model_sha
ORGMEMORY_REQUIRE_PUBLIC_SMOKE=false
EOF
  chmod 0600 "$path"
}

write_stubs() {
  local case_dir="$1"
  mkdir -p "$case_dir/bin"
  cat >"$case_dir/bin/docker" <<'SH'
#!/usr/bin/env bash
set -eu
printf 'docker %s\n' "$*" >>"$TEST_LOG"
if [[ "${1:-}" == "run" && "${TEST_THEME_ARTIFACT_MISSING:-false}" == "true" ]]; then
  exit 1
fi
if [[ "${1:-}" == "compose" && " $* " == *" up "* && \
      "${TEST_STALE_CONTAINERS:-false}" != "true" ]]; then
  state_temporary="$TEST_CONTAINER_STATE.tmp"
  : >"$state_temporary"
  for key in \
    ORGMEMORY_API_IMAGE \
    ORGMEMORY_WORKER_IMAGE \
    ORGMEMORY_MCP_IMAGE \
    ORGMEMORY_WEB_IMAGE \
    ORGMEMORY_KEYCLOAK_IMAGE \
    ORGMEMORY_POSTGRES_IMAGE; do
    reference="$(grep -E "^${key}=" "$ORGMEMORY_ENV_FILE" | tail -1 | cut -d= -f2-)"
    if [[ "$reference" =~ @sha256:([0-9a-f]{64})$ ]]; then
      printf '%s=%s\n' "$key" "${BASH_REMATCH[1]}" >>"$state_temporary"
    fi
  done
  mv -f -- "$state_temporary" "$TEST_CONTAINER_STATE"
fi
if [[ "${1:-}" == "compose" && " $* " == *" ps --all --quiet "* ]]; then
  service="${!#}"
  case "$service" in
    api) hex=a ;;
    worker) hex=b ;;
    mcp) hex=c ;;
    web) hex=d ;;
    keycloak) hex=e ;;
    postgres-bootstrap) hex=f ;;
    *) exit 65 ;;
  esac
  printf 'container-%s\n' "$hex"
elif [[ "${1:-}" == "container" && "${2:-}" == "inspect" ]]; then
  hex="${3#container-}"
  if [[ "${5:-}" == *State.Status* ]]; then
    if [[ "$hex" == f ]]; then
      printf 'exited false false\n'
    elif [[ "${TEST_STOPPED_WORKER:-false}" == "true" && "$hex" == b ]]; then
      printf 'exited false false\n'
    else
      printf 'running true false\n'
    fi
  elif [[ "${5:-}" == *State.ExitCode* ]]; then
    printf '0\n'
  else
    printf 'sha256:%064s\n' "$hex" | tr ' ' "$hex"
  fi
elif [[ "${1:-}" == "image" && "${2:-}" == "inspect" ]]; then
  image_id="$3"
  hex="${image_id#sha256:}"
  case "${hex:0:1}" in
    a) repository=ghcr.io/kl3init/orgmemory-api; key=ORGMEMORY_API_IMAGE ;;
    b) repository=ghcr.io/kl3init/orgmemory-worker; key=ORGMEMORY_WORKER_IMAGE ;;
    c) repository=ghcr.io/kl3init/orgmemory-mcp; key=ORGMEMORY_MCP_IMAGE ;;
    d) repository=ghcr.io/kl3init/orgmemory-web; key=ORGMEMORY_WEB_IMAGE ;;
    e) repository=ghcr.io/kl3init/orgmemory-keycloak; key=ORGMEMORY_KEYCLOAK_IMAGE ;;
    f) repository=ghcr.io/kl3init/orgmemory-postgres-rag; key=ORGMEMORY_POSTGRES_IMAGE ;;
    *) exit 65 ;;
  esac
  if [[ -f "$TEST_CONTAINER_STATE" ]]; then
    digest="$(grep -E "^${key}=" "$TEST_CONTAINER_STATE" | tail -1 | cut -d= -f2-)"
  else
    digest="$(printf '%064s' "${hex:0:1}" | tr ' ' "${hex:0:1}")"
  fi
  [[ "$digest" =~ ^[0-9a-f]{64}$ ]]
  printf '["%s@sha256:%s"]\n' "$repository" "$digest"
fi
exit 0
SH
  cat >"$case_dir/configure" <<'SH'
#!/usr/bin/env bash
set -eu
printf 'configure %s theme=%s\n' "$*" "${ORGMEMORY_KEYCLOAK_LOGIN_THEME:-}" >>"$TEST_LOG"
case "${1:-}" in
  --print-login-theme)
    printf '%s\n' "$TEST_PREVIOUS_THEME"
    ;;
  --restore-login-theme)
    if [[ "${TEST_RESTORE_DELAY:-false}" == "true" ]]; then
      sleep 0.2
    fi
    if [[ "$TEST_RESTORE_FAIL" == "true" ]]; then
      exit 42
    fi
    ;;
esac
SH
  cat >"$case_dir/smoke" <<'SH'
#!/usr/bin/env bash
set -eu
printf 'smoke\n' >>"$TEST_LOG"
smoke_count=0
if [[ -f "$TEST_SMOKE_COUNT" ]]; then
  read -r smoke_count <"$TEST_SMOKE_COUNT"
fi
smoke_count=$((smoke_count + 1))
printf '%s\n' "$smoke_count" >"$TEST_SMOKE_COUNT"
if [[ "$TEST_SMOKE_FAIL" == "true" &&
      ( "$TEST_SMOKE_FAIL_MODE" == "always" || "$smoke_count" -eq 1 ) ]]; then
  exit 1
fi
SH
  cat >"$case_dir/coordination" <<'SH'
#!/usr/bin/env bash
set -eu
printf 'coordination %s\n' "$*" >>"$TEST_LOG"
SH
  chmod 0700 "$case_dir/bin/docker" "$case_dir/configure" "$case_dir/smoke" "$case_dir/coordination"
}

run_case() {
  local name="$1"
  local previous_theme="$2"
  local desired_theme="$3"
  local restore_fail="$4"
  local smoke_fail="$5"
  local release_mode="${6:-digest}"
  local gate_decision="${7:-}"
  local previous_mode="${8:-tag}"
  local stale_containers="${9:-false}"
  local theme_artifact_missing="${10:-false}"
  local stopped_worker="${11:-false}"
  local smoke_fail_mode="${12:-first}"
  local case_dir="$tmp_root/$name"
  local runtime="$case_dir/runtime"
  local environment="$case_dir/production.env"
  mkdir -p "$runtime/releases"
  write_environment "$environment"
  if [[ "$previous_mode" == "mismatched-digest" ]]; then
    python3 - "$environment" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")
text = text.replace(
    "ORGMEMORY_API_IMAGE=ghcr.io/kl3init/orgmemory-api:sha-" + "2" * 40,
    "ORGMEMORY_API_IMAGE=ghcr.io/kl3init/orgmemory-api@sha256:" + "9" * 64,
)
path.write_text(text, encoding="utf-8")
PY
  fi
  printf '%040d\n' 2 >"$runtime/current-commit"
  write_stubs "$case_dir"
  : >"$case_dir/events.log"

  local digest="bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
  local release_suffix="@sha256:$digest"
  if [[ "$release_mode" == "tag" ]]; then
    release_suffix=":sha-$commit_sha"
  fi
  local gate_directory=""
  local gate_helper_pid=""
  if [[ -n "$gate_decision" ]]; then
    gate_directory="/tmp/orgmemory-deploy-gate.test-$name-$$"
    if [[ "$gate_decision" == "pre-rejected" ]]; then
      : >"$gate_directory.rejected"
    elif [[ "$gate_decision" != "terminated" ]]; then
      (
        for _ in {1..500}; do
          if [[ -f "$gate_directory/ready" ]]; then
            if [[ "$gate_decision" == "conflict" ]]; then
              ln -s rejected "$gate_directory/decision"
              if ln -s approved "$gate_directory/decision" 2>/dev/null; then
                exit 1
              fi
            else
              ln -s "$gate_decision" "$gate_directory/decision"
            fi
            exit 0
          fi
          sleep 0.01
        done
        exit 1
      ) &
      gate_helper_pid=$!
    fi
  fi
  set +e
  PATH="$case_dir/bin:$PATH" \
  TEST_LOG="$case_dir/events.log" \
  TEST_CONTAINER_STATE="$case_dir/container-state" \
  TEST_STALE_CONTAINERS="$stale_containers" \
  TEST_STOPPED_WORKER="$stopped_worker" \
  TEST_THEME_ARTIFACT_MISSING="$theme_artifact_missing" \
  TEST_PREVIOUS_THEME="$previous_theme" \
  TEST_RESTORE_FAIL="$restore_fail" \
  TEST_RESTORE_DELAY="$([[ "$gate_decision" == "terminated" ]] && printf true || printf false)" \
  TEST_SMOKE_FAIL="$smoke_fail" \
  TEST_SMOKE_FAIL_MODE="$smoke_fail_mode" \
  TEST_SMOKE_COUNT="$case_dir/smoke-count" \
  ORGMEMORY_REPO_ROOT="$repo_root" \
  ORGMEMORY_RUNTIME_ROOT="$runtime" \
  ORGMEMORY_ENV_FILE="$environment" \
  ORGMEMORY_COMPOSE_FILE="$repo_root/infrastructure/deployment/compose.production.yaml" \
  ORGMEMORY_OPENFGA_MODEL_FILE="$model_file" \
  ORGMEMORY_KEYCLOAK_CONFIGURATION_SCRIPT="$case_dir/configure" \
  ORGMEMORY_SMOKE_SCRIPT="$case_dir/smoke" \
  ORGMEMORY_TEAM_DEV_COORDINATION_SCRIPT="$case_dir/coordination" \
  ORGMEMORY_FORCE_SHARED_MAINTENANCE=false \
  ORGMEMORY_EXTERNAL_GATE_DIRECTORY="$gate_directory" \
  ORGMEMORY_KEYCLOAK_LOGIN_THEME="$desired_theme" \
  ORGMEMORY_RELEASE_API_IMAGE="ghcr.io/kl3init/orgmemory-api$release_suffix" \
  ORGMEMORY_RELEASE_WORKER_IMAGE="ghcr.io/kl3init/orgmemory-worker$release_suffix" \
  ORGMEMORY_RELEASE_MCP_IMAGE="ghcr.io/kl3init/orgmemory-mcp$release_suffix" \
  ORGMEMORY_RELEASE_WEB_IMAGE="ghcr.io/kl3init/orgmemory-web$release_suffix" \
  ORGMEMORY_RELEASE_KEYCLOAK_IMAGE="ghcr.io/kl3init/orgmemory-keycloak$release_suffix" \
  ORGMEMORY_RELEASE_POSTGRES_IMAGE="ghcr.io/kl3init/orgmemory-postgres-rag$release_suffix" \
    "$deploy_script" "$commit_sha" >"$case_dir/stdout.log" 2>"$case_dir/stderr.log" &
  local deployment_pid=$!
  if [[ "$gate_decision" == "terminated" ]]; then
    (
      for _ in {1..500}; do
        if [[ -f "$gate_directory/ready" ]]; then
          kill -TERM "$deployment_pid"
          sleep 0.05
          kill -TERM "$deployment_pid" 2>/dev/null || true
          exit 0
        fi
        sleep 0.01
      done
      exit 1
    ) &
    gate_helper_pid=$!
  fi
  wait "$deployment_pid"
  status=$?
  if [[ -n "$gate_helper_pid" ]]; then
    wait "$gate_helper_pid" || status=1
  fi
  set -e
  printf '%s\n' "$status" >"$case_dir/status"
}

run_case restore-success keycloak orgmemory-shadcn false true
run_case restore-failure keycloak orgmemory-shadcn true true
run_case rollback-smoke-failure keycloak orgmemory-shadcn false true digest "" tag false false false always
run_case stock-rollback orgmemory-shadcn keycloak false false
run_case mutable-release keycloak keycloak false false tag
run_case browser-approve keycloak orgmemory-shadcn false false digest approved
run_case browser-reject keycloak orgmemory-shadcn false false digest rejected
run_case browser-cancel-before-ready keycloak orgmemory-shadcn false false digest pre-rejected
run_case browser-terminated keycloak orgmemory-shadcn false false digest terminated
run_case browser-decision-conflict keycloak orgmemory-shadcn false false digest conflict
run_case previous-digest-mismatch keycloak orgmemory-shadcn false false digest "" mismatched-digest
run_case stale-containers keycloak orgmemory-shadcn false false digest "" tag true
run_case stock-missing-rollback-theme orgmemory-shadcn keycloak false false digest "" tag false true
run_case stopped-worker keycloak orgmemory-shadcn false false digest "" tag false false true

python3 - "$tmp_root" <<'PY'
from pathlib import Path
import sys

root = Path(sys.argv[1])

def events(name):
    return (root / name / "events.log").read_text(encoding="utf-8").splitlines()

def status(name):
    return int((root / name / "status").read_text(encoding="utf-8"))

success = events("restore-success")
restore_index = next(i for i, line in enumerate(success) if "--restore-login-theme" in line)
after_restore = success[restore_index + 1:]
assert any(line.startswith("docker ") and " up --pull never -d --wait --wait-timeout 240 --remove-orphans" in line for line in after_restore), success
assert not any(line.startswith("docker ") and " pull" in line for line in after_restore), success
assert status("restore-success") != 0
restored_env = (root / "restore-success" / "production.env").read_text(encoding="utf-8")
assert "ORGMEMORY_SERVICE_VERSION=previous" in restored_env
expected_previous_digests = {
    "api": "a",
    "worker": "b",
    "mcp": "c",
    "web": "d",
    "keycloak": "e",
    "postgres-rag": "f",
}
for component, hex_digit in expected_previous_digests.items():
    assert f"ghcr.io/kl3init/orgmemory-{component}@sha256:" + hex_digit * 64 in restored_env

failure = events("restore-failure")
restore_index = next(i for i, line in enumerate(failure) if "--restore-login-theme" in line)
assert not any(line.startswith("docker ") for line in failure[restore_index + 1:]), failure
assert status("restore-failure") == 70, status("restore-failure")
candidate_env = (root / "restore-failure" / "production.env").read_text(encoding="utf-8")
assert "ORGMEMORY_SERVICE_VERSION=1111111111111111111111111111111111111111" in candidate_env
assert (root / "restore-failure" / "runtime" / "deployment-intervention-required").is_file()
assert not (root / "restore-failure" / "runtime" / "current-commit").exists()

rollback_smoke_failure = events("rollback-smoke-failure")
assert rollback_smoke_failure.count("smoke") == 2, rollback_smoke_failure
assert status("rollback-smoke-failure") == 70
assert (root / "rollback-smoke-failure" / "runtime" / "deployment-intervention-required").is_file()
assert not (root / "rollback-smoke-failure" / "runtime" / "current-commit").exists()

stock = events("stock-rollback")
theme_index = next(i for i, line in enumerate(stock) if "--login-theme-only" in line and "theme=keycloak" in line)
pull_index = next(i for i, line in enumerate(stock) if line.startswith("docker ") and " pull" in line)
assert pull_index < theme_index, stock
assert status("stock-rollback") == 0, status("stock-rollback")
stock_env = (root / "stock-rollback" / "production.env").read_text(encoding="utf-8")
for line in stock_env.splitlines():
    if line.startswith("ORGMEMORY_") and line.endswith("_IMAGE"):
        raise AssertionError(line)
for key in (
    "ORGMEMORY_API_IMAGE",
    "ORGMEMORY_WORKER_IMAGE",
    "ORGMEMORY_MCP_IMAGE",
    "ORGMEMORY_WEB_IMAGE",
    "ORGMEMORY_KEYCLOAK_IMAGE",
    "ORGMEMORY_POSTGRES_IMAGE",
):
    value = next(line.split("=", 1)[1] for line in stock_env.splitlines() if line.startswith(key + "="))
    assert "@sha256:" in value, (key, value)

mutable = events("mutable-release")
assert status("mutable-release") != 0
assert not any(line.startswith("docker ") or line.startswith("configure ") for line in mutable), mutable

mismatched = events("previous-digest-mismatch")
assert status("previous-digest-mismatch") != 0
assert any("container inspect" in line for line in mismatched), mismatched
assert not any(" pull" in line or line.startswith("configure ") for line in mismatched), mismatched

stale = events("stale-containers")
assert status("stale-containers") != 0
assert any(" up --pull never -d --wait --wait-timeout 240 --remove-orphans" in line for line in stale), stale
stale_marker = (root / "stale-containers" / "runtime" / "current-commit").read_text().strip()
assert stale_marker == f"{2:040d}", stale_marker

missing_theme = events("stock-missing-rollback-theme")
assert status("stock-missing-rollback-theme") != 0
assert any(line.startswith("docker pull ") for line in missing_theme), missing_theme
assert not any("--login-theme-only" in line for line in missing_theme), missing_theme
missing_marker = (root / "stock-missing-rollback-theme" / "runtime" / "current-commit").read_text().strip()
assert missing_marker == f"{2:040d}", missing_marker

stopped = events("stopped-worker")
assert status("stopped-worker") != 0
assert any("container inspect" in line and "State.Status" in line for line in stopped), stopped
assert not any(" pull" in line for line in stopped), stopped

assert status("browser-approve") == 0
rejected = events("browser-reject")
assert status("browser-reject") != 0
restore_index = next(i for i, line in enumerate(rejected) if "--restore-login-theme" in line)
assert any(" up --pull never -d --wait --wait-timeout 240 --remove-orphans" in line for line in rejected[restore_index + 1:]), rejected

cancelled = events("browser-cancel-before-ready")
assert status("browser-cancel-before-ready") != 0
restore_index = next(i for i, line in enumerate(cancelled) if "--restore-login-theme" in line)
assert any(" up --pull never -d --wait --wait-timeout 240 --remove-orphans" in line for line in cancelled[restore_index + 1:]), cancelled
terminated = events("browser-terminated")
assert status("browser-terminated") == 143
restore_index = next(i for i, line in enumerate(terminated) if "--restore-login-theme" in line)
assert any(" up --pull never -d --wait --wait-timeout 240 --remove-orphans" in line for line in terminated[restore_index + 1:]), terminated
conflict = events("browser-decision-conflict")
assert status("browser-decision-conflict") != 0
restore_index = next(i for i, line in enumerate(conflict) if "--restore-login-theme" in line)
assert any(" up --pull never -d --wait --wait-timeout 240 --remove-orphans" in line for line in conflict[restore_index + 1:]), conflict
for case in ("browser-reject", "browser-cancel-before-ready", "browser-terminated", "browser-decision-conflict"):
    marker = (root / case / "runtime" / "current-commit").read_text().strip()
    assert marker == f"{2:040d}", (case, marker)
PY

printf 'Keycloak deployment rollback contract passed.\n'
