# AI Model Control Plane Spec

Source: `core/src/main/java/com/orgmemory/core/ai`,
`integrations/ai-model-gateways`, `apps/api/.../AdminAiModelController`,
API/worker `application*.yml`, and
`apps/web/src/features/admin/components/admin-language-models-page.tsx`.

Reconciled: `2026-08-06-assistant-catalog-none-reasoning`.

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
discovery reflects the live provider response and can seed an administrator's
explicit Assistant catalog; discovery alone grants nothing and never lets a
user bypass explicit workload routes.

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

The active organization Assistant gateway may own up to 50 additional chat
model activations. Catalog replacement takes a pessimistic profile lock,
excludes the default route model, soft-disables removed or renamed entries, and
creates a new immutable activation UUID when a model is later re-enabled. Each
row repeats organization ownership in its profile and actor foreign keys; only
one active row may exist for an organization/profile/model tuple. Catalog
mutation is unavailable on deployment defaults, inactive gateways, or Answer
routes with explicit reasoning effort other than `none`. Provider-default and
explicit `none` may publish additional choices. Every selected activation
inherits that server-owned route policy; the composer sends only the opaque
activation UUID and cannot submit a raw model ID or reasoning value. Higher
reasoning policies suppress alternate choices because catalog models have not
been capability-validated for them. Catalog changes are audited without model
prompts, output, endpoints, or credentials.

Deployment gateways use binder-safe nested objects. A production profile may
contribute only a managed credential while retaining the endpoint,
capabilities, timeout, and feature flags defined in base configuration; API and
worker verify this base-plus-profile binding against their real configuration
files.
Nested immutable route records that also expose convenience constructors mark
their canonical constructor explicitly, so Spring Boot binds the independent
Answer, Keyword Planning, Graph Extraction, and embedding deployment routes
instead of falling back to the shared Java defaults.

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
`gpt-5.4-mini`; Answer retains `gpt-5.6-sol` with explicit reasoning `none` so
its fixed function tools remain valid on OpenAI Chat Completions. ZM production
and the production Compose defaults use that route split. The general
development configuration remains capability-off and leaves route effort empty
by default because an arbitrary custom OpenAI-compatible endpoint has not
proved `reasoning_effort` support.

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
- [0032](../../decisions/0032-conversation-model-selection-is-bound-to-admin-route-authority.md)
