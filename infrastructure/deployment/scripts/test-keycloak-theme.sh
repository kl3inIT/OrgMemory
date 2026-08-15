#!/usr/bin/env bash
set -Eeuo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
theme="orgmemory-shadcn"
run_id="${RANDOM}-$$"
container="orgmemory-keycloak-theme-test-${run_id}"
image="orgmemory-keycloak-theme-test:${run_id}"
tmp_root="${TMPDIR:-/tmp}"
tmp_dir="$(mktemp -d "${tmp_root%/}/orgmemory-keycloak-theme-test.XXXXXX")"

cleanup() {
  docker rm --force "$container" >/dev/null 2>&1 || true
  if [[ "${ORGMEMORY_KEEP_THEME_TEST_IMAGE:-false}" != "true" ]]; then
    docker image rm "$image" >/dev/null 2>&1 || true
  fi
  case "$tmp_dir" in
    "${tmp_root%/}"/orgmemory-keycloak-theme-test.*)
      rm -rf -- "$tmp_dir"
      ;;
    *)
      printf 'Refusing to remove unexpected temporary path: %s\n' "$tmp_dir" >&2
      ;;
  esac
}
trap cleanup EXIT

python3 - "$repo_root" "$theme" <<'PY'
import json
import pathlib
import re
import sys

root = pathlib.Path(sys.argv[1])
theme = sys.argv[2]
theme_ui = root / "infrastructure" / "keycloak" / "theme-ui"
required = (
    theme_ui / "package.json",
    theme_ui / "vite.config.ts",
    theme_ui / "src" / "login" / "main.css",
    theme_ui / "src" / "assets" / "fonts" / "hanken-grotesk-latin-wght-normal.woff2",
    theme_ui / "src" / "assets" / "fonts" / "OFL.txt",
    theme_ui / "public" / "favicon.svg",
    theme_ui / "scripts" / "verify-production-login.mjs",
)
missing = [str(path.relative_to(root)) for path in required if not path.is_file()]
if missing:
    raise SystemExit("Missing Keycloakify theme files: " + ", ".join(missing))

package = json.loads((theme_ui / "package.json").read_text(encoding="utf-8"))
if package.get("name") != "@orgmemory/keycloak-theme":
    raise SystemExit("Unexpected Keycloakify package name")
if package.get("dependencies", {}).get("keycloakify") != "11.15.14":
    raise SystemExit("Keycloakify must be pinned to 11.15.14")

vite = (theme_ui / "vite.config.ts").read_text(encoding="utf-8")
for expected in (theme, 'accountThemeImplementation: "none"', '"all-other-versions"'):
    if expected not in vite:
        raise SystemExit(f"Keycloakify config is missing {expected!r}")

css = (theme_ui / "src" / "login" / "main.css").read_text(encoding="utf-8")
if "--orgmemory-theme-marker: orgmemory-shadcn" not in css:
    raise SystemExit("Theme CSS marker is missing")
if re.search(r"url\(\s*['\"]?https?://", css, re.IGNORECASE):
    raise SystemExit("Theme CSS must not load external assets")
if "prefers-reduced-motion" not in css or ":focus-visible" not in css:
    raise SystemExit("Theme CSS must retain motion and keyboard-focus guardrails")

native_theme = root / "infrastructure" / "keycloak" / "themes" / theme
if native_theme.exists():
    raise SystemExit("Native and Keycloakify themes must not ship under the same name")

for realm_name in ("orgmemory-realm.json", "orgmemory-realm.prod.json"):
    realm_path = root / "infrastructure" / "keycloak" / realm_name
    realm = json.loads(realm_path.read_text(encoding="utf-8"))
    if realm.get("loginTheme") != theme:
        raise SystemExit(f"{realm_name} does not select {theme}")

dockerfile = (root / "infrastructure" / "keycloak" / "Dockerfile").read_text(encoding="utf-8")
for expected in (
    "build-keycloak-theme",
    "/opt/keycloak/providers/orgmemory-keycloak-theme.jar",
    "/opt/keycloak/bin/kc.sh build",
):
    if expected not in dockerfile:
        raise SystemExit(f"Keycloak Dockerfile is missing {expected!r}")
if "infrastructure/keycloak/themes/" in dockerfile:
    raise SystemExit("Keycloak Dockerfile must not package the replaced native theme")
dockerignore = (root / ".dockerignore").read_text(encoding="utf-8")
if "**/dist_keycloak" not in dockerignore:
    raise SystemExit("Docker context must exclude generated Keycloakify JAR output")

verifier = (theme_ui / "scripts" / "verify-production-login.mjs").read_text(encoding="utf-8")
if "submit.form === form" not in verifier or 'submit.closest("form")' in verifier:
    raise SystemExit("Production verifier must validate the submit control's actual form owner")
if "/login/keycloak/css/login.css" not in verifier or "keycloak.v2/css/styles.css" in verifier:
    raise SystemExit("Production verifier must validate Keycloak 26.7's stock keycloak theme assets")

smoke = (root / "infrastructure" / "deployment" / "scripts" / "smoke-production.sh").read_text(
    encoding="utf-8"
)
if "/login/keycloak/css/login.css" not in smoke or "/login/keycloak.v2/css/styles.css" in smoke:
    raise SystemExit("Server smoke must validate Keycloak 26.7's stock keycloak theme assets")

deploy = (root / "infrastructure" / "deployment" / "scripts" / "deploy.sh").read_text(encoding="utf-8")
for expected in (
    "previous_keycloak_login_theme",
    "keycloak_theme_reconcile_attempted=true",
    "--print-login-theme",
    "--restore-login-theme",
):
    if expected not in deploy:
        raise SystemExit(f"Deployment rollback contract is missing {expected!r}")
rollback = deploy[deploy.index("rollback() {"):deploy.index("trap rollback ERR")]
restore_position = rollback.find("ORGMEMORY_KEYCLOAK_LOGIN_THEME")
image_position = rollback.find('install -m 0600 "$previous_environment" "$environment_file"')
if restore_position < 0 or image_position < 0 or restore_position > image_position:
    raise SystemExit("Deployment must restore the realm login theme before reverting the image")
PY

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

port="$(docker port "$container" 8080/tcp | tr -d '\r' | tail -n 1 | sed -E 's/.*:([0-9]+)$/\1/')"
base_url="http://127.0.0.1:${port}"
metadata_url="$base_url/realms/orgmemory/.well-known/openid-configuration"

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

authorization_status="$(
  curl --silent --show-error \
    --output "$tmp_dir/login.html" \
    --write-out '%{http_code}' \
    --get "$base_url/realms/orgmemory/protocol/openid-connect/auth" \
    --data-urlencode response_type=code \
    --data-urlencode client_id=orgmemory-web \
    --data-urlencode redirect_uri=https://om.kl3in.tech/login/oauth2/code/keycloak \
    --data-urlencode scope=openid \
    --data-urlencode state=theme-verification \
    --data-urlencode nonce=theme-verification \
    --data-urlencode code_challenge=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA \
    --data-urlencode code_challenge_method=S256
)"
[[ "$authorization_status" == "200" ]]

python3 - "$tmp_dir/login.html" "$base_url" "$theme" "$tmp_dir/asset-urls.txt" <<'PY'
import html
import pathlib
import re
import sys

page_path, base_url, theme, output_path = sys.argv[1:]
page = pathlib.Path(page_path).read_text(encoding="utf-8")
for marker in ('id="root"', "window.kcContext"):
    if marker not in page:
        raise SystemExit(f"Keycloakify bootstrap page is missing {marker}")

script_matches = re.findall(r'<script[^>]+src="([^"]+\.js)"', page)
script_url = next(
    (html.unescape(value) for value in script_matches if f"/{theme}/dist/assets/" in value),
    None,
)
favicon_match = re.search(r'<link[^>]+href="([^"]*?/favicon\.svg)"', page)
if script_url is None or favicon_match is None:
    raise SystemExit(f"Keycloakify bootstrap did not select theme {theme}")

urls = [script_url, html.unescape(favicon_match.group(1))]
urls = [base_url + value if value.startswith("/") else value for value in urls]
pathlib.Path(output_path).write_text("\n".join(urls) + "\n", encoding="utf-8", newline="\n")
PY

while IFS= read -r asset_url; do
  curl --fail --silent --show-error --output /dev/null "$asset_url"
done <"$tmp_dir/asset-urls.txt"

runtime_uid="$(docker exec "$container" id -u)"
[[ "$runtime_uid" == "1000" ]]
owner_mode="$(
  # MSYS_NO_PATHCONV keeps Git Bash from rewriting the in-container path.
  MSYS_NO_PATHCONV=1 docker exec "$container" stat -c '%u:%g:%a' \
    /opt/keycloak/providers/orgmemory-keycloak-theme.jar
)"
# The runtime stays non-root, while the immutable provider is root-owned and
# cannot be modified by the Keycloak process.
[[ "$owner_mode" == "0:0:444" ]]

auth_url="$base_url/realms/orgmemory/protocol/openid-connect/auth?response_type=code&client_id=orgmemory-web&redirect_uri=https%3A%2F%2Fom.kl3in.tech%2Flogin%2Foauth2%2Fcode%2Fkeycloak&scope=openid&state=browser-verification&nonce=browser-verification&code_challenge=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA&code_challenge_method=S256"
corepack pnpm --dir "$repo_root/infrastructure/keycloak/theme-ui" \
  exec node --input-type=module - "$auth_url" <<'JS'
import { chromium } from "@playwright/test"

const authUrl = process.argv[2]
const browser = await chromium.launch({ headless: true })
try {
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
  const failedRequests = []
  const externalRequests = []
  const consoleErrors = []
  const successfulAssets = []
  page.on("requestfailed", request => {
    failedRequests.push(`${request.url()} ${request.failure()?.errorText ?? "failed"}`)
  })
  page.on("request", request => {
    if (new URL(request.url()).hostname !== "127.0.0.1") {
      externalRequests.push(request.url())
    }
  })
  page.on("console", message => {
    if (message.type() === "error") {
      consoleErrors.push(message.text())
    }
  })
  page.on("response", response => {
    if (response.ok() && /orgmemory-shadcn.*\.(?:css|woff2|js|svg)(?:\?|$)/.test(response.url())) {
      successfulAssets.push(response.url())
    }
  })

  await page.goto(authUrl, { waitUntil: "networkidle" })
  const card = page.locator(".orgmemory-login__card")
  const username = page.locator('input[name="username"]')
  const forgotPassword = page.getByRole("link", { name: "Forgot password?" })
  const cardBox = await card.boundingBox()
  const usernameBox = await username.boundingBox()
  const forgotBox = await forgotPassword.boundingBox()
  if (!cardBox || cardBox.width < 400 || cardBox.width > 460) {
    throw new Error(`desktop login card width is outside 400-460px: ${JSON.stringify(cardBox)}`)
  }
  if (!usernameBox || usernameBox.width < 330) {
    throw new Error(`desktop username control is too narrow: ${JSON.stringify(usernameBox)}`)
  }
  if (!forgotBox || forgotBox.height > 28) {
    throw new Error(`forgot-password link wrapped unexpectedly: ${JSON.stringify(forgotBox)}`)
  }
  if (await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth)) {
    throw new Error("desktop login page has horizontal overflow")
  }
  if (failedRequests.length || externalRequests.length || consoleErrors.length) {
    throw new Error(JSON.stringify({ failedRequests, externalRequests, consoleErrors }))
  }
  if (!successfulAssets.some(url => url.endsWith(".woff2"))) {
    throw new Error(`Keycloakify did not serve a local Hanken Grotesk font: ${successfulAssets}`)
  }
  await page.evaluate(() => document.activeElement?.blur())
  const focusedControl = page.locator('input[name="username"]')
  for (let index = 0; index < 10; index += 1) {
    await page.keyboard.press("Tab")
    if (await focusedControl.evaluate(element => element === document.activeElement)) {
      break
    }
  }
  const focusAppearance = await focusedControl.evaluate(element => {
    const style = getComputedStyle(element)
    return {
      focusVisible: element.matches(":focus-visible"),
      outlineStyle: style.outlineStyle,
      outlineWidth: Number.parseFloat(style.outlineWidth)
    }
  })
  if (
    !focusAppearance.focusVisible ||
    focusAppearance.outlineStyle === "none" ||
    focusAppearance.outlineWidth < 2
  ) {
    throw new Error(`username keyboard focus is not visibly outlined: ${JSON.stringify(focusAppearance)}`)
  }
  const themeStylesheet = await page.locator('link[rel="stylesheet"]').evaluateAll(links =>
    links.map(link => link.href).find(href => href.includes("/orgmemory-shadcn/dist/assets/"))
  )
  if (!themeStylesheet) {
    throw new Error("Keycloakify theme stylesheet was not loaded")
  }
  const stylesheetText = await page.evaluate(async url => (await fetch(url)).text(), themeStylesheet)
  if (!/--orgmemory-theme-marker:\s*orgmemory-shadcn/.test(stylesheetText)) {
    throw new Error("Keycloakify theme stylesheet marker is missing")
  }

  await page.emulateMedia({ colorScheme: "dark" })
  await page.reload({ waitUntil: "networkidle" })
  const darkControlBackgrounds = await page.evaluate(() =>
    ["username", "password"].map(name =>
      getComputedStyle(document.querySelector(`input[name="${name}"]`)).backgroundColor
    )
  )
  if (darkControlBackgrounds.some(value => value.includes("/"))) {
    throw new Error(`dark controls must use an opaque surface: ${darkControlBackgrounds}`)
  }
  await page.emulateMedia({ colorScheme: "light" })
  await page.goto(authUrl, { waitUntil: "networkidle" })

  await forgotPassword.click()
  await page.waitForLoadState("networkidle")
  if (!(await page.getByRole("heading", { name: "Reset your access" }).isVisible())) {
    throw new Error("password-reset screen did not retain the OrgMemory theme")
  }
  const resetCardBox = await card.boundingBox()
  if (!resetCardBox || resetCardBox.width < 400 || resetCardBox.width > 460) {
    throw new Error(`password-reset card width is outside 400-460px: ${JSON.stringify(resetCardBox)}`)
  }

  await page.goto(authUrl, { waitUntil: "networkidle" })
  await username.fill("theme-verification")
  await page.locator('input[name="password"]').fill("invalid-verification-password")
  await page.locator("#kc-login").click()
  await page.waitForLoadState("networkidle")
  const invalidFeedback = page.locator("#input-error").filter({
    hasText: "Invalid username or password."
  })
  if (!(await invalidFeedback.isVisible())) {
    throw new Error("invalid-credential feedback did not render as themed inline feedback")
  }

  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto(authUrl, { waitUntil: "networkidle" })
  const mobileCard = await card.boundingBox()
  if (!mobileCard || mobileCard.width < 350 || mobileCard.width > 390 || mobileCard.height > 600) {
    throw new Error(`mobile login card does not fit the viewport: ${JSON.stringify(mobileCard)}`)
  }
  if (await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth)) {
    throw new Error("mobile login page has horizontal overflow")
  }
} finally {
  await browser.close()
}
JS

MSYS_NO_PATHCONV=1 docker exec "$container" /opt/keycloak/bin/kcadm.sh config credentials \
  --server http://127.0.0.1:8080 \
  --realm master \
  --user admin \
  --password verification-only >/dev/null
MSYS_NO_PATHCONV=1 docker exec "$container" /opt/keycloak/bin/kcadm.sh update realms/orgmemory \
  --server http://127.0.0.1:8080 \
  --realm master \
  -s loginTheme=keycloak >/dev/null
ORGMEMORY_AUTH_ORIGIN="$base_url" \
  corepack pnpm --dir "$repo_root/infrastructure/keycloak/theme-ui" \
    exec node scripts/verify-production-login.mjs keycloak

printf 'OrgMemory Keycloak theme contract passed at %s.\n' "$base_url"
