# 0023 — Batch rechecks carry a typed result policy; affordances are served, not derived

Status: accepted (2026-08-01)

## Decision

1. The OpenFGA batch recheck that guards selected evidence is implemented
   once, in a collaborator whose caller must choose an exhaustive typed
   result policy: `FILTER_DENIED` (denied evidence is dropped, partial
   results are allowed — hybrid search) or `REQUIRE_ALL_ALLOWED` (any deny
   fails the whole request — citation opens and the GraphRAG final closure).
   Surface-specific deny-reason and exception mappings stay at the call
   sites; the collaborator propagates provider-indeterminate reasons without
   clearing the interrupt flag.
2. Governance decision rights shown in the browser are served by Core as
   per-decision affordances (`canApprove`, `canRequestChanges`, `canReject`,
   `canCancel`, `canOpenGovernance`) computed by the same predicate the
   mutation enforces. The browser renders the served flags and performs no
   authorization arithmetic of its own.

## Why

The recheck existed as three hand-rolled copies that had already drifted in
reason codes, and an independent cross-model architecture challenge showed
the copies were not behaviorally equivalent: the differences (filter vs
atomic failure) are product policy, not accidents. A collaborator
parameterized only by a deny-reason code would have hidden exactly that
distinction; the typed policy keeps it mandatory and visible. On the client
side, the browser's derived rule disagreed with Core — it hid Request
Changes and Reject from revision authors although Core forbids authors only
from Approve — so publishing server-computed affordances both removes the
duplication and corrects a live disagreement.

## Rejected alternatives

- A deny-code-parameterized shared helper (hides per-surface policy; the
  challenge verdict would have rejected the consolidation in that shape).
- Single `canDecide`/`canManage` booleans (semantically false against Core's
  per-decision rules; `can_manage` does not exist in the OpenFGA model).
- Keeping the three copies (the strongest counterargument: duplication keeps
  policy visible at call sites — outweighed once the policy became a
  mandatory typed parameter with characterization coverage).

Challenge record: `docs/increments/completed/2026-08-01-authz-consolidation/`
(`challenge-verdict.md`, `challenge-verdict-codex.md`).
