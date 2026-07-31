#!/usr/bin/env bash
set -Eeuo pipefail

repo_root="${ORGMEMORY_REPO_ROOT:-/apps/orgmemory}"
compose_file="${ORGMEMORY_COMPOSE_FILE:-$repo_root/infrastructure/deployment/compose.production.yaml}"
environment_file="${ORGMEMORY_ENV_FILE:-$repo_root/.env.production}"

if [[ ! -f "$environment_file" || "$(stat -c '%a' "$environment_file")" != "600" ]]; then
  printf 'The server environment file is missing or is not mode 0600.\n' >&2
  exit 1
fi

python3 - "$environment_file" "$compose_file" <<'PY'
import json
import subprocess
import sys

environment_path, compose_path = sys.argv[1:]
values = {}
with open(environment_path, encoding="utf-8") as stream:
    for raw_line in stream:
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
            value = value[1:-1]
        values[key.strip()] = value

allowed = (
    "ORGMEMORY_DB_NAME",
    "ORGMEMORY_DB_USER",
    "ORGMEMORY_DB_PASSWORD",
    "ORGMEMORY_OIDC_CLIENT_SECRET",
    "ORGMEMORY_OPENFGA_STORE_ID",
    "ORGMEMORY_OPENFGA_AUTHORIZATION_MODEL_ID",
    "ORGMEMORY_OBJECT_STORAGE_ACCESS_KEY",
    "ORGMEMORY_OBJECT_STORAGE_SECRET_KEY",
    "ORGMEMORY_OBJECT_STORAGE_BUCKET",
    "ORGMEMORY_OPENAI_BASE_URL",
    "ORGMEMORY_OPENAI_API_KEY",
    "ORGMEMORY_OPENAI_MODEL",
    "ORGMEMORY_KEYWORD_MODEL",
    "ORGMEMORY_GRAPH_EXTRACTION_MODEL",
    "ORGMEMORY_OPENAI_EMBEDDING_MODEL",
    "ORGMEMORY_OPENAI_EMBEDDING_DIMENSIONS",
    "ORGMEMORY_SECRETS_KEY",
    "ORGMEMORY_SECRETS_SALT",
    "ORGMEMORY_SECRETS_KEY_VERSION",
    "ORGMEMORY_SCIM_VERIFIER_KEY",
    "ORGMEMORY_SCIM_VERIFIER_KEY_VERSION",
    "ORGMEMORY_SCIM_PREVIOUS_VERIFIER_KEYS",
    "ORGMEMORY_MCP_OIDC_CLIENT_ID",
    "ORGMEMORY_MCP_OIDC_CLIENT_SECRET",
)

required = (
    "ORGMEMORY_DB_PASSWORD",
    "ORGMEMORY_OIDC_CLIENT_SECRET",
    "ORGMEMORY_OPENFGA_STORE_ID",
    "ORGMEMORY_OPENFGA_AUTHORIZATION_MODEL_ID",
    "ORGMEMORY_OBJECT_STORAGE_ACCESS_KEY",
    "ORGMEMORY_OBJECT_STORAGE_SECRET_KEY",
    "ORGMEMORY_SECRETS_KEY",
    "ORGMEMORY_SECRETS_SALT",
)
missing = [key for key in required if not values.get(key)]
if missing:
    raise SystemExit("Required shared-development values are missing: " + ", ".join(missing))


def output(*command: str) -> str:
    return subprocess.check_output(command, text=True).strip()


def service_container(service: str) -> str:
    container = output(
        "docker", "compose", "--file", compose_path,
        "--env-file", environment_path, "ps", "-q", service,
    )
    if not container:
        raise SystemExit(f"Service {service!r} is not running")
    return container


def container_target(container: str, port: int) -> str:
    networks = json.loads(output("docker", "inspect", container))[0]["NetworkSettings"]["Networks"]
    addresses = [item.get("IPAddress") for item in networks.values() if item.get("IPAddress")]
    if not addresses:
        raise SystemExit(f"Container {container!r} has no reachable bridge address")
    return f"{addresses[0]}:{port}"


postgres_container = values.get("ORGMEMORY_SHARED_POSTGRES_CONTAINER", "zeromail-postgres")
payload = {key: values.get(key, "") for key in allowed}
payload["ORGMEMORY_DB_NAME"] = payload["ORGMEMORY_DB_NAME"] or "orgmemory"
payload["ORGMEMORY_DB_USER"] = payload["ORGMEMORY_DB_USER"] or "orgmemory"
payload["ORGMEMORY_OBJECT_STORAGE_BUCKET"] = payload["ORGMEMORY_OBJECT_STORAGE_BUCKET"] or "orgmemory-evidence"
payload["ORGMEMORY_MCP_OIDC_CLIENT_ID"] = payload["ORGMEMORY_MCP_OIDC_CLIENT_ID"] or "orgmemory-mcp"
payload["postgresTarget"] = container_target(postgres_container, 5432)
payload["openfgaTarget"] = container_target(service_container("openfga"), 8080)
payload["minioTarget"] = container_target(service_container("minio"), 9000)
print(json.dumps(payload, separators=(",", ":")))
PY
