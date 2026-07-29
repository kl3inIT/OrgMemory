# AI Model Control Plane Spec

Source: `core/src/main/java/com/orgmemory/core/ai`,
`integrations/ai-openai-compatible`, `apps/api/.../AdminAiModelController`, and
`apps/web/src/features/admin/components/admin-language-models-page.tsx`.

Reconciled: `2026-07-29-openfga-model-rollout`.

## Current Behavior

Organization administrators can connect, test, update, credential-rotate, and
disable chat gateway profiles without restarting the application. Provider
cards are presentation presets; runtime dispatch uses one of two implemented
protocols:

- `OPENAI_COMPATIBLE`: OpenAI, 9Router, OpenRouter, LiteLLM, Ollama, and
  operator-allowlisted custom endpoints;
- `ANTHROPIC_MESSAGES`: direct Anthropic through the native Spring AI adapter.

Credentials are accepted only as redacted request values, encrypted with the
shared AES-GCM `SecretCipher`, and never returned. Every profile and route is
organization-scoped in both service lookup and database foreign keys.
Credential rotation advances a monotonic runtime revision so new calls cannot
reuse a cached client built with an old secret.

Assistant and prompt-execution routes are explicit organization overrides.
Administrators can restore the deployment default by clearing an override.
Gateway-key collisions never substitute an organization credential for a
deployment route.

Fixed providers use fixed HTTPS endpoints. Custom endpoints require an exact
origin in `ORGMEMORY_AI_ALLOWED_CUSTOM_ORIGINS`; redirects, response size,
timeouts, model count, and provider errors are bounded during connection tests.
Spring Boot 4.1 `HttpClientSettings` and `InetAddressFilter` harden the
Boot-managed probe client. Spring AI provider SDK traffic is additionally
bounded by origin policy and production egress controls.

Assistant and Prompt execution routes are editable. Deployment routes remain
visible defaults only when no organization override exists. An explicit
override fails closed and is never silently replaced by a deployment default.
Keyword planning and graph extraction stay deployment-managed.

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
- `integrations.ai-openai-compatible`
- `apps.api.admin`
- `web.features.admin`

## Related Decisions

- [0006](../../decisions/0006-ai-tasks-route-through-provider-adapters.md)
- [0008](../../decisions/0008-worker-owns-ingestion-and-derived-indexes.md)
- [0017](../../decisions/0017-pin-openfga-models-to-product-releases.md)
