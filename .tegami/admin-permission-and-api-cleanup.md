---
packages:
  orgmemory: patch
subject: AI management permission visible in admin, leaner API surface
---

## Improvements

The organization permission catalog now includes the AI management permission,
so admin screens can show and explain who may manage AI gateways. The
redundant identity endpoint was removed in favor of the session endpoint, and
request handling avoids repeated per-request work in SCIM limits and worker
scheduling configuration.
