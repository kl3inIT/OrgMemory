#!/usr/bin/env bash
set -Eeuo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
run_id="${RANDOM}-$$"
container="orgmemory-keycloak-mcp-test-${run_id}"
image="orgmemory-keycloak-mcp-test:${run_id}"
tmp_root="${TMPDIR:-/tmp}"
tmp_dir="$(mktemp -d "${tmp_root%/}/orgmemory-keycloak-mcp-test.XXXXXX")"
container_kcadm_config="/tmp/orgmemory-mcp-test-${run_id}.config"

cleanup() {
  if [[ "$(docker inspect "$container" --format '{{.Name}}' 2>/dev/null || true)" == "/$container" ]]; then
    docker rm --force "$container" >/dev/null
  fi
  docker image rm "$image" >/dev/null 2>&1 || true
  case "$tmp_dir" in
    "${tmp_root%/}"/orgmemory-keycloak-mcp-test.*)
      rm -rf -- "$tmp_dir"
      ;;
    *)
      printf 'Refusing to remove unexpected temporary path: %s\n' "$tmp_dir" >&2
      ;;
  esac
}
trap cleanup EXIT

docker build \
  --tag "$image" \
  --file "$repo_root/infrastructure/keycloak/Dockerfile" \
  "$repo_root" >/dev/null

docker run --detach \
  --name "$container" \
  --publish 127.0.0.1::8080 \
  --env KC_DB=dev-file \
  --env KC_BOOTSTRAP_ADMIN_USERNAME=admin \
  --env KC_BOOTSTRAP_ADMIN_PASSWORD=verification-only \
  --env ORGMEMORY_OIDC_CLIENT_SECRET=verification-web-only \
  --env ORGMEMORY_MCP_OIDC_CLIENT_SECRET=verification-mcp-only \
  --env ORGMEMORY_MCP_RESOURCE_URI=https://om.kl3in.tech/mcp \
  "$image" \
  start-dev --import-realm >/dev/null

port="$(
  docker port "$container" 8080/tcp \
    | tail -n 1 \
    | sed -E 's/.*:([0-9]+)$/\1/'
)"
base_url="http://127.0.0.1:${port}"
metadata_url="$base_url/realms/orgmemory/.well-known/oauth-authorization-server"

ready=false
for _ in {1..90}; do
  if curl --fail --silent --show-error \
    --connect-timeout 2 \
    --max-time 3 \
    "$metadata_url" >"$tmp_dir/metadata.json" 2>/dev/null; then
    ready=true
    break
  fi
  sleep 1
done
if [[ "$ready" != "true" ]]; then
  docker logs "$container" >&2
  exit 1
fi

ORGMEMORY_KEYCLOAK_CONTAINER="$container" \
ORGMEMORY_KEYCLOAK_REALM=orgmemory \
  "$repo_root/infrastructure/deployment/scripts/configure-keycloak-mcp.sh"
MSYS_NO_PATHCONV=1 docker exec "$container" sh -ec \
  '/opt/keycloak/bin/kcadm.sh config credentials \
    --config "$1" \
    --server http://127.0.0.1:8080 \
    --realm master \
    --user "$KC_BOOTSTRAP_ADMIN_USERNAME" \
    --password "$KC_BOOTSTRAP_ADMIN_PASSWORD" >/dev/null' \
  sh "$container_kcadm_config"
MSYS_NO_PATHCONV=1 docker exec "$container" \
  /opt/keycloak/bin/kcadm.sh get client-scopes \
  -r orgmemory \
  -q name=basic \
  --config "$container_kcadm_config" >"$tmp_dir/basic-scope.json"
basic_scope_id="$(
  python3 -c \
    'import json,sys; print(json.load(open(sys.argv[1], encoding="utf-8"))[0]["id"])' \
    "$tmp_dir/basic-scope.json"
)"
MSYS_NO_PATHCONV=1 docker exec "$container" \
  /opt/keycloak/bin/kcadm.sh update "client-scopes/$basic_scope_id" \
  -r orgmemory \
  -s description=drifted \
  --config "$container_kcadm_config" >/dev/null
ORGMEMORY_KEYCLOAK_CONTAINER="$container" \
ORGMEMORY_KEYCLOAK_REALM=orgmemory \
  "$repo_root/infrastructure/deployment/scripts/configure-keycloak-mcp.sh"
MSYS_NO_PATHCONV=1 docker exec "$container" \
  /opt/keycloak/bin/kcadm.sh get "client-scopes/$basic_scope_id" \
  -r orgmemory \
  --config "$container_kcadm_config" >"$tmp_dir/basic-scope.json"
python3 - "$tmp_dir/basic-scope.json" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as stream:
    scope = json.load(stream)
expected_description = (
    "OpenID Connect scope for basic subject and authentication-time claims"
)
assert scope["description"] == expected_description, scope
PY

curl --fail --silent --show-error "$metadata_url" >"$tmp_dir/metadata.json"
python3 - "$tmp_dir/metadata.json" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as stream:
    metadata = json.load(stream)
assert metadata["client_id_metadata_document_supported"] is True
assert metadata["registration_endpoint"].endswith(
    "/clients-registrations/openid-connect"
)
PY

registration_url="$base_url/realms/orgmemory/clients-registrations/openid-connect"
cat >"$tmp_dir/good-client.json" <<'JSON'
{
  "client_name": "OrgMemory deployment contract",
  "redirect_uris": ["http://127.0.0.1:43821/oauth/callback"],
  "grant_types": ["authorization_code", "refresh_token"],
  "response_types": ["code"],
  "token_endpoint_auth_method": "none",
  "scope": "assets:read"
}
JSON
curl --fail --silent --show-error \
  --request POST \
  --header 'Content-Type: application/json' \
  --data-binary "@$tmp_dir/good-client.json" \
  "$registration_url" >"$tmp_dir/registration.json"

client_id="$(
  python3 -c \
    'import json,sys; print(json.load(open(sys.argv[1], encoding="utf-8"))["client_id"])' \
    "$tmp_dir/registration.json"
)"
MSYS_NO_PATHCONV=1 docker exec "$container" \
  /opt/keycloak/bin/kcadm.sh get clients \
  -r orgmemory \
  -q "clientId=$client_id" \
  --config "$container_kcadm_config" >"$tmp_dir/client.json"
python3 - "$tmp_dir/client.json" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as stream:
    clients = json.load(stream)
assert len(clients) == 1
client = clients[0]
assert client["publicClient"] is True
assert client["consentRequired"] is True
assert client["fullScopeAllowed"] is False
assert client["directAccessGrantsEnabled"] is False
assert client["serviceAccountsEnabled"] is False
assert client["attributes"]["pkce.code.challenge.method"] == "S256"
PY

cat >"$tmp_dir/bad-redirect.json" <<'JSON'
{
  "client_name": "Rejected redirect",
  "redirect_uris": ["https://evil.example/callback"],
  "grant_types": ["authorization_code"],
  "response_types": ["code"],
  "token_endpoint_auth_method": "none",
  "scope": "assets:read"
}
JSON
bad_redirect_status="$(
  curl --silent --show-error \
    --output /dev/null \
    --write-out '%{http_code}' \
    --request POST \
    --header 'Content-Type: application/json' \
    --data-binary "@$tmp_dir/bad-redirect.json" \
    "$registration_url"
)"
[[ "$bad_redirect_status" == "403" ]]

cat >"$tmp_dir/bad-scope.json" <<'JSON'
{
  "client_name": "Rejected scope",
  "redirect_uris": ["http://127.0.0.1:43822/oauth/callback"],
  "grant_types": ["authorization_code"],
  "response_types": ["code"],
  "token_endpoint_auth_method": "none",
  "scope": "assets:write"
}
JSON
bad_scope_status="$(
  curl --silent --show-error \
    --output /dev/null \
    --write-out '%{http_code}' \
    --request POST \
    --header 'Content-Type: application/json' \
    --data-binary "@$tmp_dir/bad-scope.json" \
    "$registration_url"
)"
[[ "$bad_scope_status" == "403" ]]

registration_uri="$(
  python3 -c \
    'import json,sys; print(json.load(open(sys.argv[1], encoding="utf-8"))["registration_client_uri"])' \
    "$tmp_dir/registration.json"
)"
registration_token="$(
  python3 -c \
    'import json,sys; print(json.load(open(sys.argv[1], encoding="utf-8"))["registration_access_token"])' \
    "$tmp_dir/registration.json"
)"
delete_status="$(
  curl --silent --show-error \
    --output /dev/null \
    --write-out '%{http_code}' \
    --request DELETE \
    --header "Authorization: Bearer $registration_token" \
    "$registration_uri"
)"
[[ "$delete_status" == "204" ]]

printf 'Keycloak MCP onboarding contract passed.\n'
