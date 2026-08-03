# AI Model Control Plane Spec

Source: `core/src/main/java/com/orgmemory/core/ai`,
`integrations/ai-model-gateways`, `apps/api/.../AdminAiModelController`,
API/worker `application*.yml`, and
`apps/web/src/features/admin/components/admin-language-models-page.tsx`.

Reconciled: `2026-08-02-rag-workload-routing-luna (24c31aea)`.

## Current Behavior

Organization administrators can connect, test, update, credential-rotate, and
disable chat gateway profiles without restarting the application. Provider
cards are presentation presets; runtime dispatch uses one of two implemented
protocols:

- `OPENAI_COMPATIBLE`: OpenAI, 9Router, OpenRouter, LiteLLM, Ollama, and
  operator-allowlisted custom endpoints;
- `ANTHROPIC_MESSAGES`: direct Anthropic through the native Spring AI adapter.

The administration UI gives each named provider its verified brand mark and
uses a neutral endpoint mark for the unbranded OpenAI-compatible preset. The
setup dialog groups encrypted credentials, endpoint policy, connection testing,
discovered models, and the read-only organization governance boundary. Model
discovery reflects the live provider response; it does not imply a persisted
model allowlist or let a user bypass explicit workload routes.

Credentials are accepted only as redacted request values, encrypted with the
shared AES-GCM `SecretCipher`, and never returned. Every profile and route is
organization-scoped in both service lookup and database foreign keys.
Credential rotation advances a monotonic runtime revision so new calls cannot
reuse a cached client built with an old secret.

Provider-neutral routing and model caching live in
`integrations.ai.gateway`. Protocol-specific SDK construction is isolated in
`gateway.openai` and `gateway.anthropic`. The dispatcher rejects duplicate
factories during startup and fails closed if a route selects an unimplemented
protocol.

An OpenAI-compatible gateway must explicitly declare support before a route may
set OpenAI `reasoning_effort`. Supported values are `none`, `low`, `medium`,
`high`, `xhigh`, and `max`; absence omits the field and preserves the provider
default. Native Anthropic and undeclared compatible gateways reject the option.
A capability cannot be disabled while one of its routes still uses an explicit
value.

Assistant, keyword-planning, and prompt-execution routes are explicit
organization overrides.
Administrators can restore the deployment default by clearing an override.
Gateway-key collisions never substitute an organization credential for a
deployment route.

Deployment gateways use binder-safe nested objects. A production profile may
contribute only a managed credential while retaining the endpoint,
capabilities, timeout, and feature flags defined in base configuration; API and
worker verify this base-plus-profile binding against their real configuration
files.

Fixed providers use fixed HTTPS endpoints. Custom endpoints require an exact
origin in `ORGMEMORY_AI_ALLOWED_CUSTOM_ORIGINS`; redirects, response size,
timeouts, model count, and provider errors are bounded during connection tests.
Spring Boot 4.1 `HttpClientSettings` and `InetAddressFilter` harden the
Boot-managed probe client. Spring AI provider SDK traffic is additionally
bounded by origin policy and production egress controls.

Assistant, Keyword Planning, and Prompt Execution routes are editable and apply
to subsequent organization requests. Deployment routes remain visible defaults
only when no organization override exists. An explicit override fails closed
and is never silently replaced by a deployment default. Graph Extraction is
visible but read-only, defaults independently to `gpt-5.4-mini`, and changes
only through deployment configuration. A Graph route change affects newly
enqueued jobs; it neither starts reindexing nor changes queued/completed jobs.

The fixed live evaluation approved `gpt-5.6-luna` with reasoning `none` for
Keyword Planning but rejected it for Graph Extraction. Graph therefore retains
`gpt-5.4-mini`; Answer retains `gpt-5.6-sol`. ZM production and the production
Compose defaults now use that evaluated split. The general development
configuration remains capability-off by default because an arbitrary custom
OpenAI-compatible endpoint has not proved `reasoning_effort` support.

Index Settings is a separate read-only surface. The embedding provider, model,
dimensions, and cosine metric cannot be mutated through the chat control plane;
a geometry change requires a versioned embedding profile and reindex lifecycle.

Both administration surfaces require OpenFGA `organization#can_manage_ai`.
Production writes and pins the repository authorization model before a release
whose model digest changed starts application containers. A legacy deployment
with no stored digest writes the current model once. A failed release restores
the previous model ID with its previous image set.

## Source Modules

- `core.ai`
- `integrations.ai-model-gateways`
- `apps.api.admin`
- `web.features.admin`

## Related Decisions

- [0006](../../decisions/0006-ai-tasks-route-through-provider-adapters.md)
- [0008](../../decisions/0008-worker-owns-ingestion-and-derived-indexes.md)
- [0017](../../decisions/0017-pin-openfga-models-to-product-releases.md)
