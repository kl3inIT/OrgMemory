#!/usr/bin/env bash
set -Eeuo pipefail

runtime_root="${ORGMEMORY_TEAM_DEV_RUNTIME_ROOT:-/apps/orgmemory-runtime/team-development}"
compose_file="${ORGMEMORY_COMPOSE_FILE:-/apps/orgmemory/infrastructure/deployment/compose.production.yaml}"
environment_file="${ORGMEMORY_ENV_FILE:-/apps/orgmemory/.env.production}"
lease_file="$runtime_root/active.lease"
lock_file="$runtime_root/coordination.lock"
default_ttl="${ORGMEMORY_TEAM_DEV_LEASE_TTL_SECONDS:-300}"

usage() {
  printf 'Usage: %s acquire <worker|maintenance> <session> <owner> <host> <commit> [ttl-seconds]\n' "$0" >&2
  printf '       %s heartbeat <worker|maintenance> <session> [ttl-seconds]\n' "$0" >&2
  printf '       %s release <worker|maintenance> <session>\n' "$0" >&2
  printf '       %s status|reap\n' "$0" >&2
  exit 64
}

valid_token() {
  [[ "$1" =~ ^[A-Za-z0-9._@:-]+$ ]]
}

compose() {
  docker compose --file "$compose_file" --env-file "$environment_file" "$@"
}

read_lease() {
  IFS='|' read -r lease_mode lease_session lease_owner lease_host lease_commit lease_expires < "$lease_file"
}

restore_worker() {
  compose up -d worker >/dev/null
  printf 'Restored the canonical ZM worker.\n'
}

reap_expired() {
  [[ -s "$lease_file" ]] || return 0
  read_lease
  if (( lease_expires > $(date +%s) )); then
    return 0
  fi
  rm -f -- "$lease_file"
  if [[ "$lease_mode" == "worker" ]]; then
    restore_worker
  fi
  printf 'Reaped expired %s lease %s.\n' "$lease_mode" "$lease_session"
}

write_lease() {
  local mode="$1" session="$2" owner="$3" host="$4" commit="$5" ttl="$6"
  local temporary_file
  temporary_file="$(mktemp "$runtime_root/lease.XXXXXX")"
  printf '%s|%s|%s|%s|%s|%s\n' \
    "$mode" "$session" "$owner" "$host" "$commit" "$(( $(date +%s) + ttl ))" \
    > "$temporary_file"
  if [[ "${ORGMEMORY_TEAM_DEV_SKIP_PERMISSION_CHECK:-false}" != "true" ]]; then
    chmod 0600 "$temporary_file"
  fi
  mv -f -- "$temporary_file" "$lease_file"
}

watchdog() {
  local session="$1"
  while true; do
    sleep 15
    exec 9>"$lock_file"
    flock 9
    if [[ ! -s "$lease_file" ]]; then
      exit 0
    fi
    read_lease
    if [[ "$lease_session" != "$session" ]]; then
      exit 0
    fi
    if (( lease_expires <= $(date +%s) )); then
      reap_expired
      exit 0
    fi
    flock -u 9
    exec 9>&-
  done
}

if [[ "${ORGMEMORY_TEAM_DEV_SKIP_PERMISSION_CHECK:-false}" == "true" ]]; then
  mkdir -p "$runtime_root"
else
  install -d -m 0700 "$runtime_root"
fi
command="${1:-}"
[[ -n "$command" ]] || usage
shift

if [[ "$command" == "watchdog" ]]; then
  [[ "$#" -eq 1 ]] || usage
  watchdog "$1"
  exit 0
fi

exec 9>"$lock_file"
flock 9
reap_expired

case "$command" in
  acquire)
    [[ "$#" -ge 5 && "$#" -le 6 ]] || usage
    mode="$1"; session="$2"; owner="$3"; host="$4"; commit="$5"; ttl="${6:-$default_ttl}"
    [[ "$mode" == "worker" || "$mode" == "maintenance" ]] || usage
    for value in "$session" "$owner" "$host" "$commit"; do
      valid_token "$value" || { printf 'Lease values may contain only safe identifier characters.\n' >&2; exit 64; }
    done
    if [[ ! "$ttl" =~ ^[0-9]+$ ]] || (( ttl < 30 || ttl > 3600 )); then
      printf 'TTL must be between 30 and 3600 seconds.\n' >&2
      exit 64
    fi
    if [[ -s "$lease_file" ]]; then
      read_lease
      if [[ "$lease_mode" == "$mode" && "$lease_session" == "$session" ]]; then
        write_lease "$mode" "$session" "$owner" "$host" "$commit" "$ttl"
        printf 'Renewed %s lease %s.\n' "$mode" "$session"
        exit 0
      fi
      printf 'Shared ZM mutation is already owned by %s@%s (%s, session %s).\n' \
        "$lease_owner" "$lease_host" "$lease_mode" "$lease_session" >&2
      exit 75
    fi
    if [[ "$mode" == "worker" ]]; then
      compose stop worker >/dev/null
    fi
    write_lease "$mode" "$session" "$owner" "$host" "$commit" "$ttl"
    if [[ "${ORGMEMORY_TEAM_DEV_DISABLE_WATCHDOG:-false}" != "true" ]]; then
      nohup "$0" watchdog "$session" >/dev/null 2>&1 &
    fi
    read_lease
    printf 'Acquired %s lease %s until %s.\n' "$mode" "$session" "$(date -d "@$lease_expires" -u +%FT%TZ 2>/dev/null || true)"
    ;;
  heartbeat)
    [[ "$#" -ge 2 && "$#" -le 3 ]] || usage
    mode="$1"; session="$2"; ttl="${3:-$default_ttl}"
    [[ -s "$lease_file" ]] || { printf 'No active lease.\n' >&2; exit 69; }
    read_lease
    [[ "$lease_mode" == "$mode" && "$lease_session" == "$session" ]] || {
      printf 'Lease ownership does not match.\n' >&2
      exit 77
    }
    write_lease "$lease_mode" "$lease_session" "$lease_owner" "$lease_host" "$lease_commit" "$ttl"
    ;;
  release)
    [[ "$#" -eq 2 ]] || usage
    mode="$1"; session="$2"
    if [[ ! -s "$lease_file" ]]; then
      exit 0
    fi
    read_lease
    [[ "$lease_mode" == "$mode" && "$lease_session" == "$session" ]] || {
      printf 'Lease ownership does not match.\n' >&2
      exit 77
    }
    rm -f -- "$lease_file"
    if [[ "$lease_mode" == "worker" ]]; then
      restore_worker
    fi
    printf 'Released %s lease %s.\n' "$mode" "$session"
    ;;
  status)
    if [[ ! -s "$lease_file" ]]; then
      printf 'No active shared ZM mutation lease.\n'
      exit 0
    fi
    read_lease
    printf '%s|%s|%s|%s|%s|%s\n' \
      "$lease_mode" "$lease_session" "$lease_owner" "$lease_host" "$lease_commit" "$lease_expires"
    ;;
  reap)
    ;;
  *)
    usage
    ;;
esac
