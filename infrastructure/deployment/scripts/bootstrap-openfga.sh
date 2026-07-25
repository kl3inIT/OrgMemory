#!/usr/bin/env bash
set -Eeuo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
compose_file="$repo_root/infrastructure/deployment/compose.production.yaml"
environment_file="${ORGMEMORY_ENV_FILE:-$repo_root/.env.production}"

if [[ ! -f "$environment_file" ]]; then
  printf 'Missing production environment file: %s\n' "$environment_file" >&2
  exit 1
fi

if [[ "$(stat -c '%a' "$environment_file")" != "600" ]]; then
  printf 'Production environment file must have mode 0600: %s\n' \
    "$environment_file" >&2
  exit 1
fi

read_environment_value() {
  local key="$1"

  python3 - "$environment_file" "$key" <<'PY'
import sys

path, wanted = sys.argv[1:]
with open(path, encoding="utf-8") as stream:
    for raw_line in stream:
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        if key.strip() != wanted:
            continue
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
            value = value[1:-1]
        print(value)
        break
PY
}

openfga_store_id="$(read_environment_value ORGMEMORY_OPENFGA_STORE_ID)"
openfga_model_id="$(read_environment_value ORGMEMORY_OPENFGA_AUTHORIZATION_MODEL_ID)"

if [[ -n "$openfga_store_id" || -n "$openfga_model_id" ]]; then
  printf 'OpenFGA identifiers already exist; refusing to create a second store.\n' >&2
  exit 1
fi

compose=(
  docker compose
  --file "$compose_file"
  --env-file "$environment_file"
)

"${compose[@]}" config --quiet
"${compose[@]}" up -d openfga
"${compose[@]}" run --rm openfga-ready

bootstrap_json="$("${compose[@]}" --profile ops run --rm --no-deps openfga-bootstrap)"

readarray -t identifiers < <(
  BOOTSTRAP_JSON="$bootstrap_json" python3 - <<'PY'
import json
import os

payload = json.loads(os.environ["BOOTSTRAP_JSON"])
store_id = payload["store"]["id"]
model = payload.get("model") or payload.get("authorization_model") or {}
model_id = model.get("authorization_model_id") or model.get("id")
if not store_id or not model_id:
    raise SystemExit("OpenFGA CLI response did not contain both identifiers")
print(store_id)
print(model_id)
PY
)

store_id="${identifiers[0]}"
model_id="${identifiers[1]}"

update_environment_key() {
  local key="$1"
  local value="$2"
  local temporary_file
  temporary_file="$(mktemp)"

  awk -v key="$key" -v value="$value" '
    BEGIN { found = 0 }
    $0 ~ "^" key "=" {
      print key "=" value
      found = 1
      next
    }
    { print }
    END {
      if (!found) {
        print key "=" value
      }
    }
  ' "$environment_file" > "$temporary_file"

  install -m 0600 "$temporary_file" "$environment_file"
  rm -f "$temporary_file"
}

update_environment_key ORGMEMORY_OPENFGA_STORE_ID "$store_id"
update_environment_key ORGMEMORY_OPENFGA_AUTHORIZATION_MODEL_ID "$model_id"

printf 'OpenFGA store and model created. Identifiers were saved to %s.\n' \
  "$environment_file"
