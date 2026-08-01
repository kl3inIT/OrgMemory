# Challenge Brief — Authorization Consolidation

Your job is to ATTACK the proposal below, not validate it. Verify every claim
in the repository source yourself; treat this brief as allegations, not facts.
You are read-only: no edits, no mutations. Read `CLAUDE.md`,
`docs/conventions.md`, the knowledge and asset-registry domain specs under
`docs/specs/domains/`, and scan `docs/decisions/` filenames for binding rules
before forming a verdict.

## Product context

OrgMemory is a governed organizational memory layer for enterprise AI work:
every piece of retrieved knowledge must be authorized per actor at serving
time, fail-closed, with OpenFGA as the relationship-authorization provider.
The product promise at stake is that no user ever sees evidence they are not
currently authorized to see, and that governance surfaces (asset review,
publication) never let an actor act beyond their rights.

## Decision under review

From `design.md` in this directory:

1. Extract the triplicated fail-closed OpenFGA batch recheck in core knowledge
   services (retrieval service, canonical evidence authorization, hybrid
   search — currently under `core/src/main/java/com/orgmemory/core/knowledge/retrieval/`)
   into ONE shared collaborator parameterized by deny-reason code, with
   characterization tests written against the current three implementations
   before the swap.
2. Publish `canDecide` (per governance review) and `canManage` (per asset)
   flags from the API (computed by existing core services), and delete the web
   client's local re-derivations (`apps/web/src/features/assets/governance-policy.ts`
   self-review prohibition; `asset-detail-page.tsx` role-assignment
   arithmetic).

## Questions you must answer with repository evidence

- Are the three recheck implementations actually equivalent in observable
  behavior (reason codes, interrupt handling, empty-result handling, deny
  mode), or do the differences encode intentional per-surface policy? Cite
  file:line for every difference.
- Does a shared collaborator weaken anything: does any call site need to
  diverge in the future for a stated product reason?
- For the flags: does the server already compute these verdicts somewhere
  (asset-registry authorization services), or would the API layer grow new
  policy? Would publishing flags create a second source of truth vs the
  existing grant/permission-catalog pattern (`grantOptions()` precedent cited
  in the phase 5 report)?
- Contract impact: is adding fields to those endpoints backward-compatible for
  the generated web client and MCP/CLI consumers?
- What does the comparable system do? Read `tmp/onyx` (Onyx source, if present
  in this worktree or at D:/OrgMemory/tmp/onyx) for how it scopes
  document-set/persona permissions at serving time vs client-side.

## Motivating cost

The full-codebase simplify review (see `references/phase1-codex-report.md`
item B-2 deferral and `references/phase5-codex-report.md` item C2) found the
three backend copies already drifted in reason codes/structure, and phase 5
confirmed a user-facing defect family (client-derived rules disagreeing with
server behavior) in the same area.

## Required output

A structured verdict in plain Markdown: VERDICT (approve / approve-with-must-fix
/ reject) for each of the two decisions separately; must-fix list; the
strongest counterargument you found; repository evidence (file:line) for every
claim; scope limits of your verdict.
