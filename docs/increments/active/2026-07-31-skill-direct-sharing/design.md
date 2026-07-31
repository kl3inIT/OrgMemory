# Direct Skill sharing

Date: 2026-07-31

## Outcome

Make the first useful Skill lifecycle match the low-friction organization
sharing model used by Claude: a contributor can validate a Skill, import it as
a private Draft, and publish an immutable version directly to the people who
already have access. Human review is not a mandatory gate for every Skill.

This increment changes only `SKILL`. Prompt Templates, Work Instructions, and
Capability Packs retain the existing submit, independent review, approval, and
publication lifecycle.

## Problem

The current Skill path is:

```text
package -> Draft -> submit -> independent reviewer -> publisher -> Release
```

That flow assumes the organization already has a qualified reviewer for every
Skill. In a new organization the author is often the only subject-matter
expert, while a manager or platform administrator can assess distribution risk
but cannot reliably judge every instruction bundle. The mandatory handoff
therefore creates a role with no natural owner and blocks small teams before
they can learn from real use.

The mismatch is sharper for the currently implemented Skill profile:

- OrgMemory validates and hashes the package but never executes it.
- `allowed-tools` is portability metadata and grants no runtime authority.
- publishing a Release does not add a viewer or widen the audience; OpenFGA
  still decides `can_use` for each actor.
- an immutable revision, release digest, exact package reference, availability
  history, and audit evidence are useful without a human review case.
- after `REQUEST_CHANGES`, Skill package replacement is explicitly deferred, so
  the strict review flow can lead to a dead end.

## Comparable product evidence

| Product | Observed model | Relevance |
| --- | --- | --- |
| Claude Team and Enterprise Skills | Owners may provision Skills directly. When administrators enable member sharing, peer-to-peer and organization-directory sharing do not require per-Skill owner approval; sharing events remain auditable. | Separate the administrator's policy decision from a mandatory review of every item. |
| Microsoft 365 Agent Builder | Direct sharing to named people or groups has no review. Only promotion to the organization catalog receives an administrator review of metadata, capabilities, sources, and compliance. | Increase governance with audience and risk instead of applying the broadest gate to every draft. |
| skills.sh | Automated security audits and install telemetry provide signals, but no human domain reviewer is assigned to every Skill and quality is not guaranteed. | Human review does not scale as a universal quality or security boundary. |

Sources:

- https://support.claude.com/en/articles/13119606-provision-and-manage-skills-for-your-organization
- https://learn.microsoft.com/en-us/microsoft-365/copilot/extensibility/agent-builder-submit-to-org-catalog
- https://www.skills.sh/docs
- https://vercel.com/changelog/automated-security-audits-now-available-for-skills-sh

## Decision

Add a Skill-only direct-publication command:

```text
validated package
-> private Draft
-> Publish Skill
-> immutable Revision
-> immutable Release
-> visible only to actors already authorized with can_use
```

The command:

- requires live Asset `can_publish_skill`, computed as owner, backup owner,
  steward, or organization administrator inside a Knowledge Space where the
  actor may create Assets;
- accepts a release version label but no reviewer, review comment, or approval;
- atomically snapshots the current Draft into a new immutable Revision and
  Release;
- copies the exact validated Skill package reference from Draft to Revision and
  Release after checking the manifest digest and length;
- records the actor and a direct-publication policy marker in the audit trail;
- does not create a review case and does not change any role assignment;
- does not bypass `can_use`, object authorization, exact-release installation,
  deprecation, or withdrawal.

The existing reviewed publication endpoint remains valid for non-Skill
profiles and as an optional Skill path. New Skill UI defaults to direct
publication; an active Skill review interlocks the direct command.

Every Release persists `publicationMode` as `REVIEWED` or `DIRECT`. Existing
rows are backfilled to `REVIEWED`, which is correct because the current
publication path always requires an approved ReviewCase. Governance and
delivery projections expose the mode explicitly so consumers do not infer it
from absent review history.

No tenant-wide sharing toggle is introduced in this increment. Audience is the
current Asset relationship set. A future organization-directory policy may add
an approval threshold when broad discovery is implemented, but it must not be
approximated with fake ratings or a generic reviewer role now.

## Strongest counterargument

`can_edit` is an authoring permission, not a publication permission. Allowing an
editor to create a consumable immutable release could let a malicious or
mistaken author distribute instructions without a second person checking them.
An Asset owner can also manage viewer assignments, so "publication does not
widen access" does not by itself prevent an author from granting a group access
and then publishing unsafe content.

The narrow response is that current human review is not a security boundary:
the reviewer is not guaranteed to inspect scripts, has no automated evaluation
evidence for Skill behavior, and the installed package executes in the
consumer's agent environment rather than OrgMemory. Package validation,
authorization-scoped distribution, exact digests, visible provenance,
deprecation, withdrawal, and consumer confirmation remain the enforceable
controls. The independent challenge confirmed that `can_edit` is too broad.
The accepted rule is the dedicated computed `can_publish_skill` permission
described above. A future Knowledge Space policy can narrow that permission
without changing the command contract.

## Rejected alternatives

### Keep reviewer and publisher mandatory

Rejected because it preserves a role that early organizations cannot staff and
does not solve the incomplete Skill correction loop.

### Remove immutable revisions and Releases

Rejected because direct sharing still needs exact package identity, provenance,
update isolation, deprecation, withdrawal, and reproducible installation.

### Publish immediately during CLI upload

Deferred because creation projects new Asset relationships before later object
authorization is authoritative, and combining upload, audience selection, and
publication would make failure recovery and user intent less clear. This
increment keeps one explicit `Publish Skill` confirmation after the Draft is
visible.

### Add ratings, install counts, or a separate marketplace

Rejected from this increment. Those are adoption signals after a usable
lifecycle exists, not publication authorization.

## Contract sketch

Core exposes a Skill-specific direct-publication method and returns the normal
`AssetView`. REST exposes a Skill-specific command below the Asset:

```http
POST /api/assets/{assetId}/skill-releases
Content-Type: application/json

{"versionLabel":"1.0.0"}
```

The generated governance-action projection adds `canPublishSkill`, true only
when the Asset is a Skill and the actor has live `can_publish_skill`. Every mutation
repeats that authorization.

The Governance Draft card shows package identity and one version field with
`Publish Skill`. Non-Skill Drafts keep `Submit for review`. Historical Skill
reviews remain readable but are not required for new versions.

## Verification

- service tests prove only Skill Drafts can use direct publication;
- an editor/owner can publish while an unauthorized viewer cannot;
- publication creates one immutable Revision and Release without a review case;
- Draft, Revision, and Release pin the same package object and SHA-256;
- duplicate labels and concurrent sequences retain the current conflict
  behavior;
- an active Skill review blocks direct publication while the reviewed path
  remains available;
- every current Release is migrated to `REVIEWED` and every direct Release is
  visibly `DIRECT`;
- catalog and delivery still intersect exact Releases with live `can_use`;
- web tests prove a Skill Draft offers `Publish Skill` and does not offer
  `Submit for review`;
- non-Skill governance tests remain unchanged;
- the browser proof imports one Skill, publishes it directly, and installs the
  exact released package as a second authorized user.

## Scope limits

- no package replacement or orphan cleanup;
- no CLI update/remove commands;
- no new tenant setting or organization-wide marketplace;
- no automatic execution, new tool authority, or credential access;
- no ratings, usage totals, contributor score, or rewards.

## Accepted limitations

- No tenant or Knowledge Space setting mandates Skill review yet. Review remains
  available as an optional API path, and the dedicated permission is the
  future policy hook.
- `can_manage_roles` currently lets a steward self-escalate to owner. Excluding
  steward from `can_publish_skill` would therefore change the number of steps,
  not reachable authority. Fixing role escalation is a separate material
  authorization decision.
