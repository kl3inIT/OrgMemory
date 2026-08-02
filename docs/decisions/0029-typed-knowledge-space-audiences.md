# 0029 — Knowledge Spaces persist typed audience policies

Status: accepted (2026-08-02)

## Decision

Every Knowledge Space persists one immutable-at-creation audience mode and a
policy version:

- `ORGANIZATION` has no owning department and projects the organization member
  set as its built-in viewer audience.
- `DEPARTMENT` has exactly one owning department and projects that department's
  member set as its built-in viewer audience.
- `RESTRICTED_CUSTOM` has no owning department, starts with no viewers, and
  admits explicit user or department viewer grants recorded in the PostgreSQL
  audience ledger as well as OpenFGA.

The built-in audience of an organization or department Space cannot be removed
or widened by the ordinary grant API. A change of mode is not an ordinary
update: it requires a later governed transition with an impact preview,
immutable preview hash, reason, independent approval, reversible tuple diff,
audit evidence, verification, and bounded rollback.

OpenFGA remains the relationship engine, but tuples are not the complete Space
business definition. Runtime content visibility intersects the persisted mode
with OpenFGA eligibility. Restricted-custom viewing additionally requires a
matching PostgreSQL audience-ledger row, so a stray tuple grants nothing. Source
ACL, classification, tenant, lifecycle, and retrieval gates remain independent
hard ceilings.

`can_manage_acl`, `can_publish`, `can_create_asset`, and `can_view` are
independent permissions. Holding an operational relation does not implicitly
grant content read.

## Why

A nullable department id and an arbitrary set of tuples cannot distinguish a
deliberate collaboration policy from drift. In particular, a department-tagged
Space could previously receive an organization-wide viewer tuple, while an
administrator or contributor automatically became a reader through computed
permission nesting. That made the UI's department promise unenforceable and
made it impossible to explain which rule was authoritative.

Typed modes make the promise explicit, database-validatable, API-validatable,
and enforceable again at read time. Space authorization still has a purpose:
it defines the intended audience and operational roles, while Source ACL says
which documents inside that audience each person may actually read.

## Strongest counterargument

OpenFGA already expresses flexible relationships, so mode state duplicates
some tuple meaning and introduces a cross-store projection to keep aligned.
Tuple-only state is simpler when every tuple is correct. It cannot, however,
identify an invalid tuple as drift, preserve a durable department-only promise,
or fail closed while a tuple migration is incomplete.

## Rejected alternatives

- Department metadata as a display-only label; it creates a security promise
  the backend does not enforce.
- Allowing organization-wide viewer grants on every mode; that collapses the
  three modes back into one mutable tuple set.
- Letting operational roles imply read; administration, publication, authoring,
  and content visibility have different duties and separation-of-duty needs.
- An ordinary mode update endpoint; widening and narrowing have different
  cross-store failure modes and need a governed transition workflow.

Independent challenge record:
`docs/increments/active/2026-08-02-effective-access-inspector/design.md`.
