#!/usr/bin/env bash
set -Eeuo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
deploy_script="$repo_root/infrastructure/deployment/scripts/deploy-docs.sh"
temporary_root="$(mktemp -d)"

cleanup() {
  if [[ -n "${temporary_root:-}" && "$temporary_root" == /tmp/* ]]; then
    rm -rf -- "$temporary_root"
  fi
}
trap cleanup EXIT

runtime_root="$temporary_root/runtime"
environment_file="$temporary_root/.env.docs.production"
compose_file="$temporary_root/compose.docs.yaml"
stub_bin="$temporary_root/bin"
smoke_script="$temporary_root/smoke.sh"
production_smoke_script="$repo_root/infrastructure/deployment/scripts/smoke-docs.sh"
docker_log="$temporary_root/docker.log"
curl_log="$temporary_root/curl.log"
smoke_count="$temporary_root/smoke.count"
old_sha="1111111111111111111111111111111111111111"
candidate_sha="2222222222222222222222222222222222222222"

install -d -m 0700 "$runtime_root/releases" "$stub_bin"
printf 'ORGMEMORY_DOCS_IMAGE=ghcr.io/kl3init/orgmemory-docs:sha-%s\n' \
  "$old_sha" > "$environment_file"
chmod 0600 "$environment_file"
install -m 0600 "$environment_file" "$runtime_root/current.env"
printf '%s\n' "$old_sha" > "$runtime_root/current-commit"
printf 'services: {}\n' > "$compose_file"

cat > "$stub_bin/docker" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "$ORGMEMORY_DOCS_TEST_DOCKER_LOG"
SH
chmod +x "$stub_bin/docker"

cat > "$smoke_script" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
count=0
if [[ -f "$ORGMEMORY_DOCS_TEST_SMOKE_COUNT" ]]; then
  count="$(cat "$ORGMEMORY_DOCS_TEST_SMOKE_COUNT")"
fi
count=$((count + 1))
printf '%s\n' "$count" > "$ORGMEMORY_DOCS_TEST_SMOKE_COUNT"
if [[ "$count" -eq 1 ]]; then
  exit 1
fi
SH
chmod +x "$smoke_script"

set +e
PATH="$stub_bin:$PATH" \
ORGMEMORY_REPO_ROOT="$repo_root" \
ORGMEMORY_DOCS_COMPOSE_FILE="$compose_file" \
ORGMEMORY_DOCS_ENV_FILE="$environment_file" \
ORGMEMORY_DOCS_RUNTIME_ROOT="$runtime_root" \
ORGMEMORY_DOCS_SMOKE_SCRIPT="$smoke_script" \
ORGMEMORY_DOCS_TEST_DOCKER_LOG="$docker_log" \
ORGMEMORY_DOCS_TEST_SMOKE_COUNT="$smoke_count" \
  "$deploy_script" "$candidate_sha"
status="$?"
set -e

if [[ "$status" -eq 0 ]]; then
  printf 'Expected the forced canary to fail.\n' >&2
  exit 1
fi

grep -Fxq \
  "ORGMEMORY_DOCS_IMAGE=ghcr.io/kl3init/orgmemory-docs:sha-$old_sha" \
  "$environment_file"
grep -Fxq "2" "$smoke_count"

if grep -Eq \
  '(^|[[:space:]])(api|worker|mcp|web|keycloak|postgres)([[:space:]]|$)' \
  "$docker_log"; then
  printf 'Docs rollback attempted to operate on a product service.\n' >&2
  exit 1
fi

grep -Eq 'pull orgmemory-docs$' "$docker_log"
grep -Eq 'up -d --wait --wait-timeout 60 orgmemory-docs$' "$docker_log"

printf 'Forced docs canary rollback passed.\n'

cat > "$stub_bin/curl" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "$ORGMEMORY_DOCS_TEST_CURL_LOG"
url="${!#}"
if [[ "$url" == "https://docs.example.test/" ]]; then
  printf '200 https://docs.example.test/docs/overview'
else
  printf '200'
fi
SH
chmod +x "$stub_bin/curl"

printf '%s\n' \
  'ORGMEMORY_DOCS_IMAGE=ghcr.io/kl3init/orgmemory-docs:sha-test' \
  'ORGMEMORY_DOCS_PUBLIC_URL=https://docs.example.test' \
  > "$environment_file"
chmod 0600 "$environment_file"
: > "$docker_log"

PATH="$stub_bin:$PATH" \
ORGMEMORY_REPO_ROOT="$repo_root" \
ORGMEMORY_DOCS_COMPOSE_FILE="$compose_file" \
ORGMEMORY_DOCS_ENV_FILE="$environment_file" \
ORGMEMORY_DOCS_REQUIRE_PUBLIC_SMOKE=true \
ORGMEMORY_DOCS_TEST_CURL_LOG="$curl_log" \
ORGMEMORY_DOCS_TEST_DOCKER_LOG="$docker_log" \
  "$production_smoke_script"

grep -Fq 'http://127.0.0.1:3000/docs/overview' "$docker_log"
if grep -Eq 'http://127\.0\.0\.1:3000/$' "$docker_log"; then
  printf 'Internal smoke still treats the redirecting root as a document.\n' >&2
  exit 1
fi
grep -Fq -- '--location' "$curl_log"
grep -Fq 'https://docs.example.test/' "$curl_log"

printf 'Docs root redirect smoke passed.\n'
