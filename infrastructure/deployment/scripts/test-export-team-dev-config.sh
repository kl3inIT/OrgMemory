#!/usr/bin/env bash
set -Eeuo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
script="$repo_root/infrastructure/deployment/scripts/export-team-dev-config.sh"
temporary_root="$(mktemp -d)"
trap 'rm -rf -- "$temporary_root"' EXIT
mkdir -p "$temporary_root/bin"

environment_file="$temporary_root/production.env"
cat > "$environment_file" <<'EOF'
ORGMEMORY_DB_NAME=orgmemory
ORGMEMORY_DB_USER=orgmemory
ORGMEMORY_DB_PASSWORD=db-secret
ORGMEMORY_OIDC_CLIENT_SECRET=oidc-secret
ORGMEMORY_OPENFGA_STORE_ID=store-id
ORGMEMORY_OPENFGA_AUTHORIZATION_MODEL_ID=model-id
ORGMEMORY_OBJECT_STORAGE_ACCESS_KEY=minio-key
ORGMEMORY_OBJECT_STORAGE_SECRET_KEY=minio-secret
ORGMEMORY_SECRETS_KEY=encryption-key
ORGMEMORY_SECRETS_SALT=encryption-salt
ORGMEMORY_OPENAI_REASONING_EFFORT_SUPPORTED=true
ORGMEMORY_KEYWORD_OPENAI_REASONING_EFFORT=none
ORGMEMORY_GRAPH_EXTRACTION_OPENAI_REASONING_EFFORT=
EOF
chmod 0600 "$environment_file"
printf 'services: {}\n' > "$temporary_root/compose.yaml"

cat > "$temporary_root/bin/docker" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
if [[ "$*" == *"ps -q openfga" ]]; then
  printf 'openfga-container\n'
elif [[ "$*" == *"ps -q minio" ]]; then
  printf 'minio-container\n'
elif [[ "$1" == "inspect" ]]; then
  case "$2" in
    zeromail-postgres) ip=172.20.0.2 ;;
    openfga-container) ip=172.20.0.3 ;;
    minio-container) ip=172.20.0.4 ;;
    *) exit 1 ;;
  esac
  printf '[{"NetworkSettings":{"Networks":{"shared":{"IPAddress":"%s"}}}}]\n' "$ip"
else
  exit 1
fi
SH
chmod +x "$temporary_root/bin/docker"

payload="$(
  PATH="$temporary_root/bin:$PATH" \
  ORGMEMORY_ENV_FILE="$environment_file" \
  ORGMEMORY_COMPOSE_FILE="$temporary_root/compose.yaml" \
    "$script"
)"

python3 - "$payload" <<'PY'
import json
import sys

payload = json.loads(sys.argv[1])
assert payload["postgresTarget"] == "172.20.0.2:5432"
assert payload["openfgaTarget"] == "172.20.0.3:8080"
assert payload["minioTarget"] == "172.20.0.4:9000"
assert payload["ORGMEMORY_DB_PASSWORD"] == "db-secret"
assert payload["ORGMEMORY_OPENAI_REASONING_EFFORT_SUPPORTED"] == "true"
assert payload["ORGMEMORY_KEYWORD_OPENAI_REASONING_EFFORT"] == "none"
assert payload["ORGMEMORY_GRAPH_EXTRACTION_OPENAI_REASONING_EFFORT"] == ""
assert "SHARED_POSTGRES_ADMIN_PASSWORD" not in payload
PY

printf 'Shared ZM configuration export contract passed.\n'
