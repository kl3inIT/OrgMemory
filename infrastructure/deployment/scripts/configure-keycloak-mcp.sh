#!/usr/bin/env bash
set -Eeuo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
compose_file="$repo_root/infrastructure/deployment/compose.production.yaml"
environment_file="${ORGMEMORY_ENV_FILE:-$repo_root/.env.production}"
realm="${ORGMEMORY_KEYCLOAK_REALM:-orgmemory}"
keycloak_container="${ORGMEMORY_KEYCLOAK_CONTAINER:-}"
profiles_source="$repo_root/infrastructure/keycloak/mcp-client-profiles.json"
policies_source="$repo_root/infrastructure/keycloak/mcp-client-policies.json"
registration_policy_source="$repo_root/infrastructure/keycloak/mcp-dcr-registration-policy.json"
basic_scope_source="$repo_root/infrastructure/keycloak/mcp-basic-client-scope.json"
kcadm_config="/tmp/orgmemory-mcp-kcadm.config"
tmp_root="${TMPDIR:-/tmp}"
tmp_dir="$(mktemp -d "${tmp_root%/}/orgmemory-keycloak-mcp.XXXXXX")"

compose=(
  docker compose
  --file "$compose_file"
  --env-file "$environment_file"
)

keycloak_exec() {
  if [[ -n "$keycloak_container" ]]; then
    MSYS_NO_PATHCONV=1 docker exec -i "$keycloak_container" "$@"
  else
    "${compose[@]}" exec -T keycloak "$@"
  fi
}

kcadm() {
  keycloak_exec \
    /opt/keycloak/bin/kcadm.sh "$@" --config "$kcadm_config"
}

cleanup() {
  keycloak_exec rm -f "$kcadm_config" >/dev/null 2>&1 || true
  case "$tmp_dir" in
    "${tmp_root%/}"/orgmemory-keycloak-mcp.*)
      rm -rf -- "$tmp_dir"
      ;;
    *)
      printf 'Refusing to remove unexpected temporary path: %s\n' "$tmp_dir" >&2
      ;;
  esac
}
trap cleanup EXIT

# The quoted variables are intentionally expanded inside the Keycloak container.
# shellcheck disable=SC2016
keycloak_exec bash -ec \
  '/opt/keycloak/bin/kcadm.sh config credentials \
    --config /tmp/orgmemory-mcp-kcadm.config \
    --server http://127.0.0.1:8080 \
    --realm master \
    --user "$KC_BOOTSTRAP_ADMIN_USERNAME" \
    --password "$KC_BOOTSTRAP_ADMIN_PASSWORD" >/dev/null'

kcadm get "realms/$realm" >/dev/null

client_scopes_csv="$tmp_dir/client-scopes.csv"
kcadm get client-scopes \
  -r "$realm" \
  --fields id,name \
  --format csv \
  --noquotes \
  | tr -d '\r' >"$client_scopes_csv"
if ! awk -F, '$2 == "basic" { found = 1 } END { exit !found }' \
  "$client_scopes_csv"; then
  kcadm create client-scopes -r "$realm" -f - <"$basic_scope_source" >/dev/null
fi

merge_client_policy_document() {
  local endpoint="$1"
  local array_key="$2"
  local desired_source="$3"
  local current_path="$tmp_dir/${array_key}-current.json"
  local merged_path="$tmp_dir/${array_key}-merged.json"

  kcadm get "$endpoint" -r "$realm" >"$current_path"
  python3 - "$current_path" "$desired_source" "$array_key" >"$merged_path" <<'PY'
import json
import sys

current_path, desired_path, array_key = sys.argv[1:]
with open(current_path, encoding="utf-8") as stream:
    current = json.load(stream)
with open(desired_path, encoding="utf-8") as stream:
    desired = json.load(stream)

desired_items = desired[array_key]
desired_names = {item["name"] for item in desired_items}
preserved = [
    item for item in current.get(array_key, [])
    if item.get("name") not in desired_names
]
json.dump({array_key: [*preserved, *desired_items]}, sys.stdout)
PY
  kcadm update "$endpoint" -r "$realm" -f - <"$merged_path"
}

merge_client_policy_document \
  client-policies/profiles profiles "$profiles_source"
merge_client_policy_document \
  client-policies/policies policies "$policies_source"

components_csv="$tmp_dir/registration-components.csv"
kcadm get components \
  -r "$realm" \
  -q type=org.keycloak.services.clientregistration.policy.ClientRegistrationPolicy \
  --fields id,providerId,subType \
  --format csv \
  --noquotes \
  | tr -d '\r' >"$components_csv"

mapfile -t registration_providers < <(
  python3 - "$registration_policy_source" <<'PY' | tr -d '\r'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as stream:
    for provider_id in json.load(stream):
        print(provider_id)
PY
)

for provider_id in "${registration_providers[@]}"; do
  component_id="$(
    awk -F, -v provider="$provider_id" \
      '$2 == provider && $3 == "anonymous" { print $1; exit }' \
      "$components_csv"
  )"
  if [[ -z "$component_id" ]]; then
    printf 'Missing anonymous Keycloak registration policy: %s\n' "$provider_id" >&2
    exit 1
  fi

  case "$provider_id" in
    trusted-hosts)
      kcadm update "components/$component_id" -r "$realm" \
        -s 'config."host-sending-registration-request-must-match"=["false"]' \
        -s 'config."trusted-hosts"=["localhost","127.0.0.1","claude.ai","claude.com","vscode.dev"]' \
        -s 'config."client-uris-must-match"=["true"]'
      ;;
    allowed-client-templates)
      kcadm update "components/$component_id" -r "$realm" \
        -s 'config."allow-default-scopes"=["true"]' \
        -s 'config."allowed-client-scopes"=["basic","assets:read"]'
      ;;
    max-clients)
      kcadm update "components/$component_id" -r "$realm" \
        -s 'config."max-clients"=["50"]'
      ;;
    *)
      printf 'Unsupported MCP registration policy: %s\n' "$provider_id" >&2
      exit 1
      ;;
  esac

  component_path="$tmp_dir/component-${provider_id}.json"
  kcadm get "components/$component_id" -r "$realm" >"$component_path"
  python3 \
    - "$component_path" "$registration_policy_source" "$provider_id" <<'PY'
import json
import sys

component_path, policy_path, provider_id = sys.argv[1:]
with open(component_path, encoding="utf-8") as stream:
    actual = json.load(stream)["config"]
with open(policy_path, encoding="utf-8") as stream:
    expected = json.load(stream)[provider_id]

normalize = lambda config: {
    key: sorted(values) for key, values in config.items()
}
if normalize(actual) != normalize(expected):
    raise SystemExit(
        f"Keycloak registration policy verification failed for {provider_id}"
    )
PY
done

profiles_actual="$tmp_dir/profiles-actual.json"
policies_actual="$tmp_dir/policies-actual.json"
kcadm get client-policies/profiles -r "$realm" >"$profiles_actual"
kcadm get client-policies/policies -r "$realm" >"$policies_actual"

python3 \
  - "$profiles_actual" "$profiles_source" profiles \
  "$policies_actual" "$policies_source" policies <<'PY'
import json
import sys

for offset in (1, 4):
    actual_path, expected_path, array_key = sys.argv[offset:offset + 3]
    with open(actual_path, encoding="utf-8") as stream:
        actual = json.load(stream)
    with open(expected_path, encoding="utf-8") as stream:
        expected = json.load(stream)
    actual_by_name = {item["name"]: item for item in actual[array_key]}
    for item in expected[array_key]:
        if actual_by_name.get(item["name"]) != item:
            raise SystemExit(
                f"Keycloak {array_key} verification failed for {item['name']}"
            )
PY

printf 'Keycloak MCP client onboarding policies are configured.\n'
