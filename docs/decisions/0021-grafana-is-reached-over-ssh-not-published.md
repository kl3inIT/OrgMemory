# 0021 — Grafana is reached over SSH, not published behind Keycloak

Status: accepted, 2026-07-30.

## Context

The observability increment assumed Grafana would be published through Nginx
Proxy Manager with Keycloak OIDC, the way every other OrgMemory surface is. That
assumption was carried, not decided.

Inspecting the production host answered it. `orgmemory-keycloak-1` runs with
`KC_DB_URL=jdbc:postgresql://postgres:5432/keycloak` on the `shared-infra`
network — the same PostgreSQL container the product's API and worker use. The
dependency chain a published Grafana would have is therefore:

```
Grafana → Keycloak → shared PostgreSQL ← OrgMemory API/worker
```

A database incident is one of the most likely production failures, and it is
precisely the kind you open Grafana to understand. On that path it would take out
the product, take out Keycloak, and take out the login to the tool you were
reaching for.

## Decision

Grafana binds `127.0.0.1` and is reached over an SSH tunnel. It authenticates
against a local administrator account, not Keycloak. Anonymous access is off and
sign-up is off.

Prometheus binds loopback for the same reason. Loki and Tempo publish no host
port at all; Grafana reaches them on the internal network.

`ssh zm` is already the operator credential for this host, so this adds nothing
new to manage, and it keeps working when the identity provider does not.

## If OIDC is wanted later

Two constraints, both structural rather than conventional:

- **Never the product realm.** Grafana displays telemetry spanning every
  organization. OrgMemory's realm is the tenant directory. Putting an
  operator tool's clients in the customer realm makes "a customer cannot read
  cross-tenant telemetry" a role mapping rather than a boundary.
- **Keep the local administrator as break-glass**, or the coupling this decision
  exists to avoid comes back the first time the identity provider is unavailable.

## Rejected alternatives

**Publish through Nginx Proxy Manager with Keycloak OIDC on the product realm.**
What the increment assumed. Rejected on the dependency chain above, and on
audience: the realm holds product users.

**Same Keycloak instance, separate `ops` realm.** Removes the audience problem
and keeps the availability one — Keycloak still depends on the same database.
Reasonable once there is an operations team large enough that shared SSH access
is the worse risk. Not now.

**Anonymous Grafana behind the tunnel.** One fewer credential, and it makes
anyone who reaches the host's loopback an administrator. The tunnel is a network
control, not an authentication one.
