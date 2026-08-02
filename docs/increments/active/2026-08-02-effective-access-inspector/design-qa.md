# Effective Access Inspector Design QA

Status: `BLOCKED` — browser and screenshot comparison remain required.

Reference: the selected 1440 x 1024 dark enterprise decision-brief concept,
with a named document on the left and a gate-by-gate decision timeline on the
right.

## Implemented Fidelity

- The result is a responsive two-column decision brief at large widths and a
  single-column flow below that breakpoint.
- Document title, Knowledge Space name, and classification are the primary
  resource identity. UUIDs, model IDs, and reason codes are collapsed under
  `Technical details`.
- The three visible decisions are OpenFGA relationship, Source ACL and content
  policy, and final content access.
- Copy explicitly states that Space eligibility cannot override source-owned
  restrictions. The UI does not claim a Department Space mode because the
  current persistence model cannot prove one.
- Relationship-only checks do not render raw tuple identifiers in the primary
  result.
- Existing Hanken Grotesk typography, semantic light/dark tokens, status
  surfaces, shadcn primitives, radii, and responsive page layout are retained.

## Automated Evidence

- `access-inspector.test.tsx`: named-resource hierarchy, contradictory gate
  states, relationship-only identifier suppression, and unknown semantics.
- Oxlint: pass.
- TypeScript build: pass under Node 24.18.1.
- Vite production build: pass under Node 24.18.1.
- Full web unit suite: 59 tests passed across 17 files.

## Remaining Visual Gate

The in-app Browser runtime returned no available browser instances after the
documented bootstrap and discovery checks. Consequently no rendered viewport,
interaction walkthrough, console inspection, responsive screenshot, or
side-by-side reference comparison was available. This document deliberately
does not mark visual QA as passed. The next session must open the user detail
route, inspect the denied canonical-content state at desktop and narrow widths,
exercise `Check access` and `Technical details`, and compare a same-size
screenshot with the selected reference before completing the increment.
