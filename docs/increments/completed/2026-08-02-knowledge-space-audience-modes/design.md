# Typed Knowledge Space Audience Modes — Design

## Intent

Turn the independently challenged Space audience verdict into enforced current
behavior. Administrators choose a durable audience promise when creating a
Space; ordinary grant operations cannot contradict it; operational roles do not
silently become readers; and canonical evidence retrieval rejects relationship
drift that conflicts with the persisted mode.

## Binding decision

[ADR 0029](../../../decisions/0029-typed-knowledge-space-audiences.md) owns the
durable decision. This increment does not add a mode-transition workflow. Modes
are immutable at creation until the governed transition described by the ADR is
implemented.

The architecture challenge is not rerun because this implementation is the
deferred scope of the two-architect debate and fresh-judge verdict already
recorded in the effective-access inspector design. The strongest counterargument
and rejected tuple-only alternative are consolidated into ADR 0029.

## Data and projection

`knowledge_spaces` gains non-null `audience_mode` and positive
`audience_version`. Existing rows backfill deterministically: a row with
`department_id` becomes `DEPARTMENT`; a row without one becomes
`ORGANIZATION`. A database constraint couples department presence to mode.
Missing or conflicting viewer projection is reported as drift and stays
fail-closed; this increment does not silently widen a historical Space.

`knowledge_space_custom_viewer_grants` is the tenant-scoped PostgreSQL authority
for restricted-custom user and department viewers. OpenFGA remains necessary,
but not sufficient, for those reads. The database row is flushed before the
relationship write: a failed relationship write rolls it back, while an
ambiguous relationship success followed by a failed database commit leaves an
inert extra tuple rather than excess access. Revocation is the symmetric narrow
failure mode.

New organization and department Spaces write their built-in viewer tuple in the
same relationship write request as their structural and creator-accountability
tuples. Restricted custom Spaces write no viewer tuple. Existing OpenFGA data is
reported as stored; invalid audience tuples are not treated as effective by the
runtime mode gate.

## Enforcement

- Create validates the mode/department combination before persistence.
- Ordinary viewer mutation is locked for built-in modes.
- Restricted custom viewers may be users or departments, but never the whole
  organization. Organization roles remain available for contributor/reviewer
  operations only when the OpenFGA model accepts their exact projection.
- Operational grants continue to use the model's subject restrictions and are
  independent from viewer eligibility.
- Visible Space discovery and canonical evidence retrieval intersect OpenFGA
  results with the persisted mode. Department mode requires the actor's current
  persisted department to equal the Space owner.
- Source ACL and all later content gates remain unchanged.

## UI

Creation presents three decision-oriented audience choices with consequences,
not raw ids. Each Space row shows a mode badge and resolved department name.
Built-in audience grants are labelled as policy-managed and cannot be revoked.
Restricted custom Spaces explain that they start closed. The grant form offers
viewer changes only where the mode allows them, while operational roles remain
available.

## Rollout and rollback

The API-owned Flyway migration runs before binaries that require the new
columns. Deployment rolls a new immutable OpenFGA model and pins its id through
the existing rollout workflow. Rollback requires the previous application image
and model id; the additive columns remain compatible with the previous binary.
No mode transition or destructive tuple cleanup occurs in this increment.
