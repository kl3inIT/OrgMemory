---
packages:
  orgmemory: patch
subject: Accurate review actions in the governance workspace
---

# Accurate review actions in the governance workspace

## Improvements

The governance workspace now shows exactly the review actions the server
permits: a revision author can request changes or reject, and only approval
is withheld, matching the enforcement rule. Action availability comes from
the server instead of being derived in the browser, so what you see always
matches what you may do. Authorization rechecks behind search, citations,
and graph answers now share one hardened implementation with their existing
per-surface behavior preserved.
