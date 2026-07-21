# 0002 — One Domain, Three Deployables

## Status

Accepted on 2026-07-20. This is the target dependency boundary; the current
prototype still contains ranking/fallback behavior in an API controller that
must move behind core use cases during the agent slice.

## Context

OrgMemory needs synchronous delivery, durable background work, and agent access
without duplicating authorization or knowledge rules.

## Decision

Keep domain/application behavior in the Spring Modulith `core`. `apps/api`,
`apps/worker`, and `apps/mcp` are thin deployables. Domain packages remain logical
modules; separate Gradle projects are reserved for deployables, reusable engines,
or external adapters.

## Consequences

The Northstar `integrations/*` pattern is adopted for real provider boundaries,
not copied for every package. API, worker, and MCP may depend on core and selected
adapters; core never depends on a delivery app.
