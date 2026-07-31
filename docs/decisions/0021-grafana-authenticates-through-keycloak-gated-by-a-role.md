# 0021 — Grafana authenticates through Keycloak, gated by a role

Status: accepted, 2026-07-30.

## Context

The observability increment assumed Grafana would be published through Nginx
Proxy Manager with Keycloak OIDC, the way every other OrgMemory surface is. That
assumption was carried, not decided, so it was re-opened.

Inspecting the production host produced an argument against publishing at all.
`orgmemory-keycloak-1` runs with `KC_DB_URL=jdbc:postgresql://postgres:5432/keycloak`
on the `shared-infra` network — the same PostgreSQL container the product's API
and worker use. A published, Keycloak-protected Grafana therefore depends on:

```
Grafana → Keycloak → shared PostgreSQL ← OrgMemory API/worker
```

A database incident is one of the most likely production failures and precisely
what you open Grafana to understand. On that path it takes out the product,
Keycloak, and the login to the tool you were reaching for.

The counter-argument is that the alternative was not "no authentication risk"
but a different one. Reached only over an SSH tunnel, a single local
administrator password is adequate. Published at a public hostname, it is one
password on a public login page. The owner wanted it published, which makes an
identity provider the stronger option rather than the weaker one.

## Decision

Grafana is published at `grafana.zeromail.vn` and authenticates through Keycloak
on the existing `orgmemory` realm, with two constraints that are the reason the
realm choice is acceptable.

**Access is gated by a role, not by membership.**
`GF_AUTH_GENERIC_OAUTH_ROLE_ATTRIBUTE_STRICT=true` with a role attribute path of
`contains(realm_access.roles[*], 'observability') && 'Admin'`. Without the strict
flag, a user Keycloak authenticates who maps to no Grafana role is admitted as a
Viewer — and Grafana shows telemetry spanning every organization. With it, the
same user is refused.

This is not hypothetical. The `orgmemory` realm already holds thirty-five
accounts, thirty-two of them seeded product users. The role is held by one.

**The local administrator stays enabled as break-glass.**
`GF_AUTH_DISABLE_LOGIN_FORM` remains false. The incident most likely to send an
operator to Grafana is the one most likely to have taken the identity provider
with it, and disabling the form would make Grafana unreachable exactly then.

Prometheus and Alloy bind loopback. Loki and Tempo publish no host port at all.
Only Grafana joins the proxy network.

## What would change this

The product realm is the tenant directory, and Grafana displays cross-tenant
telemetry. The role gate makes a customer account's access a deliberate grant
rather than a default, which is enough while the realm's non-seed accounts are
all operators.

Move Grafana to a separate realm when that stops being true — when someone who
should never see cross-tenant telemetry has an account in `orgmemory` that a
mistaken role grant could reach. Migrating later costs re-creating a handful of
operator accounts; migrating after a leak costs more.

## Rejected alternatives

**Loopback and an SSH tunnel only, no Keycloak.** What this decision originally
recorded. It removes the dependency chain entirely and keeps working when
everything else is down. Rejected by the owner in favour of a reachable URL; the
break-glass login preserves most of the property it was protecting.

**A separate `ops` realm now.** Structural rather than conventional: a product
user would not have an account to grant a role to. Rejected as more identity
infrastructure than a proof of concept with no external users needs, with the
migration trigger recorded above instead.

**Published with the local administrator alone.** One password on a public login
page, and no revocation path other than changing it for everyone. Weaker than
both alternatives above.
