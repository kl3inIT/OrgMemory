# Plan — Copy-Forward Coordinator

Design: [design.md](design.md). Verdict: [challenge-verdict.md](challenge-verdict.md)
(must-fix list in [challenge-verdict-codex.md](challenge-verdict-codex.md) is
binding). Working branch: `increment/copyforward-coordinator` in
`D:/OrgMemory-worktrees/full-codebase-review`.

## Step 1 — Characterization of current behavior

Tests against the CURRENT three implementations
(`OpenSearchStagedIndex`, `OpenSearchLexicalIndex`, `OpenSearchVectorIndex`
in `integrations/graph-rag-opensearch`) pinning: marker/lock key shapes,
same-JVM blocking + marker re-read, READY short-circuit, and read-back
equality of copied documents (all `_source` fields incl. vectors, nested
structures, numerics, IDs). These document the status quo the coordinator
must preserve where it is correct.

Gate: `.\gradlew.bat :integrations:graph-rag-opensearch:test` green on
unchanged production code.

## Step 2 — Coordinator with the corrected protocol

`OpenSearchCopyForwardCoordinator` beside `OpenSearchStoreSupport`:

- Durable marker states `COPYING` / `READY` / `FAILED` with owner and
  timestamps; a live `COPYING` is never CAS-stolen — takeover requires an
  explicit durable `FAILED` (written by the failing owner's cleanup) or a
  documented expiry rule implemented as a durable transition.
- Local lock retirement: remove-before-unlock + current-map-entry
  validation (the staged helper's shape) for every adapter.
- Marker/lock keys from adapter-supplied canonical target identity (exact
  strings, not hashCode; graph entity/relation remain distinct copy units).
- Copy step is page→bulk streaming with configurable count and byte
  bounds; on failure the coordinator cleans the failing projection's
  partial output and writes `FAILED`.
- Adapters delegate; index naming/document mapping stay adapter-side as
  typed parameters.

New tests: cross-process simulation (two coordinator instances, separate
lock maps, one durable store) proving live-COPYING is not stolen and FAILED
enables takeover; crash/partial-page cleanup; retired-lock reuse; streaming
bounds; `_source` byte-identity on the streamed path.

Step 1 characterization must pass unchanged wherever the old behavior was
correct; intentional protocol corrections (live-COPYING steal removed)
update the corresponding characterization with a comment referencing the
verdict.

Gate: `.\gradlew.bat :integrations:graph-rag-opensearch:test` green.

## Step 3 — Full gates

Terminating clean `.\gradlew.bat --no-daemon test`. No contract or frontend
changes expected.

## Step 4 — Consolidation (after merge)

- secure-graph-rag spec: record the single copy-forward protocol if the
  spec names staging mechanics; refresh `Source:`/`Reconciled:`.
- Decision entry: corrected copy-forward protocol; `_reindex` rejected for
  the current layout; strongest counterargument (real per-index structural
  differences) and how typed parameters preserve them.
- Move increment to completed; roadmap row shipped.

## Out of scope

Publication-protocol unification with Postgres, refresh policy, index
layout redesign, `_reindex` revisit.
