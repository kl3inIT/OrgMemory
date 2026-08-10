#!/usr/bin/env bash
set -Eeuo pipefail

: "${ORGMEMORY_REAL_RM:?}"
: "${ORGMEMORY_RM_FAIL_PATTERN:?}"
for argument in "$@"; do
  if [[ "$argument" == *"$ORGMEMORY_RM_FAIL_PATTERN"* ]]; then
    exit 73
  fi
done
exec "$ORGMEMORY_REAL_RM" "$@"
