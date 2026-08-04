# 0032 — Conversation Model Selection Is Bound To Administrator Route Authority

## Status

Accepted on 2026-08-04. Supersedes decision 0006 only where it deferred
per-user model selection.

## Context

The organization AI control plane chooses one effective gateway and default
model per Assistant workload. A composer model picker is useful only when it
changes the actual route, but accepting a browser gateway/model pair would let
an actor bypass administrator cost, capability, and provider policy. Model
choice must also coexist with bounded conversation memory and remain invalid
after an administrator changes and later recreates an apparently identical
route.

## Decision

An organization administrator may activate additional chat model identifiers
only on the organization profile currently routed to `ASSISTANT_CHAT`. A user
selects an opaque activation UUID, never a gateway route. The current route
model remains a synthetic default.

Core resolves the activation into a sealed Assistant route authority bound to
the organization, gateway profile, route override identity and version, and
catalog activation. The provider adapter revalidates that authority immediately
before constructing a conversation-memory model client. The generic route
registry remains strict and no other workload receives the Assistant exception.

Catalog activations are soft-disabled. Re-enabling the same textual model
creates a new activation identity, so stale conversation state cannot revive.
Deployment defaults remain read-only and create no tenant profile. Alternate
models are not selectable while the effective route carries a model-specific
reasoning option.

The browser receives safe display data only. Catalog changes and effective
route identity are audited without prompts, completions, credentials, base
URLs, or transcript content.

## Consequences

Conversation model choice is real but cannot cross the administrator-selected
gateway. Route changes apply to the next request, including A -> B -> A changes.
An in-flight turn may complete under the exact authority revalidated when its
cold stream starts; later turns must resolve again.

Catalog activation, conversation selection, and turn creation require explicit
locking/version semantics and database-level organization ownership. Provider
discovery is an administrator aid, not an ordinary-user authorization source.

The cheaper read-only model indicator and the more permissive Northstar-style
gateway/model request pair are rejected: the former does not provide choice,
and the latter does not meet OrgMemory's tenant and control-plane promise.
