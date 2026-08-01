---
packages:
  orgmemory: patch
subject: Close the Knowledge ACL module boundary
---

# Close the Knowledge ACL module boundary

## Improvements

Knowledge ACL now enforces a closed public API with an explicit dependency
allowlist limited to `organization`, `permission`, `shared`, and
`shared::error`. This completes the ACL closure required by the existing
[Claude Fable 5 architecture verdict](../docs/increments/active/2026-07-31-spring-modulith-package-refactor/challenge-verdict.md)
after its sibling implementation edges were replaced with owned APIs.
