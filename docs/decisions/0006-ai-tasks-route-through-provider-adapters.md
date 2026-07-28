# 0006 — AI Tasks Route Through Provider Adapters

## Status

Accepted on 2026-07-20.

## Context

Model choice, credentials, chat, embedding, extraction, and reranking should not
spread provider conditionals through domain features.

## Decision

Core identifies an AI task and required capability through provider-neutral
ports. Integration modules implement verified protocols. Deployables resolve
routes at call time. Heavy parsing/extraction/embedding runs in worker. The
in-app agent is implemented first; MCP later exposes the same permission-aware
domain tools.

## Consequences

OrgMemory learns Northstar's route/adapter/tool-reuse pattern without copying
its personal-assistant feature set. A missing provider is explicit for
authoritative production work; demo fallback cannot publish trusted knowledge.

Deployment routes remain the explicit baseline. Organization administrators may
add organization-scoped chat gateway profiles, encrypted write-only credentials,
and explicit Assistant/Prompt route overrides at runtime. These overrides are
resolved at call time and fail closed: an unavailable selected gateway never
silently falls through to the deployment route.

Provider presentation is separate from wire protocol. OpenAI, 9Router,
OpenRouter, LiteLLM, Ollama, and custom endpoints share the verified
OpenAI-compatible adapter; Anthropic uses its native Messages adapter.
Embedding geometry remains deployment-managed because changing it is an index
lifecycle operation, not a chat-routing setting. Per-user selection, automatic
failover, dynamic tools, and mutable embedding profiles remain deferred.
