# Plan — Authorization Consolidation

Design: [design.md](design.md). Verdict: [challenge-verdict.md](challenge-verdict.md).
Working branch: `increment/authz-consolidation` in
`D:/OrgMemory-worktrees/full-codebase-review`.

## Step 1 — Characterization tests (before any production change)

Write tests against the CURRENT three implementations in
`core/src/main/java/com/orgmemory/core/knowledge/retrieval/`
(`CanonicalEvidenceAuthorizationService`, `CanonicalHybridKnowledgeSearch`
recheck section, `GraphRagKnowledgeRetrievalService` final closure) covering,
per surface:

- empty input / upstream-empty handling (citation rejects empty with
  `IllegalArgumentException`; search and graph return allowed-empty)
- duplicate resources in the batch
- mixed allow/deny (search filters and keeps partials; citation and graph
  fail whole request)
- missing decisions, outer model mismatch, per-decision model mismatch
  (`AUTHORIZATION_MODEL_MISMATCH` vs collapsed reason codes per surface)
- provider timeout / unavailable / interrupted (interrupt flag preserved,
  `OPENFGA_INTERRUPTED` propagated) — including the batchCheck path the
  existing adapter test does not cover
- audit reason codes and exception types observable per surface

Gate: `.\gradlew.bat :core:test` green with the new tests on unchanged
production code.

## Step 2 — Typed collaborator

Introduce the collaborator with a mandatory typed policy
(`FILTER_DENIED` | `REQUIRE_ALL_ALLOWED`) and per-surface reason mapping
supplied by the caller as a typed mapping, not booleans. Swap the three call
sites one at a time; Step 1 tests must pass unchanged after each swap. No
telemetry-observable reason code changes.

## Step 3 — Governance affordances

- Core: extend `AssetGovernanceActions` computation in `AssetRegistryService`
  with `canApprove`, `canRequestChanges`, `canReject`, `canCancel` for the
  open review (same predicate `AssetRegistryCoordinator.decide` enforces,
  including the author-only-blocked-from-APPROVE rule and requester-only
  CANCEL rule and `IN_REVIEW` state gate) and `canOpenGovernance` derived
  from the bundle. Unit tests per flag against the coordinator rules.
- API: fields ride the existing endpoints (additive). Refresh
  `contracts/openapi.json` via `OpenApiContractTests`.
- Web: regenerate hey-api client; replace `governance-policy.ts` derivations
  and `asset-detail-page.tsx` role arithmetic with the served flags (absent →
  false); update the workspace to show REQUEST_CHANGES/REJECT to authors per
  server policy. Delete dead local policy code.

## Step 4 — Gates

- Backend: terminating clean `.\gradlew.bat --no-daemon test`.
- Web: `pnpm lint`, `typecheck`, `test:unit`, `build` in `apps/web`.
- Contract: `OpenApiContractTests` regenerated output committed.

## Step 5 — Consolidation (after merge)

- Specs: reflect the typed recheck policy in
  `docs/specs/domains/secure-retrieval.md` / `secure-graph-rag.md` if their
  wording names the implementations; update the asset-registry domain spec
  for the new affordance flags; refresh `Source:`/`Reconciled:` lines.
- Decision entry: add `docs/decisions/` entry recording the consolidation,
  the strongest counterargument, and the rejected alternatives (from the
  verdict).
- Move this increment to `docs/increments/completed/`; update roadmap.

## Out of scope

Everything listed under design.md Non-goals, including JDBC batching and the
other backlog batches.
