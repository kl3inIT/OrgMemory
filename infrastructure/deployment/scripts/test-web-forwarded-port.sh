#!/usr/bin/env bash
set -Eeuo pipefail

runtime_image="nginxinc/nginx-unprivileged:1.29.5-alpine@sha256:42a7d7f2ee23e9f5a1dcdf3647ba5c585bbd18f79e79cd817e70e8cd61c55779"
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
run_id="${RANDOM}-$$"
network="orgmemory-forwarded-port-${run_id}"
api_container="orgmemory-forwarded-port-api-${run_id}"
web_container="orgmemory-forwarded-port-web-${run_id}"
tmp_root="${TMPDIR:-/tmp}"
tmp_dir="$(mktemp -d "${tmp_root%/}/orgmemory-forwarded-port.XXXXXX")"
web_config_path="$repo_root/web/nginx.conf"
api_config_path="$tmp_dir/api.conf"

if command -v cygpath >/dev/null 2>&1; then
  export MSYS_NO_PATHCONV=1
  web_config_path="$(cygpath -w "$web_config_path")"
  api_config_path="$(cygpath -w "$api_config_path")"
fi

cleanup() {
  docker rm --force "$web_container" "$api_container" >/dev/null 2>&1 || true
  docker network rm "$network" >/dev/null 2>&1 || true
  case "$tmp_dir" in
    "${tmp_root%/}"/orgmemory-forwarded-port.*)
      rm -rf -- "$tmp_dir"
      ;;
    *)
      printf 'Refusing to remove unexpected temporary path: %s\n' "$tmp_dir" >&2
      ;;
  esac
}
trap cleanup EXIT

cat >"$tmp_dir/api.conf" <<'NGINX'
server {
    listen 8080;
    listen 8090;
    server_name _;

    location / {
        add_header X-Seen-Forwarded-Port $http_x_forwarded_port always;
        return 204;
    }
}
NGINX

docker network create "$network" >/dev/null

docker run --detach --rm \
  --name "$api_container" \
  --network "$network" \
  --network-alias api \
  --network-alias mcp \
  --volume "$api_config_path:/etc/nginx/conf.d/default.conf:ro" \
  "$runtime_image" >/dev/null

docker run --detach --rm \
  --name "$web_container" \
  --network "$network" \
  --volume "$web_config_path:/etc/nginx/conf.d/default.conf:ro" \
  "$runtime_image" >/dev/null

ready=false
for _ in {1..30}; do
  if docker exec "$web_container" \
    wget -q -O - http://127.0.0.1:8080/healthz 2>/dev/null \
    | grep -Fxq ok; then
    ready=true
    break
  fi
  sleep 1
done

if [[ "$ready" != "true" ]]; then
  docker logs "$web_container" >&2
  exit 1
fi

assert_forwarded_port() {
  local expected="$1"
  shift
  local headers

  headers="$(
    docker exec "$web_container" \
      wget -S -O /dev/null "$@" \
      http://127.0.0.1:8080/api/probe 2>&1
  )"

  grep -Eiq "^[[:space:]]*X-Seen-Forwarded-Port:[[:space:]]*${expected}[[:space:]]*$" \
    <<<"$headers"
}

assert_forwarded_port 443 --header="X-Forwarded-Proto: https"
assert_forwarded_port 80 --header="X-Forwarded-Proto: http"
assert_forwarded_port 8443 \
  --header="X-Forwarded-Proto: https" \
  --header="X-Forwarded-Port: 8443"

discovery_headers="$(
  docker exec "$web_container" \
    wget -S -O /dev/null \
    --header="X-Forwarded-Proto: https" \
    http://127.0.0.1:8080/.well-known/oauth-protected-resource/mcp 2>&1
)"
grep -Eiq \
  '^[[:space:]]*X-Seen-Forwarded-Port:[[:space:]]*443[[:space:]]*$' \
  <<<"$discovery_headers"

publication_headers="$(
  docker exec "$web_container" \
    wget -S -O /dev/null \
    --post-data="probe" \
    --header="X-Forwarded-Proto: https" \
    http://127.0.0.1:8080/skill-publications 2>&1
)"
grep -Eiq \
  '^[[:space:]]*X-Seen-Forwarded-Port:[[:space:]]*443[[:space:]]*$' \
  <<<"$publication_headers"

printf 'Web forwarded-port regression passed.\n'
