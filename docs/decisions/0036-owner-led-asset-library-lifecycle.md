# 0036 — Use an owner-led Asset Library lifecycle

Status: Accepted

Date: 2026-08-10

Supersedes: [0022](0022-skill-publication-is-direct-by-default-and-reviewable.md)
for new Asset Library contribution and publication UX. Existing reviewed
Releases and open review records remain compatible history.

## Decision

Every new Asset has exactly one human owner. The primary lifecycle is a mutable
private working copy, an immutable direct Release, optional Viewer or Editor
sharing, and asset-level withdrawal. Viewer sharing exposes released content
only. Editors may change the working copy but cannot publish, share, transfer
ownership, or withdraw. The owner may perform those ordinary lifecycle actions.

The first Viewer share creates the first immutable direct Release in the same
database transaction as canonical sharing intent. OpenFGA projection remains a
separate fail-closed convergence step. Organization-wide sharing is Viewer-only
and uses `organization:<id>#member`; group sharing uses `group:<id>#member`.

Ownership transfer is locked, replaces the sole Owner relationship, and demotes
the previous owner to Editor. Administrators receive separate vacancy recovery
and emergency-withdraw permissions; administration does not imply ordinary
authoring or content visibility.

Skills add an actor-scoped Enabled preference. Runtime discovery and activation
require both live Asset visibility and Enabled state. Enabling never changes
sharing or Release bytes.

## Rationale

Review is too expensive to make the default POC contribution gate, while
mutable published content would make agent consumption irreproducible. One
accountable owner plus immutable Releases preserves a small mental model and
exact execution evidence. Viewer-only released DTOs prevent wider Catalog
audiences from receiving Drafts, comments, or role principals.

## Rejected alternatives

- Mandatory review for every publication: excessive staffing and expertise
  requirements for current adoption.
- Publish mutable working copies: breaks exact Release pins and auditability.
- Backup-owner as a required lifecycle role: adds administration without
  solving ordinary POC contribution; owner vacancy uses explicit recovery.
- Organization Editor sharing: exceeds the bounded company-wide read use case.
