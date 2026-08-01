---
packages:
  orgmemory: patch
subject: Route ACL reads through an owned query boundary
---

# Route ACL reads through an owned query boundary

## Improvements

Retrieval and Graph now read source ACL facts through `SourceAclQuery` and
immutable ACL-owned references instead of consuming the ACL repository, JPA
entity, or a Space-owned projection type. This follows the existing
[Claude Fable 5 architecture verdict](../docs/increments/active/2026-07-31-spring-modulith-package-refactor/challenge-verdict.md)
to replace direct implementation edges before closing nested modules.
