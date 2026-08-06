---
packages:
  orgmemory: patch
subject: Put production services on one shared Docker DNS fabric
---

## Fixes

OrgMemory, documentation, and observability services now join the same external
Docker DNS network while retaining their existing private and proxy networks.
Cross-stack diagnostics and integrations can use stable service names instead of
container IP addresses without publishing additional host ports.
