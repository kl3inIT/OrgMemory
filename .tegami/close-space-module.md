---
packages:
  orgmemory: patch
subject: Close the Knowledge Space module boundary
---

# Close the Knowledge Space module boundary

## Improvements

Knowledge Space now enforces a closed public API with an explicit dependency
allowlist limited to `authorization`, `knowledge.sourceledger`, `organization`,
`permission`, `shared`, and `shared::error`. This completes the independently
reviewed Space closure after sibling repository access was replaced with an
owned query API.
