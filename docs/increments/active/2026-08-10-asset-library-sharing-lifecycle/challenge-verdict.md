# Architecture Verdict: Onyx-like Asset library lifecycle

- Date: 2026-08-10
- Repository: `D:\OrgMemory`
- Review basis: commit `e71975a228f53633dd8d5d7a42ee0a055ee1c927`, plus
  the uncommitted increment proposal
- Reference pins: Onyx `5200dade0709f926f15309dbe48b1e43e680c202`;
  Langfuse `ac6020bb4f5e903a2dc3cf57c9eaf373a3e03e2a`

## Reviewer availability

The required Fable 5 reviewer was started through Orca in a fresh read-only
terminal, but Claude reported `Not logged in · Run /login`. Per the architecture
challenge fallback rule, a fresh Codex `gpt-5.6-sol` session with `ultra`
reasoning performed the independent read-only review. It received only the
challenge brief, inspected the repository and pinned references, and returned
one verdict with its counterattack embedded. It made no file changes.

## Verdict

**Accept with must-fix constraints.**

Use one internal-POC contributor lifecycle for Prompt Templates, Work
Instructions, Capability Packs, and Skills: Private Draft, Shared exact Release,
and Withdrawn. Do not require new Review for any enabled profile. Keep one
accountable owner, immutable Releases, truthful `DIRECT` provenance, Skill-only
per-user activation, and owner projection in Catalog.

Reject Editor publication. Editors may edit a working Draft; only the sole
active owner may create the current Release or change sharing. Organization
administrators may recover ownership only when the valid-owner count is zero and
may use a separately named emergency-withdraw permission. Administration does
not imply edit, publication, or ordinary sharing authority.

## Claim corrections

- Onyx has at most one nullable author, not guaranteed ownership. Its Editors
  are broader than this design: they may replace the live bundle, manage scoped
  shares, and hard-delete. Onyx has no immutable Release or Withdraw boundary.
- Langfuse proves append-versioned Prompt content without universal review, not
  fully immutable rows or Asset-scoped Editor authority. Prior labels/tags
  mutate and versions may be deleted.
- The current OrgMemory database does not enforce one owner; it prevents only
  duplicate `(asset, principal, role)` rows and tests assign a second owner.
- Current owner health checks unexpired assignments, not whether the user still
  exists and is active.
- The current Catalog already has no Space filter. The increment preserves that
  product choice rather than removing an existing control.
- Current group subjects and OpenFGA usersets do not line up, organization
  audience intent has no canonical persisted form, and the Asset authorization
  outbox can only add tuples. Atomic unshare or transfer is impossible with the
  present infrastructure.
- `can_view` currently exposes one broad `AssetView` containing Draft, revision,
  review, Release payload, and role history. Audience widening would therefore
  leak governance data unless the wire contracts are split first.
- Existing open reviews are unresolved migration state. Freezing review
  mutations while cases remain open can permanently block direct publication.

## Committed boundaries

### Profiles and roles

All four enabled profiles may use direct contribution within the internal POC.
Profile validation and exact consumption remain mandatory.

- `OWNER`: exactly one active human; edit, publish direct, manage sharing,
  transfer ownership, and withdraw.
- `EDITOR`: user or group; edit Draft only.
- `VIEWER`: discover and consume released data only, within the Space ceiling.
- `ORG_ADMIN`: vacancy-only recovery and separately audited emergency withdraw;
  no standing edit, publication, or sharing authority.

Transfer targets must be active standard humans in the same organization and
pass both Space `can_view` and `can_create_asset`. Ordinary transfer demotes the
old owner to Editor. Recovery does not recreate a missing/inactive owner as an
Editor.

### Lifecycle

Immutable Releases plus Asset-level Withdraw are sufficient without new Review
or manual Deprecated for this POC. Private and Shared are derived audience
states; Withdrawn is a lifecycle condition.

First Share commits the validated Revision, immutable Release, canonical
audience intent, and audit evidence in PostgreSQL. OpenFGA projection follows;
the UI reports pending or failed convergence and never claims cross-store
atomicity. Unshare and transfer take effect in the canonical ledger immediately,
and sensitive checks recheck its generation so stale tuples cannot grant.

Asset-level Withdraw locks the Asset, withdraws every non-withdrawn Release,
retires identity, prevents an old Release from resurfacing, and keeps history
behind a governance-history permission. Installed Skill bytes cannot be
recalled, which the UI must state. Historical `DEPRECATED`, `REVIEWED`, and
review records remain readable after the migration.

## Must-fix before implementation

1. Make direct publication owner-only; separate recovery and emergency-withdraw
   permissions from ordinary operations.
2. Split released consumption/detail from governance/history wire contracts
   before adding Viewer audiences.
3. Add a database invariant for exactly one active human owner and validate
   typed subjects plus active organization membership.
4. Produce a per-Asset redacted conflict report; quarantine zero/multiple/group/
   inactive/missing owner conflicts rather than selecting an owner silently.
5. Persist canonical user, `group#member`, and `organization#member` share and
   ownership intent.
6. Add versioned OpenFGA WRITE/DELETE outbox operations, idempotent replay,
   convergence state, and canonical-generation rechecks.
7. Define action-specific Space ceilings; ownership/publication require both
   view and create authority.
8. Drain or explicitly cancel open reviews and resolve approved-unreleased
   revisions before disabling review mutations.
9. Preserve legacy relations, tuples, model pins, and history authorization
   through the previous-binary rollback window.
10. Implement locked Asset-level withdrawal with no old-Release resurrection
    and a separate governor-only history path.
11. Specify Skill Enabled default/migration and compute it as preference AND
    live authorization AND non-withdrawn current Release.
12. Add concurrency and security-negative tests for Viewer isolation, Editor
    publication denial, group/org audiences, transfer races, stale tuple
    deletion, owner inactivity, withdraw-versus-publish, rollback, and review
    cutover.

## Counterattack and survival

The reviewer attacked its preliminary acceptance with five failures:

1. Owner-only publication can recreate a bottleneck. The verdict survives only
   if explicit transfer and vacancy recovery ship before backup-owner removal.
2. Owner self-publication is not review. The verdict survives only for the
   internal POC with explicit audiences, exact consumption, truthful `DIRECT`
   provenance, no automatic execution, and emergency withdrawal.
3. Asynchronous transfer can leave stale authority. Without generation recheck
   and an operation-aware outbox, the verdict changes to reject.
4. Organization sharing currently leaks governance data. Without the wire-
   contract split before audience widening, the verdict changes to reject.
5. Premature cleanup can break rollback and strand reviews. The verdict
   survives only with review drain, shadow comparison, previous-binary
   compatibility, and delayed cleanup.

## Rejected alternatives and scope limits

Rejected Editor-can-publish because it converts an authoring delegation into
live-change authority without evidence. Also rejected retaining mandatory
review for Work Instructions and Capability Packs in the internal POC because
it preserves an unstaffable queue and defeats the adoption goal.

This verdict does not approve public or cross-organization sharing, automatic
execution, regulated operation, legacy cleanup before rollback gates pass, or
implementation while the active Asset Registry package-refactor overlaps the
same files.
