# Knowledge Operations And Graph Inspector Verification

## Outcome

The Documents surface now uses a true master-detail reader on desktop while
retaining an accessible modal Sheet on smaller viewports. The list remains
interactive, classification copy describes the real policy composition, and
FAILED/QUARANTINED details are visible without hover. Rejected evidence opens a
fresh corrected-upload flow.

The graph panel is now an OrgMemory entity inspector rather than a raw-property
card. It exposes readable actions, entity description, neighbor-first
connections with direction, and permission-rechecked document title plus
heading/page evidence instead of `Source 1`.

The independent architecture challenge rejected manual FAILED retry. No retry
endpoint or affordance was added. Source-ingestion claim epochs, exact Asset
publication fencing, and manifest-pinned recovery remain explicit backlog work.

## Reference And Architecture Evidence

- LightRAG `v1.5.4` pin `9a45b64c2ee25b1d806e90db926a8af37480bb16`
  informed selected-node, neighbor-navigation, expand, and hide interactions;
  its generic property presentation was not copied.
- Onyx pin `618b5031bf21463f44e3bed9eb9d5073b806fec0`
  confirmed that processing state and file operations should remain distinct.
- The challenge at base `876246da` found that Source Ingestion has no
  never-reused claim epoch across the multi-transaction Asset-publication
  boundary. The committed rejection and future test matrix are in
  `challenge-verdict.md`.

## Automated Evidence

Verified on Node `v24.15.0`:

- `corepack pnpm --dir apps/web lint`: passed.
- `corepack pnpm --dir apps/web typecheck`: passed.
- `corepack pnpm --dir apps/web test:unit`: 20 files and 67 tests passed.
- focused Documents and graph browser tests: 3 tests passed.
- full `playwright test --workers=4`: 31 tests passed.
- `corepack pnpm --dir apps/web build`: passed.
- `corepack pnpm release:check`: 18 Tegami contract tests and 23 release-policy
  tests passed; release-management check passed.
- `python scripts/check_docs.py`: 515 Markdown files and 8 mirrored domain pairs
  passed after completion archival.
- `git diff --check`: passed before consolidation.

## Visual Evidence

Deterministic desktop captures at 1459 by 816 confirmed:

- the document list remains visible and operable beside Markdown metadata and
  evidence content;
- status/failure rows remain readable without horizontal overflow;
- the graph inspector has a stable bounded column, readable information
  hierarchy, visible actions, directional neighbor context, and real evidence
  labels.

The responsive Documents browser journey additionally proves the modal mobile
reader and no page-level horizontal overflow at 390 by 844.

## Scope And Remaining Risk

No backend Java, API, persistence, ingestion, OpenFGA model, or authorization
contract changed, so Gradle, generated API-client drift, OpenFGA model tests,
and IDE Java inspection were not applicable. Citation metadata still rechecks
permission server-side. Browser evidence is deterministic fixture coverage, not
a production deployment proof. Vite retains the repository's existing
non-blocking large-chunk warning.
