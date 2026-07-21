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

The first enterprise slice uses deployment secret references/admin configuration.
Encrypted runtime credential CRUD, dynamic model catalogs, and dynamic tool
discovery are deferred until a real multi-provider/tool-scale requirement exists.
