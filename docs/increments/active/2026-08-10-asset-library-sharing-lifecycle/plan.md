# Asset Library Sharing Lifecycle — Plan

Design: [design.md](design.md). Architecture challenge:
[challenge brief](challenge-brief.md) and [verdict](challenge-verdict.md).

Implementation began on `feat/asset-library-sharing-lifecycle` after the
project owner explicitly prioritized this increment over the stale overlapping
worktree. Remaining unchecked items are follow-up release gates or intentionally
unimplemented hardening; checked items are executable in this branch.

## 0. Decision and characterization gates

- [x] Record the independent architecture verdict and incorporate every
  must-fix item into this design or explicitly defer it with project-owner
  direction.
- [x] Characterize current create, review, direct Skill publication, role
  assignment, catalog visibility, availability, exact delivery, feedback, and
  ownership-health behavior before changing it.
- [ ] Produce a per-Asset redacted conflict report covering zero/multiple/group/
  inactive/missing owners, duplicate grants, open reviews, approved-unreleased
  revisions, and database/OpenFGA drift; no customer names or payloads.
- [x] Confirm the active Modulith refactor has landed or declared a non-overlap.

## 1. Contract-first product model

- [x] Split released-consumption/detail DTOs from governance/history DTOs first.
  Write failing tests proving Viewer access never exposes Drafts, review
  comments, unreleased payloads, or role principals.
- [x] Write failing core and API tests for one owner, direct share, Editor Draft
  edit, owner-only publication, Viewer consumption, ownership transfer,
  orphan-only administrator recovery, asset-level withdrawal, and separately
  authorized administrator emergency withdrawal.
- [x] Write security-negative tests proving a Viewer cannot edit or read
  governance, an Editor cannot publish/share/transfer/withdraw, an administrator
  cannot ordinarily edit/publish/share, organization sharing cannot grant
  Editor, and no relationship exceeds its action-specific Space ceiling.
- [x] Define the Catalog projection for owner, sharing state, current exact
  Release, and Skill activation. Preserve opaque denial behavior.
- [x] Replace new review/publication affordances with served
  `can_manage_sharing`, `can_transfer_ownership`, `can_publish_direct`, and
  `can_withdraw`; browser controls remain projections only.
- [x] Update OpenAPI and regenerate the web client only after the core contract
  is executable.

## 2. Compatibility persistence and authorization

- [x] Add Flyway persistence for typed canonical user, group-member, and
  organization-member share/ownership intent; a one-active-human-owner
  invariant; relationship generation; Skill per-user activation; and asset-
  level withdrawal/ownership-transfer evidence. Keep `ddl-auto=validate`.
- [x] Extend the authorization outbox with versioned WRITE and DELETE
  operations, idempotent replay, convergence state, and canonical-generation
  rechecks. Stale tuples may over-deny but must never grant.
- [x] Add target OpenFGA permissions while legacy relations remain valid;
  model `group:<id>#member` and organization membership explicitly; extend
  model tests for every positive and cross-tenant/security-negative case.
- [ ] Quarantine every ambiguous owner set. Implement idempotent unambiguous
  conversion: `STEWARD -> EDITOR`; `BACKUP_OWNER -> EDITOR` only when exactly
  one active valid Owner exists. Preserve immutable assignment history.
- [ ] Drain or explicitly cancel every open review and resolve approved-but-
  unreleased revisions. Then stop new `BACKUP_OWNER`, `STEWARD`, `REVIEWER`, and
  `PUBLISHER` writes while keeping legacy history reads.
- [ ] Prove database/OpenFGA convergence, rollback, and previous-binary
  compatibility before removing legacy relations from computed permissions.
- [ ] Run the authorization-model change through the dedicated production
  rollout; feature worktrees do not mutate shared ZM authorization state.

## 3. Direct library workflow

- [x] Generalize owner-only direct publication to every enabled Asset profile.
  First Share and owner Save update each validate and create one immutable
  Revision and Release with `DIRECT` provenance.
- [x] Permit Editors to edit the working Draft only. Keep publication, sharing,
  ownership transfer, and ordinary withdrawal owner-only.
- [x] Commit first Release, canonical audience intent, and audit evidence in one
  PostgreSQL transaction, then project user/group/organization Viewer
  relationships. Expose pending/failed convergence honestly; do not claim
  cross-store atomicity.
- [ ] Implement one locked ownership transfer in the canonical ledger and
  demotion of the previous owner to Editor. Validate an active organization
  member and Space ceiling, then converge the projection idempotently.
- [x] Implement ownerless-only administrator recovery with a locked vacancy
  predicate and audit evidence; add a separate `can_emergency_withdraw` path.
- [x] Implement asset-level withdrawal of every non-withdrawn Release and
  identity retirement. Exact historical reads remain available only through
  authorized governance/history paths.
- [ ] Disable new review-case mutations after compatibility gates pass. Preserve
  historical reads and old `REVIEWED` delivery metadata.

## 4. Skill activation

- [x] Add an actor-scoped Enabled preference for visible Skills; specify the
  migration and default. Effective activation is preference AND live `can_use`
  AND a non-withdrawn current Release; it never changes sharing.
- [ ] Define a canonical namespace collision key from validated Skill identity.
  Enabling a conflict requires explicit replacement and is transactional.
- [x] Filter Assistant Skill discovery by live visibility plus the actor's
  Enabled preference; keep exact Release resolution and fail-closed
  authorization.
- [ ] Cover revocation, withdrawal, renamed/replaced Skills, concurrent enables,
  orphaned owners, and same-name Skills from different Spaces.

## 5. Catalog and authoring UX

- [x] Change primary scopes to **Available to me** and **Created by me**.
  Catalog cards/list rows show owner, sharing state, updated time, type, and
  current version; Skills also show Enabled.
- [ ] Make Share one bounded modal for people, groups, or organization Viewer.
  Editors are added only as explicit collaborators, never through organization
  sharing.
- [x] Replace the primary Governance tabs with a contributor surface centered
  on working copy, Share/Save update, sharing, and ownership. Move immutable
  version and legacy review history behind secondary details.
- [x] Remove active Review and Deprecated controls. Keep truthful explanation
  that structural validation is not independent review.
- [x] Keep the current Catalog free of a primary Space taxonomy; this is a
  preservation test, not removal of a control that exists. Show Space only as
  secondary access context and in authoring when the actor must choose a target.
- [ ] Add desktop, narrow/mobile, keyboard, loading, error, authorization-race,
  and two-user browser coverage.

## 6. Consolidation and release gates

- [x] Supersede decision 0022 rather than editing it. Record why direct
  contribution now covers all profiles, why immutable Releases remain, and why
  backup owner/review are removed from new POC writes.
- [x] Reconcile `docs/specs/domains/asset-registry.md` and its test mirror only
  after behavior ships; refresh `Source:` and `Reconciled:`.
- [ ] Reconcile `ARCHITECTURE.md`, vision, public docs, and generated OpenAPI
  without describing planned behavior as current.
- [x] Run focused core/API/OpenFGA/web tests while iterating, then terminating
  `clean test`, frontend lint/typecheck/unit/build, docs check/build, generated
  contract diff, and real-browser proof.
- [ ] Capture redacted migration/convergence, rollback, and exact old-Release
  consumption evidence before marking the increment shipped.
