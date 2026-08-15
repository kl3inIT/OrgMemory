---
packages:
  orgmemory: patch
subject: Restyle the Keycloak OAuth consent page
---

## Fixes

The OAuth consent page shown when an MCP client such as Claude requests
access now renders with the OrgMemory login theme: centered card on the
themed canvas in light and dark mode, a readable scope list, and a
consistent full-width accept/cancel pair. Previously the page lost the
theme layout entirely and rendered as an unstyled card in the top-left
corner.
