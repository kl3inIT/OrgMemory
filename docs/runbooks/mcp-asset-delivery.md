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
ORGMEMORY_MCP_RESOURCE_URI=https://mcp.example.com/mcp
ORGMEMORY_MCP_AUDIENCE=https://mcp.example.com/mcp
```

Configure the issuer to advertise optional `assets:read` and `assets:use`
client scopes. Add an Audience mapper whose Included Custom Audience exactly
matches `ORGMEMORY_MCP_RESOURCE_URI`. The POC bearer must contain:

- issuer equal to `ORGMEMORY_OIDC_ISSUER_URI`;
- both the MCP resource URI and `orgmemory-web` in `aud`;
- `assets:read` for search/read/resource calls;
- `assets:use` for Prompt render/prompt calls.

This is a dual-audience pass-through contract because the API independently
validates `orgmemory-web`. Do not accept an API-only token at MCP. If the issuer
cannot issue the dual audience, introduce explicit token exchange or
on-behalf-of before enabling the endpoint.

Discovery is:

```text
GET https://mcp.example.com/.well-known/oauth-protected-resource/mcp
```

An unauthenticated `/mcp` request returns `401` and a `WWW-Authenticate`
challenge pointing to that metadata URL.

## Authorization And Operations

OAuth scopes are coarse admission only. `/api/asset-delivery` resolves the
current actor and live `CAN_USE` authorization for every Asset. Pack items and
relations are checked independently. A denial, missing ID, or cross-tenant ID
returns one opaque unavailable result.

MCP never reads the database and never imports `core`; it forwards the bearer
to the canonical API. Successful API delivery emits a sanitized structured
audit line. Do not log bearer tokens, Prompt variables, payloads, or denial
details. The default rate limit is 120 MCP requests per authenticated subject
per minute and can be changed with
`ORGMEMORY_MCP_RATE_LIMIT_PER_MINUTE`.
