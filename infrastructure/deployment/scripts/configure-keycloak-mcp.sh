#!/usr/bin/env bash
set -Eeuo pipefail

mode="full"
case "${1:-}" in
  "")
    ;;
  --print-login-theme)
    mode="print-login-theme"
    ;;
  --login-theme-only)
    mode="login-theme-only"
    ;;
  --restore-login-theme)
    mode="restore-login-theme"
    ;;
  *)
    printf 'Usage: %s [--print-login-theme|--login-theme-only|--restore-login-theme]\n' "$0" >&2
    exit 64
    ;;
esac
if [[ "$#" -gt 1 ]]; then
  printf 'Usage: %s [--print-login-theme|--login-theme-only|--restore-login-theme]\n' "$0" >&2
  exit 64
fi

repo_root="${ORGMEMORY_REPO_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)}"
compose_file="$repo_root/infrastructure/deployment/compose.production.yaml"
environment_file="${ORGMEMORY_ENV_FILE:-$repo_root/.env.production}"
realm="${ORGMEMORY_KEYCLOAK_REALM:-orgmemory}"
keycloak_container="${ORGMEMORY_KEYCLOAK_CONTAINER:-}"
profiles_source="$repo_root/infrastructure/keycloak/mcp-client-profiles.json"
policies_source="$repo_root/infrastructure/keycloak/mcp-client-policies.json"
registration_policy_source="$repo_root/infrastructure/keycloak/mcp-dcr-registration-policy.json"
basic_scope_source="$repo_root/infrastructure/keycloak/mcp-basic-client-scope.json"
write_scope_template="$repo_root/infrastructure/keycloak/mcp-assets-write-client-scope.json"
gateway_client_source="$repo_root/infrastructure/keycloak/mcp-gateway-client.json"
team_dev_web_client_source="$repo_root/infrastructure/keycloak/team-dev-web-client.json"
kcadm_config="/tmp/orgmemory-mcp-kcadm.config"
tmp_root="${TMPDIR:-/tmp}"
tmp_dir="$(mktemp -d "${tmp_root%/}/orgmemory-keycloak-mcp.XXXXXX")"

validate_path_segment() {
  local kind="$1"
  local value="$2"
  if [[ ${#value} -gt 128 || \
        ! "$value" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ || \
        "$value" == *..* || \
        "$value" == *. ]]; then
    printf 'Invalid Keycloak %s name: %s\n' "$kind" "$value" >&2
    exit 64
  fi
}

validate_path_segment realm "$realm"

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

realm_theme_path="$tmp_dir/realm-theme.json"
kcadm get "realms/$realm" --fields loginTheme >"$realm_theme_path"
current_login_theme="$(
  python3 - "$realm_theme_path" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as stream:
    print(json.load(stream).get("loginTheme") or "")
PY
)"
if [[ "$mode" == "print-login-theme" ]]; then
  printf '%s\n' "${current_login_theme:-keycloak}"
  exit 0
fi

desired_login_theme="${ORGMEMORY_KEYCLOAK_LOGIN_THEME:-orgmemory-shadcn}"
validate_path_segment 'login theme' "$desired_login_theme"
if [[ "$mode" != "restore-login-theme" ]]; then
  case "$desired_login_theme" in
    keycloak|keycloak.v2)
      ;;
    orgmemory-shadcn)
      theme_artifact="/opt/keycloak/providers/orgmemory-keycloak-theme.jar"
      if ! keycloak_exec test -r "$theme_artifact"; then
        printf 'Keycloak image is missing login theme artifact %s\n' \
          "$theme_artifact" >&2
        exit 1
      fi
      ;;
    *)
      theme_properties="/opt/keycloak/themes/$desired_login_theme/login/theme.properties"
      if ! keycloak_exec test -r "$theme_properties"; then
        printf 'Keycloak image is missing login theme %s\n' "$desired_login_theme" >&2
        exit 1
      fi
      ;;
  esac
fi
if [[ "$current_login_theme" != "$desired_login_theme" ]]; then
  kcadm update "realms/$realm" -s "loginTheme=$desired_login_theme"
fi
kcadm get "realms/$realm" --fields loginTheme >"$realm_theme_path"
python3 - "$realm_theme_path" "$desired_login_theme" <<'PY'
import json
import sys

path, expected = sys.argv[1:]
with open(path, encoding="utf-8") as stream:
    actual = json.load(stream).get("loginTheme")
if actual != expected:
    raise SystemExit(
        f"Keycloak login theme verification failed: expected {expected!r}, got {actual!r}"
    )
PY
if [[ "$mode" == "login-theme-only" || "$mode" == "restore-login-theme" ]]; then
  exit 0
fi

resource_uri="$(
  keycloak_exec printenv ORGMEMORY_MCP_RESOURCE_URI
)"
if [[ -z "$resource_uri" ]]; then
  printf 'Keycloak is missing ORGMEMORY_MCP_RESOURCE_URI\n' >&2
  exit 1
fi
write_scope_source="$tmp_dir/assets-write-scope.json"
python3 - "$write_scope_template" "$resource_uri" >"$write_scope_source" <<'PY'
import json
import sys

template_path, resource_uri = sys.argv[1:]
with open(template_path, encoding="utf-8") as stream:
    scope = json.load(stream)
for mapper in scope.get("protocolMappers", []):
    config = mapper.get("config", {})
    if config.get("included.custom.audience") == "${ORGMEMORY_MCP_RESOURCE_URI}":
        config["included.custom.audience"] = resource_uri
json.dump(scope, sys.stdout)
PY

clients_path="$tmp_dir/clients.json"
kcadm get clients -r "$realm" >"$clients_path"
web_client_id="$(
  python3 - "$clients_path" "$team_dev_web_client_source" <<'PY'
import json
import sys

clients_path, desired_path = sys.argv[1:]
with open(clients_path, encoding="utf-8") as stream:
    clients = json.load(stream)
with open(desired_path, encoding="utf-8") as stream:
    client_id = json.load(stream)["clientId"]
matches = [client for client in clients if client.get("clientId") == client_id]
if len(matches) != 1:
    raise SystemExit(f"Expected exactly one Keycloak client {client_id!r}, found {len(matches)}")
print(matches[0]["id"])
PY
)"
web_client_current="$tmp_dir/web-client-current.json"
web_client_synced="$tmp_dir/web-client-synced.json"
kcadm get "clients/$web_client_id" -r "$realm" >"$web_client_current"
python3 \
  - "$web_client_current" "$team_dev_web_client_source" \
  >"$web_client_synced" <<'PY'
import json
import sys

current_path, desired_path = sys.argv[1:]
with open(current_path, encoding="utf-8") as stream:
    current = json.load(stream)
with open(desired_path, encoding="utf-8") as stream:
    desired = json.load(stream)
if current.get("clientId") != desired["clientId"]:
    raise SystemExit("Refusing to update a different Keycloak web client")
for key in ("redirectUris", "webOrigins"):
    current[key] = list(dict.fromkeys([*current.get(key, []), *desired.get(key, [])]))
current_attributes = current.setdefault("attributes", {})
desired_logout = desired.get("attributes", {}).get("post.logout.redirect.uris", "")
logout_values = [
    *current_attributes.get("post.logout.redirect.uris", "").split("##"),
    *desired_logout.split("##"),
]
current_attributes["post.logout.redirect.uris"] = "##".join(
    dict.fromkeys(value for value in logout_values if value)
)
json.dump(current, sys.stdout)
PY
kcadm update "clients/$web_client_id" -r "$realm" -f - <"$web_client_synced"

gateway_client_id="$(
  python3 - "$clients_path" "$gateway_client_source" <<'PY'
import json
import sys

clients_path, desired_path = sys.argv[1:]
with open(clients_path, encoding="utf-8") as stream:
    clients = json.load(stream)
with open(desired_path, encoding="utf-8") as stream:
    client_id = json.load(stream)["clientId"]
matches = [client for client in clients if client.get("clientId") == client_id]
if len(matches) != 1:
    raise SystemExit(
        f"Expected exactly one Keycloak client {client_id!r}, found {len(matches)}"
    )
print(matches[0]["id"])
PY
)"
gateway_client_current="$tmp_dir/gateway-client-current.json"
gateway_client_synced="$tmp_dir/gateway-client-synced.json"
kcadm get "clients/$gateway_client_id" \
  -r "$realm" >"$gateway_client_current"
python3 \
  - "$gateway_client_current" "$gateway_client_source" \
  >"$gateway_client_synced" <<'PY'
import json
import sys

current_path, desired_path = sys.argv[1:]
with open(current_path, encoding="utf-8") as stream:
    current = json.load(stream)
with open(desired_path, encoding="utf-8") as stream:
    desired = json.load(stream)
if current.get("clientId") != desired["clientId"]:
    raise SystemExit("Refusing to update a different Keycloak client")
current["attributes"] = {
    **current.get("attributes", {}),
    **desired.get("attributes", {}),
}
json.dump(current, sys.stdout)
PY
kcadm update "clients/$gateway_client_id" \
  -r "$realm" \
  -f - <"$gateway_client_synced"
kcadm get "clients/$gateway_client_id" \
  -r "$realm" >"$gateway_client_current"
python3 \
  - "$gateway_client_current" "$gateway_client_source" <<'PY'
import json
import sys

current_path, desired_path = sys.argv[1:]
with open(current_path, encoding="utf-8") as stream:
    current = json.load(stream)
with open(desired_path, encoding="utf-8") as stream:
    desired = json.load(stream)
actual_attributes = current.get("attributes", {})
for key, value in desired.get("attributes", {}).items():
    if actual_attributes.get(key) != value:
        raise SystemExit(
            f"Keycloak gateway client attribute verification failed for {key}"
        )
PY

client_scopes_csv="$tmp_dir/client-scopes.csv"
kcadm get client-scopes \
  -r "$realm" \
  --fields id,name \
  --format csv \
  --noquotes \
  | tr -d '\r' >"$client_scopes_csv"
basic_scope_id="$(
  awk -F, '$2 == "basic" { print $1; exit }' "$client_scopes_csv"
)"
if [[ -z "$basic_scope_id" ]]; then
  kcadm create client-scopes -r "$realm" -f - <"$basic_scope_source" >/dev/null
else
  basic_scope_current="$tmp_dir/basic-scope-current.json"
  basic_scope_synced="$tmp_dir/basic-scope-synced.json"
  kcadm get "client-scopes/$basic_scope_id" \
    -r "$realm" >"$basic_scope_current"
  python3 \
    - "$basic_scope_current" "$basic_scope_source" >"$basic_scope_synced" <<'PY'
import json
import sys

current_path, desired_path = sys.argv[1:]
with open(current_path, encoding="utf-8") as stream:
    current = json.load(stream)
with open(desired_path, encoding="utf-8") as stream:
    desired = json.load(stream)

desired["id"] = current["id"]
current_mapper_ids = {
    mapper.get("name"): mapper.get("id")
    for mapper in current.get("protocolMappers", [])
}
for mapper in desired.get("protocolMappers", []):
    mapper_id = current_mapper_ids.get(mapper.get("name"))
    if mapper_id:
        mapper["id"] = mapper_id
json.dump(desired, sys.stdout)
PY
  kcadm update "client-scopes/$basic_scope_id" \
    -r "$realm" \
    -f - <"$basic_scope_synced"
fi

client_scopes_csv="$tmp_dir/client-scopes-after-basic.csv"
kcadm get client-scopes \
  -r "$realm" \
  --fields id,name \
  --format csv \
  --noquotes \
  | tr -d '\r' >"$client_scopes_csv"
write_scope_id="$(
  awk -F, '$2 == "assets:write" { print $1; exit }' "$client_scopes_csv"
)"
if [[ -z "$write_scope_id" ]]; then
  kcadm create client-scopes -r "$realm" -f - <"$write_scope_source" >/dev/null
  kcadm get client-scopes \
    -r "$realm" \
    --fields id,name \
    --format csv \
    --noquotes \
    | tr -d '\r' >"$client_scopes_csv"
  write_scope_id="$(
    awk -F, '$2 == "assets:write" { print $1; exit }' "$client_scopes_csv"
  )"
else
  write_scope_current="$tmp_dir/write-scope-current.json"
  write_scope_synced="$tmp_dir/write-scope-synced.json"
  kcadm get "client-scopes/$write_scope_id" \
    -r "$realm" >"$write_scope_current"
  python3 \
    - "$write_scope_current" "$write_scope_source" >"$write_scope_synced" <<'PY'
import json
import sys

current_path, desired_path = sys.argv[1:]
with open(current_path, encoding="utf-8") as stream:
    current = json.load(stream)
with open(desired_path, encoding="utf-8") as stream:
    desired = json.load(stream)

desired["id"] = current["id"]
current_mapper_ids = {
    mapper.get("name"): mapper.get("id")
    for mapper in current.get("protocolMappers", [])
}
for mapper in desired.get("protocolMappers", []):
    mapper_id = current_mapper_ids.get(mapper.get("name"))
    if mapper_id:
        mapper["id"] = mapper_id
json.dump(desired, sys.stdout)
PY
  kcadm update "client-scopes/$write_scope_id" \
    -r "$realm" \
    -f - <"$write_scope_synced"
fi

if [[ -z "$write_scope_id" ]]; then
  printf 'Failed to create the assets:write client scope\n' >&2
  exit 1
fi

optional_scopes_path="$tmp_dir/gateway-optional-scopes.json"
kcadm get "clients/$gateway_client_id/optional-client-scopes" \
  -r "$realm" >"$optional_scopes_path"
if ! python3 - "$optional_scopes_path" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as stream:
    scopes = json.load(stream)
raise SystemExit(0 if any(scope.get("name") == "assets:write" for scope in scopes) else 1)
PY
then
  kcadm update \
    "clients/$gateway_client_id/optional-client-scopes/$write_scope_id" \
    -r "$realm"
fi

kcadm get "clients/$gateway_client_id/optional-client-scopes" \
  -r "$realm" >"$optional_scopes_path"
python3 - "$optional_scopes_path" "$gateway_client_source" <<'PY'
import json
import sys

actual_path, desired_path = sys.argv[1:]
with open(actual_path, encoding="utf-8") as stream:
    actual = {scope.get("name") for scope in json.load(stream)}
with open(desired_path, encoding="utf-8") as stream:
    desired = set(json.load(stream).get("optionalClientScopes", []))
missing = desired - actual
if missing:
    raise SystemExit(
        "Keycloak gateway client is missing optional scopes: "
        + ", ".join(sorted(missing))
    )
PY

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
removed_names = set(desired.get("removeNames", []))
preserved = [
    item for item in current.get(array_key, [])
    if item.get("name") not in desired_names
    and item.get("name") not in removed_names
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

  mapfile -t policy_settings < <(
    python3 - "$registration_policy_source" "$provider_id" <<'PY'
import json
import sys

policy_path, provider_id = sys.argv[1:]
with open(policy_path, encoding="utf-8") as stream:
    policies = json.load(stream)
if provider_id not in policies:
    raise SystemExit(f"Unsupported MCP registration policy: {provider_id}")
for key, values in policies[provider_id].items():
    print(f'config."{key}"={json.dumps(values, separators=(",", ":"))}')
PY
  )
  if [[ "${#policy_settings[@]}" -eq 0 ]]; then
    printf 'MCP registration policy has no settings: %s\n' "$provider_id" >&2
    exit 1
  fi
  policy_set_args=()
  for setting in "${policy_settings[@]}"; do
    policy_set_args+=(-s "$setting")
  done
  kcadm update "components/$component_id" \
    -r "$realm" \
    "${policy_set_args[@]}"

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
    for removed_name in expected.get("removeNames", []):
        if removed_name in actual_by_name:
            raise SystemExit(
                f"Retired Keycloak {array_key} still exists: {removed_name}"
            )
    for item in expected[array_key]:
        if actual_by_name.get(item["name"]) != item:
            raise SystemExit(
                f"Keycloak {array_key} verification failed for {item['name']}"
            )
PY

printf 'Keycloak MCP client onboarding policies are configured.\n'
