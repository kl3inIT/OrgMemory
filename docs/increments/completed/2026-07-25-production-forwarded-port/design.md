# Production Forwarded Port

## Problem

The first public browser smoke reached `https://om.kl3in.tech`, but the browser
login entry point redirected to
`https://om.kl3in.tech:8080/oauth2/authorization/keycloak`.

Nginx Proxy Manager terminates TLS and forwards `X-Forwarded-Proto: https`, but
does not send `X-Forwarded-Port`. The web proxy currently falls back to its
private listener port `8080`, which leaks the internal port into Spring
Security's public redirect.

## Decision

The web proxy derives the missing forwarded port from the trusted forwarded
scheme:

- explicit `X-Forwarded-Port` remains authoritative;
- missing port plus `https` becomes `443`;
- missing port plus `http` becomes `80`;
- an unknown or missing forwarded scheme falls back to the local server port.

Only Nginx Proxy Manager can reach the web container through the external proxy
network, and it overwrites the forwarded scheme. No NPM custom location is
required.

## Verification

A Docker-backed regression starts the exact unprivileged Nginx runtime used by
the web image and a header-echo upstream. It proves HTTPS/HTTP defaults and
preservation of an explicit non-standard port. Production browser verification
must then prove the authorization redirect uses port 443 and the OrgMemory
Keycloak realm.
