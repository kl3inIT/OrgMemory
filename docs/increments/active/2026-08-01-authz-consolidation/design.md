# Authorization Consolidation

Source: full-codebase simplify review (2026-08-01), deferred Tier B/C security
items. Evidence inventory: [references/](references/) (phase 1 and phase 5
reports; file:line references predate later merges — verify against current
source before editing).

## Problem

Two authorization rules are maintained in multiple places and have already
drifted or will drift:

1. **Fail-closed OpenFGA batch recheck (backend).** The protocol — batch-check
   evidence against OpenFGA at serving time, drop anything unverified, deny on
   provider failure — is hand-rolled in three core knowledge services
   (retrieval service, canonical evidence authorization, hybrid search; see
   phase 1 report items B-2/K1 for the original sites, now under
   `core/.../knowledge/retrieval/`). The copies already differ in reason codes
   and structure. A future edit to one copy but not the others silently
   weakens a security boundary.
2. **Client-side re-encoding of decision rights (frontend).** `apps/web`
   derives "may I review this draft" (self-review prohibition in
   `governance-policy.ts`) and "may I manage this asset" (role-assignment
   arithmetic in `asset-detail-page.tsx`) from raw data instead of consuming a
   server verdict. Phase 5 report item C2. Drift shows users actions the
   server rejects, or hides actions they hold.

## Proposal (revised after architecture challenge)

The original shape (deny-reason-parameterized collaborator; `canDecide` /
`canManage` booleans) was challenged and reshaped — see
[challenge-verdict.md](challenge-verdict.md) for the full rationale, strongest
counterargument, and rejected alternatives.

1. Backend: one collaborator owned next to the existing knowledge
   authorization services with a MANDATORY typed result policy —
   `FILTER_DENIED` (hybrid search: denied evidence is filtered, partial
   results allowed) vs `REQUIRE_ALL_ALLOWED` (citation and GraphRAG final
   closure: any deny fails the whole request). Each call site keeps its
   surface-specific reason/exception mapping (`AUTHORIZATION_MODEL_MISMATCH`,
   `OPENFGA_BATCH_INCOMPLETE`, provider indeterminate reasons, citation
   generic 404, GraphRAG final-recheck reason) and interrupt-flag
   propagation. Characterization tests for the full failure matrix are
   written against the CURRENT three implementations and must pass unchanged
   after the swap.
2. Contract: extend the existing `AssetGovernanceActions` bundle (the
   established server-published affordance pattern) with action-specific
   per-review flags — `canApprove`, `canRequestChanges`, `canReject`,
   `canCancel` — computed by the same core predicate `decide` enforces, plus
   `canOpenGovernance` derived from the action bundle. The web client
   consumes these and deletes its local derivations
   (`governance-policy.ts` self-review arithmetic, `asset-detail-page.tsx`
   role arithmetic). Note: the current client hides REQUEST_CHANGES from the
   revision author although core permits it — the server flags fix this
   visible disagreement. OpenAPI contract regenerated via
   `OpenApiContractTests`; generated web client refreshed; absent flags read
   as false so the server deploys first.

## Non-goals

- No change to the OpenFGA model, tuples, or any persisted data.
- No change to deny-mode semantics or reason codes observable in telemetry.
- The other deferred backlog items (JDBC batching, copy-forward coordinator,
  connector polling driver, contract-generated models, skill-package
  constraint single-sourcing) are separate future increments.

## Architecture challenge

Required by AGENTS.md (authorization boundary).

- Status: **completed 2026-08-01** — cross-model adversarial review at commit
  `a47c6e27`; Decision 1 approved with must-fixes (typed policy, full
  characterization matrix), Decision 2 rejected as originally shaped and
  replaced with action-specific `AssetGovernanceActions` extensions. Record:
  [challenge-verdict.md](challenge-verdict.md) (summary) and
  [challenge-verdict-codex.md](challenge-verdict-codex.md) (full review).
