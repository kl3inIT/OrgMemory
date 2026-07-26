# MCP Asset Delivery Runbook

## Contract

The remote endpoint is `/mcp`. It exposes:

- tools: `search_assets`, `get_asset`, `get_asset_release`,
  `get_capability_pack`, `resolve_asset_relations`, and `render_prompt`;
- resources: `orgmemory://assets/{assetId}` and
  `orgmemory://assets/{assetId}/releases/{releaseId}`;
- prompt: `released_prompt(asset_id, release_id, variables_json)`.

All operations are read-only. `render_prompt` performs deterministic variable
validation and substitution; it does not call an AI provider. There is no
`run_prompt`, progress update, fork, feedback, install, approval, publication,
withdrawal, permission mutation, or arbitrary execution tool.

## OAuth Setup

Set one canonical, externally reachable URI:

```text
ORGMEMORY_MCP_RESOURCE_URI=https://om.kl3in.tech/mcp
ORGMEMORY_MCP_AUDIENCE=https://om.kl3in.tech/mcp
```

Configure the issuer to advertise the optional `assets:read` client scope.
The checked-in Keycloak realm maps that scope to two inbound audiences:

- the exact MCP resource URI, so the MCP resource server can accept it;
- `orgmemory-mcp`, so that confidential client may perform standard token
  exchange for the user.

The `orgmemory-web` audience mapper belongs only to the confidential
`orgmemory-mcp` exchange client. The inbound MCP token is therefore rejected by
the API, while the exchanged token is accepted by the canonical API and
rejected by the MCP resource.

Enable **Standard token exchange** only on the confidential `orgmemory-mcp`
client and keep its secret in the deployment secret store. The MCP gateway
validates the issuer, expiry, MCP audience, and `assets:read`, then performs a
fresh exchange for a short-lived `orgmemory-web` audience token on every MCP
request. Exchanged clients are not persisted by principal name, and the
inbound bearer is never forwarded to the API.

For each supported MCP host, pre-register an OAuth client with exact redirect
URIs and assign `assets:read` as an optional scope. Do not enable anonymous
access, wildcard redirects, or unrestricted dynamic client registration for
the POC. A token with only the API audience is rejected by MCP.

The checked-in realm files are a baseline for a new Keycloak realm. Keycloak
imports with `IGNORE_EXISTING`, so deploying a new image does not mutate an
already-created realm. Before enabling MCP in an existing environment, apply
the same `assets:read` scope/mappers and `orgmemory-mcp` confidential client
through the Keycloak administration path, then verify:

- the incoming MCP token has the MCP URI and `orgmemory-mcp` audiences, but
  does not have `orgmemory-web`;
- the exchanged token has only `orgmemory-web`, has
  `azp=orgmemory-mcp`, and retains `assets:read`.

Rotate `ORGMEMORY_MCP_OIDC_CLIENT_SECRET` independently from the web client
secret. Never put either value in a document, issue, or log.

Discovery is:

```text
GET https://om.kl3in.tech/.well-known/oauth-protected-resource/mcp
```

An unauthenticated `/mcp` request returns `401` and a `WWW-Authenticate`
challenge pointing to that metadata URL.

## Authorization And Operations

OAuth scopes are coarse admission only. `/api/asset-delivery` resolves the
current actor and live `CAN_USE` authorization for every Asset. Pack items and
relations are checked independently. A denial, missing ID, or cross-tenant ID
returns one opaque unavailable result.

MCP never reads the database and never imports `core`; it calls the canonical
API with the exchanged token. Successful API delivery emits a sanitized
structured audit line. Do not log bearer tokens, Prompt variables, payloads,
or denial details.

The POC limiter uses Bucket4j token buckets with bounded, expiring Caffeine
state in the MCP process:

- 120 requests/minute per `(subject, OAuth client)`;
- 800 requests/minute across the process;
- at most 10,000 tracked callers, evicted after 15 minutes idle;
- a 256 KiB body limit, enforced for both known-length and chunked requests.

Tune these through `ORGMEMORY_MCP_CALLER_RATE_*`,
`ORGMEMORY_MCP_GLOBAL_RATE_*`, `ORGMEMORY_MCP_MAX_TRACKED_CALLERS`, and
`ORGMEMORY_MCP_MAX_BODY_BYTES`. This is deliberately single-replica state.
Before scaling MCP horizontally, replace it with a Bucket4j distributed proxy
manager backed by an approved shared store; do not claim a cluster-wide limit
from independent Caffeine caches.
