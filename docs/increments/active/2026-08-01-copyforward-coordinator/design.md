# OpenSearch Copy-Forward Coordinator

Source: full-codebase simplify review (2026-08-01), deferred items B18/B19/B20
and possible-defect V5 of the phase 2 report
(`docs/increments/completed/2026-08-01-authz-consolidation/references/phase2-codex-report.md`);
duplication sites originally reported at `OpenSearchStagedIndex` (~181),
`OpenSearchLexicalIndex` (~248), `OpenSearchVectorIndex` (~581) — verify
against current source, later merges may have moved lines.

## Problem

The generation copy-forward protocol — lock/marker CAS around copying the
previous generation's still-valid documents into a new generation before
staging writes — is implemented three times inside
`integrations/graph-rag-opensearch`, once per index adapter. The phase 2
verification confirmed the three copies have **materially different
retry/retirement behavior** (V5: a failed staged copy leaves its map entry;
the lock itself is always released in `finally`, so there is no live
deadlock, but what happens on the NEXT attempt differs per adapter). In
addition, copy-forward materializes the entire previous generation
client-side (including embeddings) and bulk-writes it back, paying
heap/network for data the server already holds.

The Postgres module already proves the single-owner shape:
`PostgresProjectionSupport.stage` owns staging/copy-forward mechanics once
for all its stores.

## Proposal (revised after architecture challenge)

The original framing ("choose one of the three protocols"; "_reindex if
conditions hold") was challenged and reshaped — see
[challenge-verdict.md](challenge-verdict.md). The challenge found none of
the three protocols complete: all lack a durable failure state and a
cross-JVM second publisher steals a live `COPYING` marker.

1. **One `OpenSearchCopyForwardCoordinator`** owned beside
   `OpenSearchStoreSupport`, standardizing a corrected protocol:
   - durable marker state machine — a live `COPYING` marker is never
     stolen; failure/abandonment is an explicit durable transition, not
     "anything non-READY is mine";
   - staged-style local lock retirement (remove-before-unlock plus
     current-map-entry validation) everywhere;
   - collision-resistant canonical target identity in marker/lock keys
     (no `hashCode()` base36);
   - per-copy-unit keys preserved as adapter-supplied typed parameters —
     no global coordinator lock, graph entity/relation stay distinct;
   - the failing projection itself is cleaned on failure, not only the
     already-successful ones;
   - crash, partial-page, split-process, and retired-lock tests exist
     before the protocol counts as standardized.
2. **Streaming copy, `_reindex` rejected.** Copy-forward rewrites
   `batch_id`/`generation`/sometimes `_id` and copies within one physical
   index for vector/content, so no-script `_reindex` cannot implement the
   contract and is rejected for the current layout (recorded — not an open
   optimization). Client-side copy becomes page→bulk streaming under count
   and byte bounds, with tests asserting every non-coordinate `_source`
   field survives byte-identically (vectors, nested structures, numerics,
   IDs).

## Non-goals

- No change to any persisted bytes, ids, fingerprints, manifests, or the
  publication lifecycle contract (PREPARING/COMMITTING/PUBLISHED/ABORTED).
- No cross-store publication-protocol unification with Postgres (phase 2
  item B23) — separate, larger increment.
- No refresh-policy change (B20) — publication visibility correctness is
  its own decision.

## Architecture challenge

Required by AGENTS.md (publication and concurrency).

- Status: **completed 2026-08-01** — cross-model adversarial review at
  `0cb8a187`; coordinator approved with a corrected protocol (no existing
  variant is complete), `_reindex` rejected for the current index layout,
  streaming approved. Record: [challenge-verdict.md](challenge-verdict.md),
  [challenge-verdict-codex.md](challenge-verdict-codex.md).
