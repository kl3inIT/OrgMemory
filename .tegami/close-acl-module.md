---
packages:
  orgmemory: patch
subject: Close the Knowledge ACL module boundary
---

# Close the Knowledge ACL module boundary

## Improvements

Knowledge ACL now enforces a closed public API with an explicit dependency
allowlist limited to `organization`, `permission`, `shared`, and
`shared::error`. This completes the independently reviewed ACL closure after
its sibling implementation edges were replaced with owned APIs.
