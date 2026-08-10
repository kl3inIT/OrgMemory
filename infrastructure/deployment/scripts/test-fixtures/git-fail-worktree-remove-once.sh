#!/usr/bin/env bash
set -Eeuo pipefail

: "${ORGMEMORY_REAL_GIT:?}"
: "${ORGMEMORY_GIT_FAILURE_MARKER:?}"

if [[ "$*" == *"worktree remove --force"* && ! -e "$ORGMEMORY_GIT_FAILURE_MARKER" ]]; then
  : >"$ORGMEMORY_GIT_FAILURE_MARKER"
  exit 73
fi
exec "$ORGMEMORY_REAL_GIT" "$@"
