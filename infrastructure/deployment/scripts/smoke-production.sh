#!/usr/bin/env bash
set -Eeuo pipefail

repo_root="${ORGMEMORY_REPO_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)}"
compose_file="$repo_root/infrastructure/deployment/compose.production.yaml"
environment_file="${ORGMEMORY_ENV_FILE:-$repo_root/.env.production}"

compose=(
  docker compose
  --file "$compose_file"
  --env-file "$environment_file"
)

retry() {
  local attempts="$1"
  local delay_seconds="$2"
  shift 2

  local attempt
  for ((attempt = 1; attempt <= attempts; attempt++)); do
    if timeout 15s "$@"; then
      return 0
    fi
    if ((attempt < attempts)); then
      sleep "$delay_seconds"
    fi
  done
  return 1
}

retry 5 2 "${compose[@]}" exec -T web sh -ec \
  "wget -q -T 5 -O - http://127.0.0.1:8080/healthz | grep -Fxq ok"

retry 5 2 "${compose[@]}" exec -T api sh -ec \
  "wget -q -T 5 -O - http://127.0.0.1:8080/actuator/health/readiness | grep -q '\"status\":\"UP\"'"

retry 5 2 "${compose[@]}" exec -T mcp sh -ec \
  "wget -q -T 5 -O - http://127.0.0.1:8090/actuator/health/readiness | grep -q '\"status\":\"UP\"'"

retry 5 2 "${compose[@]}" exec -T keycloak bash -ec \
  "{ printf 'GET /health/ready HTTP/1.0\r\n\r\n' >&0; grep -q '\"status\" *: *\"UP\"'; } 0<>/dev/tcp/127.0.0.1/9000"

if [[ "${ORGMEMORY_REQUIRE_PUBLIC_SMOKE:-false}" == "true" ]]; then
  web_status="$(curl --fail --silent --show-error \
    --connect-timeout 5 \
    --max-time 15 \
    --retry 5 \
    --retry-all-errors \
    --retry-delay 2 \
    --output /dev/null \
    --write-out '%{http_code}' \
    https://om.kl3in.tech/healthz)"
  [[ "$web_status" == "200" ]]

  issuer="$(curl --fail --silent --show-error \
    --connect-timeout 5 \
    --max-time 15 \
    --retry 5 \
    --retry-all-errors \
    --retry-delay 2 \
    https://auth.kl3in.tech/realms/orgmemory/.well-known/openid-configuration \
    | python3 -c 'import json,sys; print(json.load(sys.stdin)["issuer"])')"
  [[ "$issuer" == "https://auth.kl3in.tech/realms/orgmemory" ]]

  login_page="$(mktemp)"
  trap 'rm -f "$login_page"' EXIT
  login_status="$(
    curl --silent --show-error \
      --connect-timeout 5 \
      --max-time 15 \
      --retry 5 \
      --retry-all-errors \
      --retry-delay 2 \
      --output "$login_page" \
      --write-out '%{http_code}' \
      --get https://auth.kl3in.tech/realms/orgmemory/protocol/openid-connect/auth \
      --data-urlencode response_type=code \
      --data-urlencode client_id=orgmemory-web \
      --data-urlencode redirect_uri=https://om.kl3in.tech/login/oauth2/code/keycloak \
      --data-urlencode scope=openid \
      --data-urlencode state=production-smoke \
      --data-urlencode nonce=production-smoke \
      --data-urlencode code_challenge=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA \
      --data-urlencode code_challenge_method=S256
  )"
  [[ "$login_status" == "200" ]]
  login_assets="$(mktemp)"
  trap 'rm -f "$login_page" "$login_assets" "$login_assets.css" "$login_assets.js"' EXIT
  python3 - "$login_page" "${ORGMEMORY_KEYCLOAK_LOGIN_THEME:-}" "$login_assets" <<'PY'
import html
import pathlib
import re
import sys
from urllib.parse import urljoin, urlsplit

page_path, expected_theme, output_path = sys.argv[1:]
page = pathlib.Path(page_path).read_text(encoding="utf-8")
stock_form = 'name="username"' in page
keycloakify_bootstrap = 'id="root"' in page and "window.kcContext" in page
if expected_theme == "keycloak":
    stock_markers = (
        'class="login-pf"' in page
        and 'data-page-id="login-login"' in page
        and 'id="kc-form-login"' in page
        and 'method="post"' in page
        and '/login/keycloak/css/login.css' in page
    )
    if not stock_form or keycloakify_bootstrap or not stock_markers:
        raise SystemExit(f"expected stock Keycloak login theme {expected_theme!r}")
    pathlib.Path(output_path).write_text("", encoding="utf-8")
elif expected_theme == "orgmemory-shadcn":
    if not keycloakify_bootstrap or stock_form:
        raise SystemExit("expected the OrgMemory Keycloakify bootstrap, not a stock fallback")
    script = re.search(r'<script[^>]+src="([^"]+\.js[^"]*)"', page)
    if script is None:
        raise SystemExit("Keycloakify login bootstrap is missing its JavaScript entrypoint")
    assets = []
    for kind, match in (("js", script),):
        value = html.unescape(match.group(1))
        if f"/{expected_theme}/dist/assets/" not in value:
            raise SystemExit(f"unexpected {kind} asset for {expected_theme}: {value}")
        resolved = urlsplit(urljoin('https://auth.kl3in.tech', value))
        if (
            resolved.scheme != "https"
            or resolved.hostname != "auth.kl3in.tech"
            or resolved.port not in {None, 443}
            or resolved.username is not None
            or resolved.password is not None
        ):
            raise SystemExit(f"unsafe {kind} asset URL for {expected_theme}: {value}")
        assets.append(f"{kind}\t{resolved.geturl()}")
    pathlib.Path(output_path).write_text("\n".join(assets) + "\n", encoding="utf-8")
else:
    raise SystemExit(f"unsupported expected production login theme: {expected_theme!r}")
PY
  while IFS=$'\t' read -r asset_kind asset_url; do
    [[ -n "$asset_kind" && -n "$asset_url" ]] || continue
    curl --fail --silent --show-error \
      --connect-timeout 5 \
      --max-time 15 \
      --output "$login_assets.$asset_kind" \
      "$asset_url"
  done <"$login_assets"
  [[ "${ORGMEMORY_KEYCLOAK_LOGIN_THEME:-}" != "orgmemory-shadcn" || -s "$login_assets.js" ]]
  rm -f "$login_page" "$login_assets" "$login_assets.css" "$login_assets.js"
  trap - EXIT

  protected_resource_metadata="$(
    curl --fail --silent --show-error \
      --connect-timeout 5 \
      --max-time 15 \
      --retry 5 \
      --retry-all-errors \
      --retry-delay 2 \
      https://om.kl3in.tech/.well-known/oauth-protected-resource/mcp
  )"
  python3 -c '
import json
import sys

document = json.load(sys.stdin)
assert document["resource"] == "https://om.kl3in.tech/mcp"
assert document["authorization_servers"] == [
    "https://auth.kl3in.tech/realms/orgmemory"
]
assert "assets:read" in document["scopes_supported"]
assert "assets:write" in document["scopes_supported"]
' <<<"$protected_resource_metadata"

  authorization_metadata="$(
    curl --fail --silent --show-error \
      --connect-timeout 5 \
      --max-time 15 \
      --retry 5 \
      --retry-all-errors \
      --retry-delay 2 \
      https://auth.kl3in.tech/realms/orgmemory/.well-known/oauth-authorization-server
  )"
  python3 -c '
import json
import sys

document = json.load(sys.stdin)
assert document["registration_endpoint"].endswith("/clients-registrations/openid-connect")
assert document.get("client_id_metadata_document_supported") is not True
' <<<"$authorization_metadata"

  challenge_headers="$(mktemp)"
  trap 'rm -f "$challenge_headers"' EXIT
  mcp_status="$(
    curl --silent --show-error \
      --connect-timeout 5 \
      --max-time 15 \
      --retry 5 \
      --retry-all-errors \
      --retry-delay 2 \
      --dump-header "$challenge_headers" \
      --output /dev/null \
      --write-out '%{http_code}' \
      https://om.kl3in.tech/mcp
  )"
  [[ "$mcp_status" == "401" ]]
  grep -Eiq \
    '^[[:space:]]*www-authenticate:.*resource_metadata="https://om\.kl3in\.tech/\.well-known/oauth-protected-resource/mcp"' \
    "$challenge_headers"
  rm -f "$challenge_headers"
  trap - EXIT

  publication_headers="$(mktemp)"
  trap 'rm -f "$publication_headers"' EXIT
  publication_status="$(
    curl --silent --show-error \
      --connect-timeout 5 \
      --max-time 15 \
      --retry 5 \
      --retry-all-errors \
      --retry-delay 2 \
      --dump-header "$publication_headers" \
      --output /dev/null \
      --write-out '%{http_code}' \
      --request POST \
      https://om.kl3in.tech/skill-publications
  )"
  [[ "$publication_status" == "401" ]]
  grep -Eiq \
    '^[[:space:]]*www-authenticate:.*resource_metadata="https://om\.kl3in\.tech/\.well-known/oauth-protected-resource/mcp"' \
    "$publication_headers"
  rm -f "$publication_headers"
  trap - EXIT
fi

printf 'OrgMemory production smoke passed.\n'
