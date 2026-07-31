---
packages:
  orgmemory: patch
subject: Consistent Skill package metadata validation across surfaces
---

## Fixes

Skill package metadata keys are now validated identically by the CLI and the
server, closing a drift where whitespace-only keys could pass one surface and
fail another. MCP gateway error handling and CLI package safety helpers were
consolidated so the same rules live in one place per app.
