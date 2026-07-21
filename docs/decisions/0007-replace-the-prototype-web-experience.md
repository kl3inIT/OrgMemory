# 0007 — Replace The Prototype Web Experience

## Status

Accepted on 2026-07-20.

## Context

The existing dashboard/registry/analytics page set demonstrated the thesis but
does not center secure memory, evidence review, or the in-app agent. Preserving
page-level parity would constrain the product around prototype information
architecture.

## Decision

Replace most web composition with an agent-first workspace. Preserve only useful
API clients, auth plumbing, theme tokens, and proven generic components. The new
surface prioritizes ask/search, citations/evidence, private candidate review,
publish/approval, sources/devices, permission health, and admin operations.

## Consequences

Old route/page behavior is not a compatibility requirement. Light and dark
themes, accessibility, explicit waiting/error states, and maintained component
libraries remain requirements.
