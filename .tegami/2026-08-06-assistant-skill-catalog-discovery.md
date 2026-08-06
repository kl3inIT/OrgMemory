---
packages:
  orgmemory: patch
subject: Match Assistant requests against authorized Skills
---

## Fixes

The Assistant now sees the current user's authorized Skill names and
descriptions before choosing a workflow, so natural-language requests can
activate the matching exact release without inventing catalog search terms.
Unavailable Skills remain hidden, and activation still rechecks access.
