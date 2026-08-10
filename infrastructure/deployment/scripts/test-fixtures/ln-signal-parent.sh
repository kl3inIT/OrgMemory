#!/usr/bin/env bash
set -Eeuo pipefail

: "${ORGMEMORY_REAL_LN:?}"
: "${ORGMEMORY_OWNERSHIP_SIGNAL:?}"
"$ORGMEMORY_REAL_LN" "$@"
target="${!#}"
if [[ "$target" == */ownership ]]; then
  kill -s "$ORGMEMORY_OWNERSHIP_SIGNAL" "$PPID"
fi
