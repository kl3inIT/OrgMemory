#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "$#" -ne 1 || ! "$1" =~ ^[0-9a-f]{40}$ ]]; then
  printf 'Usage: %s <full-commit-sha>\n' "$0" >&2
  exit 64
fi

commit_sha="$1"
repo_root="${ORGMEMORY_REPO_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)}"
compose_file="${ORGMEMORY_DOCS_COMPOSE_FILE:-$repo_root/infrastructure/deployment/compose.docs.yaml}"
environment_file="${ORGMEMORY_DOCS_ENV_FILE:-$repo_root/.env.docs.production}"
runtime_root="${ORGMEMORY_DOCS_RUNTIME_ROOT:-/apps/orgmemory-runtime/docs}"
smoke_script="${ORGMEMORY_DOCS_SMOKE_SCRIPT:-$repo_root/infrastructure/deployment/scripts/smoke-docs.sh}"
lock_file="$runtime_root/deploy.lock"
release_stamp="$(date -u +%Y%m%dT%H%M%SZ)"
release_directory="$runtime_root/releases/$release_stamp"
previous_environment="$release_directory/previous.env"
candidate_environment="$release_directory/candidate.env"
current_commit_file="$runtime_root/current-commit"
current_environment_file="$runtime_root/current.env"
previous_image_file="$runtime_root/previous-image"
had_previous_release=false

install -d -m 0700 "$runtime_root" "$runtime_root/releases" "$release_directory"

exec 9>"$lock_file"
if ! flock -n 9; then
  printf 'Another OrgMemory docs deployment is already running.\n' >&2
  exit 75
fi

if [[ ! -f "$environment_file" ]]; then
  printf 'Missing docs environment file: %s\n' "$environment_file" >&2
  exit 1
fi

if [[ "$(stat -c '%a' "$environment_file")" != "600" ]]; then
  printf 'Docs environment file must have mode 0600: %s\n' \
    "$environment_file" >&2
  exit 1
fi

if [[ -s "$current_commit_file" && -s "$current_environment_file" ]]; then
  had_previous_release=true
fi

install -m 0600 "$environment_file" "$previous_environment"

compose=(
  docker compose
  --file "$compose_file"
  --env-file "$environment_file"
)

read_environment_value() {
  local path="$1"
  local key="$2"

  python3 - "$path" "$key" <<'PY'
import sys

path, wanted = sys.argv[1:]
with open(path, encoding="utf-8") as stream:
    for raw_line in stream:
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        if key.strip() == wanted:
            print(value.strip().strip("\"'"))
            break
PY
}

replace_image_reference() {
  local temporary_file
  temporary_file="$(mktemp)"

  awk -v image="ghcr.io/kl3init/orgmemory-docs:sha-$commit_sha" '
    BEGIN { replaced = 0 }
    /^ORGMEMORY_DOCS_IMAGE=/ {
      print "ORGMEMORY_DOCS_IMAGE=" image
      replaced = 1
      next
    }
    { print }
    END {
      if (!replaced) {
        print "ORGMEMORY_DOCS_IMAGE=" image
      }
    }
  ' "$environment_file" > "$temporary_file"

  install -m 0600 "$temporary_file" "$environment_file"
  rm -f "$temporary_file"
}

run_smoke() {
  local public_url

  ORGMEMORY_REPO_ROOT="$repo_root" \
  ORGMEMORY_DOCS_COMPOSE_FILE="$compose_file" \
  ORGMEMORY_DOCS_ENV_FILE="$environment_file" \
    "$smoke_script"

  if [[ "${ORGMEMORY_DOCS_REQUIRE_PUBLIC_SMOKE:-false}" == "true" ]]; then
    public_url="${ORGMEMORY_DOCS_PUBLIC_URL:-$(read_environment_value "$environment_file" ORGMEMORY_DOCS_PUBLIC_URL)}"
    python3 \
      "$repo_root/infrastructure/deployment/scripts/verify-docs-publication.py" \
      "$public_url"
  fi
}

rollback() {
  local exit_code="$?"
  trap - ERR

  printf 'Docs deployment failed; restoring the previous image only.\n' >&2
  if [[ "$had_previous_release" == "true" ]]; then
    install -m 0600 "$current_environment_file" "$environment_file"
    "${compose[@]}" pull orgmemory-docs
    "${compose[@]}" up -d --wait --wait-timeout 60 orgmemory-docs
    run_smoke
  else
    install -m 0600 "$previous_environment" "$environment_file"
    "${compose[@]}" down
  fi
  exit "$exit_code"
}

trap rollback ERR

if [[ "$had_previous_release" == "true" ]]; then
  previous_image="$(
    read_environment_value "$current_environment_file" ORGMEMORY_DOCS_IMAGE
  )"
else
  previous_image="$(read_environment_value "$environment_file" ORGMEMORY_DOCS_IMAGE)"
fi
replace_image_reference
install -m 0600 "$environment_file" "$candidate_environment"

"${compose[@]}" config --quiet
"${compose[@]}" pull orgmemory-docs
"${compose[@]}" up -d --wait --wait-timeout 60 orgmemory-docs
run_smoke

if [[ -n "$previous_image" ]]; then
  printf '%s\n' "$previous_image" > "$previous_image_file"
  chmod 0600 "$previous_image_file"
fi
install -m 0600 "$environment_file" "$current_environment_file"
printf '%s\n' "$commit_sha" > "$current_commit_file"
chmod 0600 "$current_commit_file"

find "$runtime_root/releases" \
  -mindepth 1 \
  -maxdepth 1 \
  -type d \
  -printf '%T@ %p\0' \
  | sort -zrn \
  | tail -z -n +6 \
  | cut -z -d ' ' -f 2- \
  | xargs -0r rm -rf --

trap - ERR
printf 'Deployed OrgMemory docs commit %s.\n' "$commit_sha"
