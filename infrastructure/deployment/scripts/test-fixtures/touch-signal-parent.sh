#!/usr/bin/env bash
set -Eeuo pipefail

: "${ORGMEMORY_REAL_TOUCH:?}"
: "${ORGMEMORY_MARKER_SIGNAL:?}"
"$ORGMEMORY_REAL_TOUCH" "$@"
target="${!#}"
if [[ "$target" == */controller-started ]]; then
  kill -s "$ORGMEMORY_MARKER_SIGNAL" "$PPID"
fi
