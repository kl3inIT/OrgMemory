# Challenge Brief — OpenSearch Copy-Forward Coordinator

Your job is to ATTACK the proposal in `design.md` (same directory), not
validate it. Verify every claim in the repository source yourself. You are
read-only: no edits, no mutations, nothing written to disk. Read
`CLAUDE.md`, `docs/conventions.md`, the secure-graph-rag and
knowledge-ingestion domain specs, and scan `docs/decisions/` filenames
(0024 and the publication-related entries matter) before forming a verdict.

## Product context

OrgMemory publishes graph/index projections as immutable generations:
staging writes build a new generation, publication switches atomically, and
readers never see a partial generation. Copy-forward moves the previous
generation's still-valid documents into the new one before staging deltas.
The promises at stake: a generation is complete-or-absent, concurrent
publishers cannot corrupt each other, and copy-forward never alters stored
bytes (fingerprint/manifest material).

## Decisions under review

1. Extract the three per-adapter copy-forward lock/marker/copy
   implementations into one `OpenSearchCopyForwardCoordinator` with ONE
   explicit retry/retirement protocol.
2. Replace client-side materialize-and-rewrite with server-side `_reindex`
   IF byte-identity, failure mapping, and no-scripting conditions hold;
   otherwise stream page→bulk.

## Questions you must answer with repository evidence

- Enumerate the three current protocols precisely: lock key shape, marker
  states, what a second publisher observes mid-copy, and what happens on
  retry after a failed copy (the phase 2 V5 finding says the map entry
  survives — what does each adapter do with it?). Cite file:line for every
  difference. Which of the three behaviors is correct to standardize on,
  and does any difference encode a real per-index requirement?
- Is there any interleaving today where the staged index's copy-forward
  interacts with lexical/vector copy-forward for the same generation (one
  publication touching all three), and would one coordinator change the
  cross-index ordering or failure story?
- For `_reindex`: does the OpenSearch Java client used here support it with
  the cluster permissions the deployment grants? Are stored documents
  copied byte-identically (no mapping-driven coercion, no script)? How do
  partial `_reindex` failures surface, and can they map to the existing
  whole-operation failure? What happens to `_reindex` on very large
  generations (task timeout, throttling)?
- Does copy-forward feed ANY fingerprint/manifest/digest computation at
  publication time, directly or via read-back? If yes, name the exact path
  — this decides whether server-side copy is even admissible.
- Comparable system: how does Onyx (D:/OrgMemory/tmp/onyx) move documents
  between index generations or handle reindexing — client-side or
  server-side, and with what locking?

## Required output

Structured verdict in plain Markdown: VERDICT per decision
(approve / approve-with-must-fix / reject), the protocol you recommend
standardizing on (with evidence), must-fix list, strongest counterargument,
file:line evidence for every claim, scope limits.
