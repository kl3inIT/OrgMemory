# Phase 5 Frontend Verification and Fix Report

Date: 2026-08-01  
Branch: `review/phase5-web`  
Scope: `apps/web` and `apps/docs` only  
Result: Tier A completed, all three possible defects verified and fixed, four low-risk Tier B items completed, Tier C left report-only, no push performed.

## Executive summary

- Verified every worklist item against the current repository before changing it. False-positive and conflict claims were explicitly resolved rather than applied mechanically.
- Delivered four logical commits:
  - `1470eb58 refactor(frontend): remove unused component surfaces`
  - `ff6560f6 build(web): stop generating unused zod schemas`
  - `a220fc85 refactor(web): consolidate shared frontend helpers`
  - `92a283fb refactor(web): apply safe runtime efficiencies`
- The final working tree is clean. Every selected Tier B item passed its own web lint/typecheck/unit/build gate, and the final state passed the same full gate again.

## Baseline integration caveat

The dispatched branch was not actually based on the current `origin/main`: its pre-Phase-5 base is `78b4d503`, while the observed `origin/main` is `250e1705` and contains later merged PRs #197 and #200. The four commits above are scoped only to `apps/web` and `apps/docs`; I did not rebase or merge after finishing because the coordinator explicitly directed that current `origin/main` will be merged during the PR loop and frontend gates rerun there.

## Tier A verdicts

| Item | Verdict | Evidence and action |
|---|---|---|
| A1 | Confirmed and fixed | `ui/chart.tsx` had no importers and was the only `recharts` consumer. Deleted the file and removed `recharts`. |
| A2 | Confirmed with claim correction, then fixed | The two `@react-sigma/*` packages had no importers, so they were removed. The worklist's broader efficiency wording was rejected: the graph still intentionally uses the raw Graphology layout implementation, so that active dependency/code was retained. |
| A3 | Confirmed and fixed in an isolated commit | The generated Zod module had no importer. Removed the hey-api `zod` plugin and web `zod` dependency, regenerated the client, and passed `check:api`. The docs app's independent Zod dependency remains. |
| A4 | Confirmed and fixed | Reduced vendored AI element files to the exports actually imported by the assistant page/answer flow; removed unused attachment, toolbar, branch, citation navigation, and related surfaces. |
| A5 | Confirmed and fixed | Removed only unused sidebar subcomponents/exports. Live components such as `SidebarGroupLabel` remain because they have current consumers. |
| A6 | Confirmed and fixed | `currentReturnPath` had no importer and was deleted. |
| A7 | Confirmed and fixed | Removed dead docs MDX pattern components and their MDX registry entries. Docs checks passed. |
| A8 | Confirmed and fixed | Added one shared binary-unit `formatBytes` helper and migrated all duplicated implementations. Added focused unit coverage. |
| A9 | Confirmed and fixed | Added one shared date formatter supporting the existing date/time variants and migrated duplicated formatters. |
| A10 | Confirmed and fixed | Removed the language-model page's lossy local helper and reused the shared tested RFC-7807-aware `apiErrorMessage`. |
| A11 | Confirmed and fixed | Lifted `CopyButton` to `components/patterns`, centralized guarded clipboard behavior in `lib/copy.ts`, and migrated all callers. |
| A12 | Confirmed and fixed | Extracted and reused `resetExploration()` in the knowledge graph panel. |
| A13 | Confirmed and fixed | Removed thirteen one-line admin query-option wrappers and applied the behaviorful generic admin query policy at call sites. |

## Conflict ledger

- **A2 vs B1 efficiency claim:** A2 was dependency dead-code removal only. Active Graphology/Sigma rendering code was not treated as dead. B1 was separately justified because the static graph-panel import placed the active graph stack in the Sources route chunk.
- **A3 vs C8 runtime role derivation:** removed the unused Zod generator instead of retaining it solely as a runtime enum carrier. Kept the hand-written role array and added a source-of-truth comment pointing to the generated OpenAPI `UserRole` union. A future runtime-generated enum remains a contract/tooling decision.
- **C4 vs D2:** pulled forward only the mechanical half: one browser limits module and consistent binary wording. Publishing upload constraints in OpenAPI remains Tier C.
- **C5 vs D3/B6:** fixed the confirmed incorrect confidential filter and derived the mutation input union from generated `UploadSourceData`; did not redesign the upload-targets response contract.

## Possible defects

| Defect | Verdict | Fix |
|---|---|---|
| D1 | Confirmed | The local language-model error formatter accepted only `Error.message`, while the shared helper extracts RFC-7807 `detail` and `title`. Replaced the local helper; existing shared helper tests cover the richer response shapes. |
| D2 | Confirmed | The same 1024-based package sizes were labeled both KB/MB and KiB/MiB, and the 20 MiB cap had conflicting wording. Standardized on binary math with KiB/MiB labels, centralized the limit, and added formatter tests. |
| D3 | Confirmed against backend behavior | Backend upload derivation requires a department only for `CONFIDENTIAL`; PUBLIC, INTERNAL, and RESTRICTED use different ACL policies. Changed the client filter so only confidential uploads require a department-backed space and left a contract-migration breadcrumb. |

## Tier B verdicts

| Item | Verdict | Evidence and action |
|---|---|---|
| B1 | Implemented | Radix Tabs unmounts inactive content without `forceMount`, but the static import still loaded graph code with the route. Added `React.lazy` and a stable `Suspense` loading state. Production output now has a `sources` chunk around 16.4 kB plus separate `knowledge-graph-panel` (~283.7 kB) and Cytoscape (~435.3 kB) chunks. Authenticated visual tab-switch verification remains advisable after the coordinator merges current main. |
| B2 | Deferred | Debouncing URL navigation is behavior-visible and changes deep-link/history timing. No low-risk change was justified without browser acceptance coverage. |
| B3 | Deferred | The installed AI SDK implementation exposes the current messages array but does not guarantee stable message-object identity across streaming updates. A plain per-message `React.memo` could be ineffective; a custom comparator risks stale rendered parts/callbacks. Context7 was attempted but its quota was exhausted, so the installed package source was used for verification. |
| B4 | Implemented | Kept conversation-list invalidation unchanged and added `refetchType: "none"` only to the just-streamed conversation-history invalidation, preventing the redundant active transcript refetch while retaining stale marking. |
| B5 | Deferred for security | Citation preview data is permission-sensitive, but its current query/cache identity has no authorization generation. Adding a long-lived blob cache without a permission-generation boundary risks stale authorized content surviving an auth change. |
| B6 | Implemented | Confirmed global CSRF transport is configured in `api-client.ts`. Replaced the handwritten wrapper with generated `uploadSourceMutation()`, derived classification from generated `UploadSourceData`, and deleted the wrapper/type duplicate. |
| B7 | Deferred | Unifying connect/edit dialogs changes form initialization, validation, pending state, and remount semantics. It is not a safe mechanical consolidation without browser regression coverage. |
| B8 | Deferred | The flagged effects coordinate router/store/default-selection state. Removing or collapsing them can change first-load and cross-space selection semantics; this requires focused behavior tests rather than a cleanup-only patch. |
| B9 | Implemented | Replaced three drifting label/value tile implementations with one feature-level `MetadataTile` using the canonical asset-detail padding and typography. |

## Tier C report-only findings

No Tier C item was implemented.

| Item | Verification result / recommendation |
|---|---|
| C1 | Confirmed routes do not use loaders and cold route/data work can serialize. Adopt loaders only through an app-wide TanStack Router data-loading convention with route-level tests. |
| C2 | Confirmed governance/manage/publish capability decisions are partly reconstructed in the browser. Add server-published action flags through backend, OpenAPI, regeneration, and authorization tests. |
| C3 | Confirmed prompt/work-instruction payloads are hand-cast while Pack/Skill have typed endpoints. Add typed profile schemas/endpoints rather than more browser casts. |
| C4 | Mechanical browser consolidation is complete; publish server-owned archive constraints in the contract as the remaining design step. |
| C5 | Immediate D3 bug and B6 type duplication are fixed. Extend upload-target responses with admissible classifications so the browser stops encoding ACL policy. |
| C6 | Confirmed connector vocabulary duplication, but current code documents a deliberate trade-off. Revisit at a fourth connector or first observed drift incident. |
| C7 | Confirmed source status remains a string in the contract. Add a server/OpenAPI enum before seeking exhaustive browser mappings. |
| C8 | Resolved with A3 as described in the conflict ledger. A future non-Zod generated runtime enum could remove the remaining manual array. |
| C9 | Not promoted. Production Nginx serves `/mcp` same-origin, but Vite dev proxies only `/api`; deriving from `window.location.origin` would produce a broken dev endpoint. Publish the MCP endpoint via runtime/session config or add a verified dev proxy contract first. |
| C10 | Confirmed `limit: 5` is pinned in the browser transport. Verify and document a backend default, then remove the client-owned evidence budget in a contract-aware change. |

## Verification gates

- **A1/A2/A4-A7 deletion batch**
  - Web: `pnpm lint`, `pnpm typecheck`, `pnpm test:unit`, `pnpm build` — passed.
  - Docs (touched): `pnpm lint`, `pnpm typecheck`, `pnpm build` — passed.
- **A3 isolated generated-client commit**
  - `pnpm gen:api`, `pnpm check:api` — passed.
  - Full web lint/typecheck/unit/build — passed.
- **A8-A13 plus D1-D3**
  - Full web lint/typecheck/unit/build — passed; 12 test files, 46 tests.
- **B1, B4, B6, B9**
  - Each item independently passed full web lint/typecheck/unit/build before proceeding.
- **Final amended state**
  - `pnpm lint` — passed.
  - `pnpm typecheck` — passed.
  - `pnpm test:unit` — 12 files / 46 tests passed.
  - `pnpm build` — passed.
- The web package has no `test` script; `test:unit` is its configured Vitest gate.
- Docs emitted an engine warning because the worker runtime is Node `23.11.1` while docs declares Node `>=24`; checks still passed. The coordinator should rerun on the project-standard Node 24 environment after merging current main.
- Vite continues to warn about unrelated chunks over 500 kB. B1 successfully split the graph route payload, but the broader warning is not fully eliminated.

## Files and delivery

- All repository changes are contained in the four commits listed above and only touch `apps/web`, `apps/docs`, and the shared `pnpm-lock.yaml`.
- Working tree: clean.
- Push: not performed.
- Remaining integration step: merge current `origin/main` into this branch as directed, resolve any current-main overlap, then rerun web gates, docs gates if affected, `check:api`, and authenticated browser verification for the Sources graph tab and upload classification/space combinations.
