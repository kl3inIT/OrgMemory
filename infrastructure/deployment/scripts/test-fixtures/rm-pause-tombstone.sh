#!/usr/bin/env bash
set -Eeuo pipefail

: "${ORGMEMORY_REAL_RM:?}"
: "${ORGMEMORY_TOMBSTONE_READY:?}"
: "${ORGMEMORY_TOMBSTONE_RELEASE:?}"
for argument in "$@"; do
  if [[ "$argument" == *.cleanup.* ]]; then
    touch "$ORGMEMORY_TOMBSTONE_READY"
    for _ in {1..200}; do
      [[ -e "$ORGMEMORY_TOMBSTONE_RELEASE" ]] && break
      sleep 0.05
    done
    [[ -e "$ORGMEMORY_TOMBSTONE_RELEASE" ]]
    break
  fi
done
exec "$ORGMEMORY_REAL_RM" "$@"
