# Direct Skill sharing architecture verdict

Date: 2026-07-31

Reviewed commit: `4acfc94fc494ee34a97486c3719e4c7282d6c95d`

Reviewer: independent read-only Claude Fable 5 session dispatched from
`challenge-brief.md`, followed by an explicit contradiction round.

## Verdict

**Accept with must-fix constraints.**

Direct Skill publication without a mandatory review case is sound in the
current repository, but the proposal's original `can_edit` authorization rule
must not ship. It would lower both the Asset gate from publisher to delegated
editor and the Knowledge Space gate from reviewer to contributor.

The human review being removed is not a content-inspection boundary today:
the product exposes a Skill Draft's digest, file paths, and sizes but no
Draft/Revision package-content endpoint. A reviewer cannot inspect `SKILL.md`
or bundled scripts through the governed product. Separately, Skill package
replacement remains deferred, so `REQUEST_CHANGES` can create a dead end.

## Committed authorization rule

Add one computed OpenFGA permission:

```fga
define can_publish_skill:
  (owner or backup_owner or steward or org_admin)
  and can_create_asset from space
```

The direct command must check live `can_publish_skill` at the service boundary.
The coordinator must additionally require `type == SKILL`, no active
`IN_REVIEW` case, and an exact match between the Draft manifest and stored
package reference.

This intentionally matches the actor set that can manage the Asset audience.
The importing author is automatically assigned `OWNER`, so the lone domain
expert can publish without granting delegated editors release authority.

## Must-fix constraints

1. Ship `can_publish_skill` in the OpenFGA model and add executable allow/deny
   cases for owner, backup owner, steward, org admin, editor, contributor, and
   viewer.
2. Use one `REQUIRES_NEW` coordinator transaction for Draft snapshot, immutable
   Revision, Draft-to-Revision-to-Release package-reference copy, Release,
   availability event, and audit. Do not compose the existing `submit` and
   `publish` transactions.
3. Reject direct publication while any Skill revision is `IN_REVIEW`.
   Retain submit/review/reviewed publication as an optional API path so an
   organization can still practice four-eyes review before a future policy
   toggle exists.
4. Persist `publication_mode = REVIEWED | DIRECT` on every Release through an
   expand-only Flyway migration, backfilling current rows to `REVIEWED`.
   Expose the mode in governance, delivery, and Skill manifest projections;
   consumers must not infer it from missing review data.
5. Add `canPublishSkill` to the display-only governance action projection and
   repeat authorization on mutation. Product copy must describe structural
   package validation honestly and never claim malware or semantic review.
6. Record a distinguishable audit event and
   `{"policy":"skill-direct-v1","permission":"can_publish_skill"}` context.

## Counterattack outcome

### Steward authority

Steward is not an ownership-health role and may be a group. Excluding it from
direct publication would nevertheless add no real boundary in the current
model: `can_manage_roles` already lets a steward assign any Asset role,
including `OWNER`, to itself or another principal. Keeping steward in the rule
does not expand its reachable authority; role self-escalation is a pre-existing
limitation that requires a separate authorization challenge.

### Missing tenant review policy

Removing the reviewed API path would hard-code a governance downgrade. The
verdict therefore retains optional Skill submit/review/reviewed publication.
The UI defaults to direct publication, while an active review interlocks the
direct path. A future Knowledge Space policy can feed the dedicated
`can_publish_skill` permission or a service policy check without renaming this
contract.

### Durable provenance

Audit-only evidence is insufficient because delivery deliberately excludes
review records. Publication mode must therefore be a Release field rather than
an absence-based inference.

## Strongest surviving counterargument

OrgMemory removes four-eyes accountability for the only current Asset profile
whose installed package may cause a consumer's agent to execute bundled code,
while plain-text Prompt Templates retain mandatory review. The repository also
lacks a tenant setting to restore mandatory Skill review.

The verdict survives because the second person cannot inspect package content
through OrgMemory today. Retaining that click is not a security review.
Owner-class publication, exact digests, immutable Releases, live `can_use`,
visible direct/reviewed provenance, audit, deprecation, and withdrawal are the
controls that the current system can actually enforce.

## Scope limits

- `SKILL` only; all other profiles retain mandatory review.
- no tenant or Knowledge Space `require review` policy in this increment;
- no fix for steward/role self-escalation;
- no change to the pre-existing stale-approved-revision ordering behavior;
- no package replacement, orphan cleanup, CLI update/remove, marketplace,
  ratings, or execution authority;
- comparable products informed the product framing only; authorization follows
  repository evidence.
