# Architecture challenge: Onyx-like Asset library lifecycle

You are an independent architecture reviewer. Attack this proposal; do not
validate it politely. Read the repository yourself and identify where the
summary is wrong. This is a read-only review: make no edits, run no mutations,
and do not enter plan mode.

Read `AGENTS.md` (via `CLAUDE.md`), `docs/conventions.md`,
`docs/guidelines/agent-safety.md`,
`docs/specs/domains/asset-registry.md`,
`docs/tests/domains/asset-registry.md`, decision filenames under
`docs/decisions`, decision 0022, and this increment's `design.md` and `plan.md`.
Verify material claims in code. The repository is at commit
`e71975a228f53633dd8d5d7a42ee0a055ee1c927`; the new increment files are an
uncommitted proposal on top of that commit.

This is one bounded review round. Include your own counterattack inside the
same response: after reaching a preliminary verdict, challenge it with at least
three concrete contradictions or failure scenarios, then state whether the
verdict survives or changes.

## Product promise at stake

OrgMemory is a governed organizational memory layer for enterprise AI work.
Assets include Prompt Templates, Work Instructions, Capability Packs, and Agent
Skills. Consumption is permission-aware and resolves exact immutable Releases;
Skills can be downloaded and installed by external agents, Packs pin exact
components, and human-facing instructions can affect real work. The product
must be easy enough for a small enterprise team to adopt without pretending
that a review board already exists.

## Exact proposal under review

From `docs/increments/active/2026-08-10-asset-library-sharing-lifecycle/design.md`:

> Adopt Onyx's contribution model as the POC product baseline: one accountable
> owner creates or imports an Asset privately, shares it directly with people,
> groups, or the organization, and transfers ownership when needed. Keep
> OrgMemory's immutable Release boundary underneath that simpler experience.

The disputed rules are:

1. Remove Review from new POC writes for **all four** enabled Asset profiles;
   preserve old review evidence read-only.
2. Allow an explicit Editor to save a new live immutable Release, while only
   the owner/admin may share, transfer ownership, or withdraw.
3. Replace backup owner with one owner, explicit transfer, and administrator
   recovery only when ownership is vacant.
4. Present lifecycle as **Private -> Shared -> Withdrawn**; keep immutable
   Revision/Release history but hide manual Deprecated in the POC.
5. Add per-user Enabled only for Skills; keep Knowledge Space as an
   authorization ceiling but remove it from primary Catalog navigation.

Today the repository enforces a different generic model:

- `docs/specs/domains/asset-registry.md:20-52,130-159,370-390` describes generic
  review/release/availability and Skill-only direct publication.
- `core/src/main/java/com/orgmemory/core/assetregistry/AssetRegistryService.java`
  exposes separate submit, review, publish, direct-Skill publish, role, and
  withdraw use cases.
- `integrations/authorization-openfga/src/main/openfga/model.fga:76-92` defines
  owner, backup owner, steward, editor, viewer, reviewer, and publisher plus
  distinct computed permissions.
- `core/src/main/java/com/orgmemory/core/assetregistry/AssetRegistryCoordinator.java`
  creates exact Revision/Release records, appends availability events, and
  retires identity only after all Releases are withdrawn.
- `apps/web/src/features/assets/components/governance-workspace-page.tsx` and
  `governance-draft-workspace.tsx` expose the current governance journey.

## Comparable-system evidence

| System and pin | Behavior verified from source | Relevant source |
| --- | --- | --- |
| Onyx `5200dade0709f926f15309dbe48b1e43e680c202` | One author/owner; user, group, or organization share with Viewer/Editor; no review state | `tmp/onyx-skills-reference/backend/onyx/db/models.py:4695-4750`, `backend/onyx/db/skill.py:145-175,433-495` |
| Onyx | Direct create/import and live sharing; per-user enable; ownership transfer demotes previous owner | `backend/onyx/server/features/skill/api.py:241-282,760-905`, `backend/onyx/db/skill.py:497-649` |
| Onyx | Bundle replacement mutates in place and delete is hard | `backend/onyx/db/skill.py:395-430,652-657` |
| Langfuse `ac6020bb4f5e903a2dc3cf57c9eaf373a3e03e2a` | Each Prompt change creates the next immutable numbered row and moves `latest`; ordinary edit capability does not imply universal review | `tmp/langfuse-reference/packages/shared/prisma/schema.prisma:711-734`, `web/src/features/prompts/server/actions/createPrompt.ts:93-214`, `web/src/features/prompts/server/routers/promptRouter.ts:300-330` |
| Current OrgMemory | Every consumer resolves an exact authorized Release; Pack and Skill distribution depend on stable coordinates and digests | `docs/specs/domains/asset-registry.md:42-49,414-454`; verify core delivery and package code |

Do not assume Onyx is correct merely because it is simpler. It manages Skills;
this proposal generalizes the interaction to Work Instructions and Capability
Packs. Do not assume OrgMemory's current complexity is justified merely because
it has already been implemented.

## Operational/product cost motivating the change

The target enterprise is still adopting the system and cannot reliably staff
expert reviewer and publisher roles. Review consumes scarce domain expertise,
creates a queue with no clear owner when policy does not require it, and blocks
authors from making useful internal Assets available. The current Catalog also
fails to show the accountable owner in its primary read model while exposing
governance machinery that most readers do not need. Backup ownership adds a
second standing authority without proving that another qualified person exists.

There is no recorded production incident proving that direct publication of a
Work Instruction or Pack is safe. That absence is evidence uncertainty, not a
claim of low risk.

## Strongest counterargument

Immutability is not approval. A bad Work Instruction or Capability Pack can
direct human behavior immediately; making the bad Release exact only makes the
error reproducible. Allowing Editors to publish lets one owner delegation become
a live-change delegation. A safer POC would apply Onyx behavior only to Skills
and Prompts, keep owner-only direct publication, and retain reviewed publication
for action-bearing Asset profiles.

## Required verdict

Answer with:

1. **Verdict:** accept, accept with must-fix constraints, or reject.
2. **Claim audit:** what the proposal or evidence gets wrong, with repository
   paths for every material claim.
3. **Profile boundary:** whether all four profiles may use the same direct
   lifecycle; commit to a rule.
4. **Role boundary:** whether Editors may create the current Release; commit to
   a rule.
5. **Lifecycle boundary:** whether immutable Releases plus Withdraw are enough
   for the POC without Review and manual Deprecated.
6. **Authorization/migration hazards:** the most likely widening, orphaning, or
   rollback failure and the structural control required.
7. **Must-fix list** before implementation.
8. **Counterattack:** at least three concrete contradictions against your own
   preliminary verdict and whether it survives.
9. **Committed recommendation** in one paragraph, plus the rejected
   alternative and explicit scope limits.
