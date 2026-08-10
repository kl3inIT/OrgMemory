---
packages:
  orgmemory: minor
subject: Brand the OrgMemory sign-in experience
---

## Features

- Give OrgMemory sign-in and password recovery a responsive, accessible visual
  theme aligned with the product's light and dark design tokens.

## Operations

- Package the Keycloak login theme in the immutable Keycloak image and reconcile
  existing realms with rollback-safe restoration of their prior theme.
- Deploy production images by their verified manifest digests. Keycloak rollback
  now fails closed if the previous realm theme cannot be restored and verified,
  rather than starting a potentially incompatible previous image.
