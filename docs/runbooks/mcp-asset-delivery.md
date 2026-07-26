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

Client onboarding is capability-based rather than vendor-specific:

1. use a pre-registered client when an operator has supplied one;
2. use restricted Dynamic Client Registration (DCR) for public MCP clients.

The production authorization-server metadata deliberately does not advertise
CIMD. Claude Web's current metadata combines a public client
(`token_endpoint_auth_method=none`) with the JWT bearer authorization grant,
which Keycloak 26.7 rejects before login. Advertising CIMD therefore sends
Claude down a known-incompatible path. MCP clients fall back to the advertised
DCR endpoint without requiring the user to enter a client ID or secret. Revisit
CIMD when Keycloak accepts that metadata combination or Claude removes the
incompatible grant.

Deployment removes the retired OrgMemory CIMD policy/profile and idempotently
merges the DCR policy without replacing unrelated realm policies. Anonymous DCR
forces PKCE S256, consent, disabled full scope, public clients,
approved redirect/client URI hosts, the standard OIDC `basic` plus
`assets:read` scope allowlist, and a maximum of 50 registered clients. The
migration creates `basic` only when a minimal imported realm does not already
contain it.

This is restricted anonymous *client registration*, not anonymous Asset
access. Every user still signs in and consents. Never enable wildcard
redirects, confidential-only shared secrets for desktop clients, password
grant, service accounts, or unrestricted DCR. The client-count cap is a POC
abuse bound, not a distributed registration rate limiter; monitor and remove
abandoned dynamic clients before raising it. A token with only the API audience
is rejected by MCP.

The checked-in realm files are a baseline for a new Keycloak realm. Keycloak
imports with `IGNORE_EXISTING`, so deploying a new image does not mutate an
already-created realm. `configure-keycloak-mcp.sh` therefore updates only the
named MCP client policies, the required token-exchange attribute on the
existing `orgmemory-mcp` client, and anonymous registration-policy components
after Keycloak is healthy. The client update starts from Keycloak's complete
current representation and merges only the required attribute, preserving its
secret and unrelated settings. The migration preserves unrelated client
policies, users, credentials, federation, and clients. The `assets:read`
scope/mappers and confidential `orgmemory-mcp` exchange client must already
exist from the realm baseline or the initial MCP migration. Verify:

- authorization-server metadata advertises DCR but not CIMD;
- a Claude-shaped DCR request uses the documented callback, creates a public
  PKCE S256 client, and reaches the login page;
- the incoming MCP token has the MCP URI and `orgmemory-mcp` audiences, but
  does not have `orgmemory-web`;
- the exchanged token has only `orgmemory-web`, has
  `azp=orgmemory-mcp`, and retains `assets:read`.

Spring Security models a Keycloak access token as `Jwt` and therefore defaults
RFC 8693 `subject_token_type` to `urn:ietf:params:oauth:token-type:jwt`.
OrgMemory replaces that single parameter with
`urn:ietf:params:oauth:token-type:access_token`, which is the token type
Keycloak standard exchange accepts for its access tokens. Do not append a
second `subject_token_type`: Keycloak rejects an ambiguous multi-value request
with `invalid_request`.

Rotate `ORGMEMORY_MCP_OIDC_CLIENT_SECRET` independently from the web client
secret. Never put either value in a document, issue, or log.

Discovery is:

```text
GET https://om.kl3in.tech/.well-known/oauth-protected-resource/mcp
```

An unauthenticated `/mcp` request returns `401` and a `WWW-Authenticate`
challenge pointing to that metadata URL.

Authenticated users can open `/connect` in OrgMemory for the canonical server
URL and client-specific steps. This page contains no client secret and does not
replace the OAuth consent screen.

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
