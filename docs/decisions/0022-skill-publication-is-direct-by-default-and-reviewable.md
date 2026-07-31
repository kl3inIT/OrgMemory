# 0022 — Skill publication is direct by default and reviewable

Status: accepted, 2026-07-31.

## Context

The shared Asset lifecycle originally required every Skill author to create a
Draft, submit an immutable Revision, find a reviewer, wait for approval, and
then find a publisher. That workflow is appropriate for controlled instructions
and Packs, but it makes ordinary Skill contribution depend on roles that many
organizations have not established.

Claude-style organization sharing provides the simpler product expectation:
an accountable author can make a Skill available to an already-authorized
audience. This is not public marketplace publication and it does not grant the
Skill any execution authority.

## Decision

An Asset `OWNER`, `BACKUP_OWNER`, `STEWARD`, or organization administrator may
publish a Skill Draft directly when the actor can still create Assets in the
parent Knowledge Space. Core checks the dedicated OpenFGA
`can_publish_skill` permission and atomically creates the immutable Revision,
Release, package-reference chain, availability event, and audit evidence.

Every release persists `publication_mode=REVIEWED|DIRECT`. Governance and
delivery projections, including the install manifest, expose that provenance.
Direct publication means the package passed structural validation; it does not
claim independent content, malware, or domain review.

The reviewed API remains available. Once a Skill has an active review case,
direct publication is blocked until the review is resolved or cancelled. Every
non-Skill Asset keeps its existing mandatory reviewed-publication path.

## Why this boundary

Reusing `can_edit` would allow an `EDITOR` to publish and would lower the parent
Space gate from owner-class `can_create_asset` to the broader authoring role.
Reusing `can_publish` would preserve a publisher bottleneck and fail the purpose
of simplifying contribution. A dedicated relation keeps the product simple
without silently broadening another permission.

## Rejected alternatives

**Mandatory manager or expert review for every Skill.** Many organizations do
not yet have a meaningful Skill expert or publication board. Making that role a
prerequisite creates process before the contribution has demonstrated risk or
reuse.

**Direct publication through `can_edit`.** Too broad. It turns an authoring
permission into release authority and weakens the parent Space invariant.

**A separate Skill marketplace lifecycle.** It duplicates Asset identity,
authorization, release history, and withdrawal. Skills remain a filtered Asset
profile, not another registry.

## What would change this

An organization-level policy may later require review for selected Spaces or
Skill risk classes. That policy should disable the dedicated direct permission
or make its check policy-aware; it should not erase the provenance distinction
or create a second release aggregate.
