---
packages:
  orgmemory: patch
subject: Enforce typed Knowledge Space audiences
---

## Features

Let administrators choose organization, department, or restricted custom
audiences for each Knowledge Space. Managed audiences cannot be silently
widened, custom viewers fail closed across PostgreSQL and OpenFGA, and the
administration UI explains policy drift without exposing internal identifiers.
