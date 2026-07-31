# Architecture challenge: direct Skill sharing

Date: 2026-07-31

## Reviewer mandate

Attack this proposal. Do not validate it because the requested UX sounds
reasonable. Work read-only, verify every repository claim in current code, and
look for authorization expansion, publication ambiguity, package-reference
loss, inconsistent audit evidence, and paths that let one actor silently make a
Skill available to unintended users.

Read `CLAUDE.md`, `docs/conventions.md`,
`docs/specs/domains/asset-registry.md`,
`docs/tests/domains/asset-registry.md`, and decision filenames before judging.
Use current source rather than completed increment prose.

Return:

1. one explicit verdict: accept, accept with must-fix constraints, or reject;
2. repository evidence for every material claim;
3. the strongest counterexample against your own verdict;
4. a must-fix list small enough for one coherent PR;
5. the exact authorization rule that should ship.

Do not edit files.

## Product promise at stake

OrgMemory is a self-hosted governed organizational memory layer. A Skill is a
validated, versioned Agent Skills package distributed as an exact immutable
release under live object authorization. The product must make contribution
easy enough that employees share useful work, without pretending a human
reviewer is a security scanner or letting "easy sharing" erase accountability,
audience control, provenance, or withdrawal.

## Exact proposal under review

> For `SKILL` only, an actor with live Asset `can_edit` may publish the current
> Draft directly. The transaction creates an immutable Revision and Release,
> copies the exact package reference to both, records audit evidence, creates no
> review case, changes no viewer or role assignment, and preserves `can_use` on
> every discovery/download. Other Asset types retain mandatory review.

Proposed REST command:

```http
POST /api/assets/{assetId}/skill-releases
{"versionLabel":"1.0.0"}
```

Proposed UI: a Skill Draft shows `Publish Skill`; it does not show `Submit for
review`.

## Current enforcement paths

| Concern | Current source |
| --- | --- |
| Skill import and package validation | `core/src/main/java/com/orgmemory/core/assetregistry/SkillRegistryService.java`, `SkillPackageInspector.java` |
| Draft, Revision, Review, Release persistence | `core/src/main/java/com/orgmemory/core/assetregistry/AssetRegistryCoordinator.java` |
| Service authorization | `core/src/main/java/com/orgmemory/core/assetregistry/AssetRegistryService.java` |
| Asset relationship model | `integrations/authorization-openfga/src/main/openfga/model.fga` |
| REST lifecycle | `apps/api/src/main/java/com/orgmemory/api/assetregistry/AssetRegistryController.java` |
| Skill Draft UI | `apps/web/src/features/assets/components/governance-draft-workspace.tsx` |
| Governance affordance projection | `core/src/main/java/com/orgmemory/core/assetregistry/AssetGovernanceActions.java` |

Today `submit` requires `can_submit_review`, a decision requires `can_review`,
and `publish` requires `can_publish` plus an approved exact revision. OpenFGA
derives `can_edit` from editor, steward, owner, backup owner, or org admin,
bounded by `can_create_asset` on the parent Knowledge Space.

## Comparable-system evidence

| System | Evidence | Observed boundary |
| --- | --- | --- |
| Claude Team/Enterprise | https://support.claude.com/en/articles/13119606-provision-and-manage-skills-for-your-organization | Owners provision directly; member peer/org sharing requires an admin toggle but no per-Skill owner approval; audit logs retain sharing events. |
| Microsoft 365 Agent Builder | https://learn.microsoft.com/en-us/microsoft-365/copilot/extensibility/agent-builder-submit-to-org-catalog | Named-user/group sharing has no review; organization catalog promotion has admin review. |
| skills.sh | https://www.skills.sh/docs and https://vercel.com/changelog/automated-security-audits-now-available-for-skills-sh | Automated security audits and install signals exist; no human domain reviewer is assigned universally. |

These are product comparisons, not authorization proofs. Reject any analogy
that does not survive OrgMemory's actual model.

## Motivating product cost

The current flow asks a contributor to find an independent reviewer and then a
publisher before any colleague can consume the package. The project owner
challenged that persona directly: a new enterprise may have no one qualified to
review a new kind of Skill, and the author may be the only domain expert.
Separately, `REQUEST_CHANGES` is not a complete Skill loop because package
replacement is explicitly deferred. The result is a governance screen that can
block the first useful sharing event without providing a workable correction
path.

## Suspected failure scenarios

Challenge at least these:

1. The owner assigns a broad group as viewer, directly publishes malicious
   instructions, and no second person intervenes.
2. `can_edit` permits a delegated editor to publish even though the owner
   intended edit-only collaboration.
3. revision creation succeeds but package-reference copy or release creation
   fails, leaving durable partial state.
4. a direct Release and a reviewed Release race on sequence or version label.
5. the UI hides review but an old API path still creates confusing active
   review cases for Skills.
6. "OrgMemory never executes packages" is used to ignore the fact that a
   consumer's agent may execute bundled scripts after installation.
7. an automated package validator is mistaken for malware or semantic-quality
   analysis.

## Candidate alternatives

- preserve mandatory review;
- allow direct publication only to `publisher` rather than `can_edit`;
- introduce `can_share_skill` derived from a Knowledge Space policy;
- keep private/team sharing direct but require admin approval for a future
  organization catalog;
- reject direct publication until malware scanning and Skill evaluation exist.
