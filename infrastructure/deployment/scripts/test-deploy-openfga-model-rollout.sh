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

install -d -m 0700 "$stub_bin"
printf 'model\n  schema 1.1\n\ntype user\n' > "$model_file"
printf 'services: {}\n' > "$compose_file"

cat > "$stub_bin/docker" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "$ORGMEMORY_TEST_DOCKER_LOG"
if [[ "$*" == *"openfga-model-write"* ]]; then
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

write_environment() {
  local path="$1"
  local model_sha256="${2:-}"

  cat > "$path" <<EOF
ORGMEMORY_API_IMAGE=ghcr.io/kl3init/orgmemory-api:sha-$old_sha
ORGMEMORY_WORKER_IMAGE=ghcr.io/kl3init/orgmemory-worker:sha-$old_sha
ORGMEMORY_MCP_IMAGE=ghcr.io/kl3init/orgmemory-mcp:sha-$old_sha
ORGMEMORY_WEB_IMAGE=ghcr.io/kl3init/orgmemory-web:sha-$old_sha
ORGMEMORY_KEYCLOAK_IMAGE=ghcr.io/kl3init/orgmemory-keycloak:sha-$old_sha
ORGMEMORY_POSTGRES_IMAGE=ghcr.io/kl3init/orgmemory-postgres-rag:sha-$old_sha
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
  ORGMEMORY_TEST_DOCKER_LOG="$docker_log" \
  ORGMEMORY_TEST_NEW_MODEL_ID="$new_model_id" \
  ORGMEMORY_TEST_SMOKE_COUNT="$smoke_count" \
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

# A failed canary after a changed model write must restore the previous image,
# model ID, and digest. The newly written immutable model remains inert.
rollback_root="$temporary_root/rollback-runtime"
rollback_environment="$temporary_root/rollback.env"
install -d -m 0700 "$rollback_root/releases"
write_environment "$rollback_environment" "$old_model_sha256"
printf '%s\n' "$old_sha" > "$rollback_root/current-commit"
: > "$docker_log"
: > "$smoke_count"

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
  "ORGMEMORY_API_IMAGE=ghcr.io/kl3init/orgmemory-api:sha-$old_sha" \
  "$rollback_environment"
grep -Fxq "1" "$smoke_count"
grep -q 'openfga-model-write' "$docker_log"
grep -q 'up -d --remove-orphans' "$docker_log"

printf 'OpenFGA model rollout, no-op, and rollback contracts passed.\n'
