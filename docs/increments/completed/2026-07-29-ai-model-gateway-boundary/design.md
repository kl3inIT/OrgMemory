# AI Model Gateway Boundary Design

## Intent

Make the source layout match the shipped multi-provider runtime. The existing
module and package were still named `ai-openai-compatible` even though the same
adapter constructed native Anthropic models.

## Constraints

- Preserve the public core ports, persisted profile schema, route semantics,
  model cache invalidation, and provider probe behavior.
- Keep embedding routing separate from chat-model construction.
- Continue to fail closed when a configured protocol has no implementation.
- Do not create one Gradle module per provider without an independent
  deployment or dependency boundary.

## Architecture Challenge

### Proposal

Use one `integrations/ai-model-gateways` module with a provider-neutral
`gateway` package and one subpackage per implemented wire protocol. A small
factory registry selects the native Spring AI implementation by
`AiGatewayProtocol`; callers depend only on `SpringAiChatModelProvider`.

### Strongest counterargument

Separate Gradle modules for OpenAI-compatible and Anthropic code would provide
stronger compile-time isolation and could avoid loading an unused provider SDK.

### Repository evidence

- API and worker share the same route registry, profile-version cache key,
  credential lifecycle, metrics, and observations.
- GraphRAG callers previously depended directly on
  `OpenAiCompatibleChatModelProvider`, even when a route could use the native
  Anthropic protocol.
- The provider SDKs are packaged into the same API and worker deployments; no
  independent release or runtime boundary exists today.

### Final choice

Keep one Gradle module and split provider-specific model construction behind
`SpringAiChatModelFactory`. Put OpenAI-compatible and Anthropic builders in
separate subpackages, and make dispatcher startup reject duplicate factories
while invocation rejects a missing factory.

### Rejected alternative

One module per provider is deferred until providers can be deployed, versioned,
or dependency-selected independently. A cosmetic module rename without
extracting the provider builders is also rejected.

The configured architecture reviewer was unavailable because its quota was
exhausted. The project owner explicitly directed implementation to continue in
this session.

## Resulting Layout

```text
integrations/ai-model-gateways
└── .../integrations/ai/gateway
    ├── SpringAiChatModelProvider.java
    ├── SpringAiChatModelFactory.java
    ├── SpringAiChatModelFactories.java
    ├── openai/OpenAiCompatibleChatModelFactory.java
    └── anthropic/AnthropicMessagesChatModelFactory.java
```
