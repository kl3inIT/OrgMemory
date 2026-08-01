# Challenge Verdict — Authorization Consolidation

- Date: 2026-08-01
- Commit reviewed: `a47c6e27` (branch `increment/authz-consolidation`, from
  `origin/main`)
- Reviewer: independent adversarial session on a different model family
  (Codex gpt-5.6-sol, reasoning high), read-only, driven by
  [challenge-brief.md](challenge-brief.md). Full verdict:
  [challenge-verdict-codex.md](challenge-verdict-codex.md).
- A second same-family reviewer was started but lost to a session restart; it
  was not re-run because the surviving verdict is adversarial on its face (it
  rejected half the proposal with file-level evidence), so the "agrees too
  easily" escalation rule did not trigger.

## Committed recommendation

1. **Shared OpenFGA batch-recheck collaborator: proceed, reshaped.** The three
   implementations are NOT observably equivalent — hybrid search filters
   denied evidence and returns partial results, citation collapses every
   failure to a generic 404, GraphRAG fails the whole request atomically per
   `docs/specs/domains/secure-graph-rag.md`. The collaborator must take a
   mandatory typed result policy (`FILTER_DENIED` vs `REQUIRE_ALL_ALLOWED`),
   preserve each surface's reason/exception mapping, propagate provider
   indeterminate reasons without clearing the interrupt flag, and land only
   behind a characterization-test matrix covering empty inputs, duplicates,
   mixed allow/deny, missing decisions, model mismatch (outer and
   per-decision), timeout/unavailable/interrupted, audit reasons, and
   partial-vs-atomic output.
2. **Single `canDecide`/`canManage` booleans: rejected.** Core forbids a
   revision author only from APPROVE (`AssetRegistryCoordinator`), not from
   REQUEST_CHANGES/REJECT; the browser's single boolean already disagrees
   with the server, and publishing it would freeze that disagreement into the
   contract. `can_manage` does not exist in the OpenFGA model. Instead:
   extend the existing `AssetGovernanceActions` bundle (already the
   server-published affordance pattern) with action-specific per-review flags
   — `canApprove`, `canRequestChanges`, `canReject`, `canCancel` — computed by
   the same core predicate `decide` enforces, plus `canOpenGovernance`
   derived from the action bundle. The web client consumes these and deletes
   `governance-policy.ts` self-review arithmetic and `asset-detail-page.tsx`
   role arithmetic. Server deploys before web; absent flags read as false.

## Strongest counterargument (recorded)

Keeping the three copies is safer than a generic abstraction because the
duplication keeps three materially different product failure policies visible
at their call sites; a collaborator with boolean/callback parameters would
hide exactly the distinction that prevents partial disclosure. Accepted
response: the policy mode is mandatory, typed, and exhaustive — if that shape
cannot be kept, the consolidation is rejected.

## Rejected alternatives

- One collaborator parameterized only by deny-reason code (original design) —
  unsafe, hides per-surface policy.
- Publishing `canDecide`/`canManage` booleans (original design) —
  semantically false against current core policy.
- Status quo (three drifting copies, client-side authorization arithmetic) —
  the drift and the existing client/server disagreement are the motivating
  defects.

## Scope limits

Static review of this snapshot only; no runtime executed. External API
consumers outside this repository were not inventoried — additive contract
fields only, server-first rollout.
