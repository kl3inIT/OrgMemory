#!/usr/bin/env bash
set -Eeuo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
deploy_script="$repo_root/infrastructure/deployment/scripts/deploy.sh"
bootstrap_script="$repo_root/infrastructure/deployment/scripts/bootstrap-openfga.sh"
temporary_root="$(mktemp -d)"

cleanup() {
  if [[ -n "${temporary_root:-}" && "$temporary_root" == /tmp/* ]]; then
    rm -rf -- "$temporary_root"
  fi
}
trap cleanup EXIT

old_sha="1111111111111111111111111111111111111111"
candidate_sha="2222222222222222222222222222222222222222"
second_candidate_sha="3333333333333333333333333333333333333333"
old_model_id="01J00000000000000000000000"
new_model_id="01J11111111111111111111111"
store_id="01J22222222222222222222222"
old_model_sha256="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
model_file="$temporary_root/model.fga"
compose_file="$temporary_root/compose.production.yaml"
stub_bin="$temporary_root/bin"
docker_log="$temporary_root/docker.log"
keycloak_script="$temporary_root/configure-keycloak.sh"
smoke_success_script="$temporary_root/smoke-success.sh"
smoke_rollback_script="$temporary_root/smoke-rollback.sh"
smoke_count="$temporary_root/smoke.count"
coordination_script="$temporary_root/team-dev-coordination.sh"
coordination_log="$temporary_root/coordination.log"

install -d -m 0700 "$stub_bin"
printf 'model\n  schema 1.1\n\ntype user\n' > "$model_file"
printf 'services: {}\n' > "$compose_file"

cat > "$stub_bin/docker" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "$ORGMEMORY_TEST_DOCKER_LOG"
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
  reference="$(grep -E "^${key}=" "$ORGMEMORY_ENV_FILE" | tail -1 | cut -d= -f2-)"
  digest="${reference##*@}"
  [[ "$digest" =~ ^sha256:[0-9a-f]{64}$ ]]
  printf '["%s@%s"]\n' "$repository" "$digest"
elif [[ "$*" == *"openfga-model-write"* ]]; then
  printf '{"authorization_model_id":"%s"}\n' "$ORGMEMORY_TEST_NEW_MODEL_ID"
elif [[ "$*" == *"openfga-bootstrap"* ]]; then
  printf \
    '{"store":{"id":"%s"},"model":{"authorization_model_id":"%s"}}\n' \
    "$ORGMEMORY_TEST_STORE_ID" \
    "$ORGMEMORY_TEST_NEW_MODEL_ID"
fi
SH
chmod +x "$stub_bin/docker"

cat > "$keycloak_script" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
if [[ "${1:-}" == "--print-login-theme" ]]; then
  printf 'keycloak\n'
fi
SH
chmod +x "$keycloak_script"

cat > "$smoke_success_script" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
SH
chmod +x "$smoke_success_script"

cat > "$smoke_rollback_script" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
count=0
if [[ -f "$ORGMEMORY_TEST_SMOKE_COUNT" ]]; then
  count="$(cat "$ORGMEMORY_TEST_SMOKE_COUNT")"
fi
count=$((count + 1))
printf '%s\n' "$count" > "$ORGMEMORY_TEST_SMOKE_COUNT"
if [[ "$count" -eq 1 ]]; then
  exit 1
fi
SH
chmod +x "$smoke_rollback_script"

cat > "$coordination_script" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "$ORGMEMORY_TEST_COORDINATION_LOG"
SH
chmod +x "$coordination_script"

write_environment() {
  local path="$1"
  local model_sha256="${2:-}"

  cat > "$path" <<EOF
ORGMEMORY_API_IMAGE=ghcr.io/kl3init/orgmemory-api@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
ORGMEMORY_WORKER_IMAGE=ghcr.io/kl3init/orgmemory-worker@sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
ORGMEMORY_MCP_IMAGE=ghcr.io/kl3init/orgmemory-mcp@sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
ORGMEMORY_WEB_IMAGE=ghcr.io/kl3init/orgmemory-web@sha256:dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd
ORGMEMORY_KEYCLOAK_IMAGE=ghcr.io/kl3init/orgmemory-keycloak@sha256:eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee
ORGMEMORY_POSTGRES_IMAGE=ghcr.io/kl3init/orgmemory-postgres-rag@sha256:ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff
ORGMEMORY_OPENFGA_STORE_ID=$store_id
ORGMEMORY_OPENFGA_AUTHORIZATION_MODEL_ID=$old_model_id
ORGMEMORY_OPENFGA_MODEL_SHA256=$model_sha256
ORGMEMORY_REQUIRE_PUBLIC_SMOKE=false
EOF
  chmod 0600 "$path"
}

run_deploy() {
  local environment_file="$1"
  local runtime_root="$2"
  local smoke_script="$3"
  local sha="$4"

  PATH="$stub_bin:$PATH" \
  ORGMEMORY_REPO_ROOT="$repo_root" \
  ORGMEMORY_COMPOSE_FILE="$compose_file" \
  ORGMEMORY_ENV_FILE="$environment_file" \
  ORGMEMORY_RUNTIME_ROOT="$runtime_root" \
  ORGMEMORY_OPENFGA_MODEL_FILE="$model_file" \
  ORGMEMORY_KEYCLOAK_CONFIGURATION_SCRIPT="$keycloak_script" \
  ORGMEMORY_SMOKE_SCRIPT="$smoke_script" \
  ORGMEMORY_TEAM_DEV_COORDINATION_SCRIPT="$coordination_script" \
  ORGMEMORY_TEST_COORDINATION_LOG="$coordination_log" \
  ORGMEMORY_TEST_DOCKER_LOG="$docker_log" \
  ORGMEMORY_TEST_NEW_MODEL_ID="$new_model_id" \
  ORGMEMORY_TEST_SMOKE_COUNT="$smoke_count" \
  ORGMEMORY_RELEASE_API_IMAGE=ghcr.io/kl3init/orgmemory-api@sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb \
  ORGMEMORY_RELEASE_WORKER_IMAGE=ghcr.io/kl3init/orgmemory-worker@sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb \
  ORGMEMORY_RELEASE_MCP_IMAGE=ghcr.io/kl3init/orgmemory-mcp@sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb \
  ORGMEMORY_RELEASE_WEB_IMAGE=ghcr.io/kl3init/orgmemory-web@sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb \
  ORGMEMORY_RELEASE_KEYCLOAK_IMAGE=ghcr.io/kl3init/orgmemory-keycloak@sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb \
  ORGMEMORY_RELEASE_POSTGRES_IMAGE=ghcr.io/kl3init/orgmemory-postgres-rag@sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb \
    "$deploy_script" "$sha"
}

run_bootstrap() {
  local environment_file="$1"

  PATH="$stub_bin:$PATH" \
  ORGMEMORY_REPO_ROOT="$repo_root" \
  ORGMEMORY_COMPOSE_FILE="$compose_file" \
  ORGMEMORY_ENV_FILE="$environment_file" \
  ORGMEMORY_OPENFGA_MODEL_FILE="$model_file" \
  ORGMEMORY_TEST_DOCKER_LOG="$docker_log" \
  ORGMEMORY_TEST_NEW_MODEL_ID="$new_model_id" \
  ORGMEMORY_TEST_STORE_ID="$store_id" \
    "$bootstrap_script"
}

assert_model_configuration() {
  local environment_file="$1"
  local expected_model_id="$2"
  local expected_sha256="$3"

  grep -Fxq \
    "ORGMEMORY_OPENFGA_AUTHORIZATION_MODEL_ID=$expected_model_id" \
    "$environment_file"
  grep -Fxq \
    "ORGMEMORY_OPENFGA_MODEL_SHA256=$expected_sha256" \
    "$environment_file"
}

assert_age_reconciliation_order() {
  local stop_line
  local reconcile_line
  local application_up_line
  stop_line="$(grep -n -- 'stop --timeout 45 worker api' "$docker_log" | head -1 | cut -d: -f1)"
  reconcile_line="$(grep -n -- '--profile ops run --rm --no-deps age-reconcile' "$docker_log" | head -1 | cut -d: -f1)"
  application_up_line="$(
    grep -n -- 'up -d --wait --wait-timeout 240 --remove-orphans' "$docker_log" \
      | head -1 \
      | cut -d: -f1
  )"
  if [[ -z "$stop_line" || -z "$reconcile_line" || -z "$application_up_line" ]] || \
     [[ "$stop_line" -ge "$reconcile_line" ]] || \
     [[ "$reconcile_line" -ge "$application_up_line" ]]; then
    printf 'AGE reconciliation did not quiesce worker/API before the application start.\n' >&2
    exit 1
  fi
}

release_model_sha256="$(sha256sum "$model_file" | awk '{ print $1 }')"

# First-store bootstrap records one coherent store/model/digest tuple.
bootstrap_environment="$temporary_root/bootstrap.env"
cat > "$bootstrap_environment" <<'EOF'
ORGMEMORY_OPENFGA_STORE_ID=
ORGMEMORY_OPENFGA_AUTHORIZATION_MODEL_ID=
ORGMEMORY_OPENFGA_MODEL_SHA256=
EOF
chmod 0600 "$bootstrap_environment"
: > "$docker_log"
run_bootstrap "$bootstrap_environment"
grep -Fxq "ORGMEMORY_OPENFGA_STORE_ID=$store_id" "$bootstrap_environment"
assert_model_configuration \
  "$bootstrap_environment" \
  "$new_model_id" \
  "$release_model_sha256"

# A legacy environment has no model digest. Its first deployment must write and
# pin the repository model before the application stack is recreated.
upgrade_root="$temporary_root/upgrade-runtime"
upgrade_environment="$temporary_root/upgrade.env"
install -d -m 0700 "$upgrade_root/releases"
write_environment "$upgrade_environment"
: > "$docker_log"
: > "$coordination_log"
run_deploy \
  "$upgrade_environment" \
  "$upgrade_root" \
  "$smoke_success_script" \
  "$candidate_sha"

assert_model_configuration \
  "$upgrade_environment" \
  "$new_model_id" \
  "$release_model_sha256"
grep -Fxq "$candidate_sha" "$upgrade_root/current-commit"
grep -q 'openfga-model-write' "$docker_log"
grep -Fq "acquire maintenance deploy-$candidate_sha deployment deployment-host $candidate_sha 1800" "$coordination_log"
grep -Fq "release maintenance deploy-$candidate_sha" "$coordination_log"
assert_age_reconciliation_order

model_write_line="$(grep -n 'openfga-model-write' "$docker_log" | head -1 | cut -d: -f1)"
application_up_line="$(
  grep -n -- 'up -d --wait --wait-timeout 240 --remove-orphans' "$docker_log" \
    | head -1 \
    | cut -d: -f1
)"
if [[ "$model_write_line" -ge "$application_up_line" ]]; then
  printf 'The application stack started before the changed model was pinned.\n' >&2
  exit 1
fi

# A second release with identical model bytes must retain the pinned version and
# avoid creating an unnecessary immutable model.
no_op_root="$temporary_root/no-op-runtime"
no_op_environment="$temporary_root/no-op.env"
install -d -m 0700 "$no_op_root/releases"
install -m 0600 "$upgrade_environment" "$no_op_environment"
printf '%s\n' "$candidate_sha" > "$no_op_root/current-commit"
: > "$docker_log"
: > "$coordination_log"
ORGMEMORY_FORCE_SHARED_MAINTENANCE=false \
run_deploy \
  "$no_op_environment" \
  "$no_op_root" \
  "$smoke_success_script" \
  "$second_candidate_sha"

assert_model_configuration \
  "$no_op_environment" \
  "$new_model_id" \
  "$release_model_sha256"
if grep -q 'openfga-model-write' "$docker_log"; then
  printf 'An unchanged authorization model created another immutable version.\n' >&2
  exit 1
fi
if [[ -s "$coordination_log" ]]; then
  printf 'A release without schema/model changes acquired maintenance.\n' >&2
  exit 1
fi
assert_age_reconciliation_order

# A failed canary after a changed model write must restore the previous image,
# model ID, and digest. The newly written immutable model remains inert.
rollback_root="$temporary_root/rollback-runtime"
rollback_environment="$temporary_root/rollback.env"
install -d -m 0700 "$rollback_root/releases"
write_environment "$rollback_environment" "$old_model_sha256"
printf '%s\n' "$old_sha" > "$rollback_root/current-commit"
: > "$docker_log"
: > "$smoke_count"
: > "$coordination_log"

set +e
run_deploy \
  "$rollback_environment" \
  "$rollback_root" \
  "$smoke_rollback_script" \
  "$candidate_sha"
status="$?"
set -e

if [[ "$status" -eq 0 ]]; then
  printf 'Expected the forced production canary to fail.\n' >&2
  exit 1
fi

assert_model_configuration \
  "$rollback_environment" \
  "$old_model_id" \
  "$old_model_sha256"
grep -Fxq \
  "ORGMEMORY_API_IMAGE=ghcr.io/kl3init/orgmemory-api@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" \
  "$rollback_environment"
grep -Fxq "2" "$smoke_count"
grep -Fxq "$old_sha" "$rollback_root/current-commit"
grep -q 'openfga-model-write' "$docker_log"
grep -q 'up --pull never -d --wait --wait-timeout 240 --remove-orphans' "$docker_log"
grep -Fq "release maintenance deploy-$candidate_sha" "$coordination_log"

printf 'OpenFGA model rollout, no-op, and rollback contracts passed.\n'
