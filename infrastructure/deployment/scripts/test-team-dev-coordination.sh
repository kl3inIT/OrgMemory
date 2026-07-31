#!/usr/bin/env bash
set -Eeuo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
script="$repo_root/infrastructure/deployment/scripts/team-dev-coordination.sh"
temporary_root="$(mktemp -d)"
trap 'rm -rf -- "$temporary_root"' EXIT

mkdir -p "$temporary_root/bin"
docker_log="$temporary_root/docker.log"
cat > "$temporary_root/bin/docker" <<'SH'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$ORGMEMORY_TEST_DOCKER_LOG"
SH
chmod +x "$temporary_root/bin/docker"

run_coordination() {
  PATH="$temporary_root/bin:$PATH" \
  ORGMEMORY_TEST_DOCKER_LOG="$docker_log" \
  ORGMEMORY_TEAM_DEV_RUNTIME_ROOT="$temporary_root/runtime" \
  ORGMEMORY_TEAM_DEV_DISABLE_WATCHDOG=true \
  ORGMEMORY_TEAM_DEV_SKIP_PERMISSION_CHECK=true \
    "$script" "$@"
}

run_coordination acquire worker session-a alice laptop-a abc123 30
grep -Fq 'compose --file /apps/orgmemory/infrastructure/deployment/compose.production.yaml --env-file /apps/orgmemory/.env.production stop worker' "$docker_log"

if run_coordination acquire maintenance session-b bob laptop-b def456 30; then
  printf 'Concurrent exceptional lease unexpectedly succeeded.\n' >&2
  exit 1
fi

run_coordination heartbeat worker session-a 60
run_coordination release worker session-a
grep -Fq 'compose --file /apps/orgmemory/infrastructure/deployment/compose.production.yaml --env-file /apps/orgmemory/.env.production up -d worker' "$docker_log"

run_coordination acquire maintenance session-b bob laptop-b def456 30
status="$(run_coordination status)"
[[ "$status" == maintenance\|session-b\|bob\|laptop-b\|def456\|* ]]
run_coordination release maintenance session-b

printf 'Shared ZM coordination contracts passed.\n'
