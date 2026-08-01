# Phase 3 Apps Verification and Fix Report

Date: 2026-08-01  
Worktree: `D:/OrgMemory-worktrees/full-codebase-review`  
Branch: `review/phase3-apps`  
Starting base: `95b717308f3467c69d60e4d23f6f168b5ce10eb9`  

## Executive Summary

- Independently verified all 11 Tier A, 15 Tier B, 10 Tier C, four possible-defect, and seven conflict/false-positive notes against the current code.
- Fixed all 11 confirmed Tier A items, four low-risk Tier B items (`B-2`, `B-8`, `B-14`, with `A-8` used instead of deeper `B-3`), and confirmed defects `D-1` and `D-4`.
- Left digest/fingerprint/cursor material byte-identical. No persisted model, Flyway, `ddl-auto`, canonical hasher, manifest fingerprint, HMAC, or source-content digest input changed.
- Committed three logical changesets; nothing was pushed. The worktree is clean.

## Tier A

| Item | Verification | Result |
|---|---|---|
| A-1 dead `SourceDocument` | Confirmed: only declaration and encoder switch existed; no constructor in main/tests. The web `SourceDocumentUIPart` is an unrelated AI SDK type. | Fixed: removed variant and switch arm. |
| A-2 unused registration repository | Confirmed: browser chain parameter was unread; logout handler independently injects the repository. | Fixed: removed browser-chain parameter. |
| A-3 dead SCIM accessor | Confirmed: zero callers of singular accessor; codec uses plural key map. | Fixed: removed accessor. |
| A-4 unused imports | Confirmed all three imports were unused. | Fixed. |
| A-5 dead scheduler fields | Confirmed all five `schedulingEnabled` components had zero callers and all five `pollInterval` components were unread. `@ConditionalOnProperty` and `@Scheduled` placeholders own the same keys. | Fixed: removed both record components/defaults/validation; scheduler annotations remain authoritative. |
| A-6 test-only graph constructor | Confirmed seven-argument constructor was called only by `GraphIndexingProcessorTests`. | Fixed: removed production constructor and added a test helper supplying test-only defaults. |
| A-7 SCIM body size | Confirmed range conversion and guards ran per request for immutable startup configuration; post-guard arithmetic catch was unreachable. | Fixed: compute one `int` in constructor; added an unrepresentable-limit test. |
| A-8 batching strategy allocation | Confirmed two per-job constructors. Local Spring AI 2.0.0 source shows immutable fields and call-local batching collections; Context7 was unavailable due quota exhaustion. | Fixed: singleton strategy per processor. |
| A-9 workload list | Confirmed hand-list exactly matched all six enum values and order. | Fixed: stream `AiWorkload.values()`. |
| A-10 Swagger patterns | Confirmed identical pattern triplet in both branches. | Fixed: one constant and one matcher registration with conditional authorization. |
| A-11 duplicate normalized text | Confirmed field and path overload were test-only; production canonicalization rebuilt the same joined content. | Fixed: removed field/reduce/path overload; tests now exercise `parse()` and assert canonical content. |

## Tier B

| Item | Verification | Result |
|---|---|---|
| B-1 temp-file single-read | Confirmed stream-to-temp plus `readAllBytes`; `DigestInputStream` feeds stored SHA verification. Removing disk buffering would also change the memory/failure envelope for non-upload source revisions. | Deferred: not low-risk under the hard digest constraint without a separately bounded byte-preservation design and characterization tests. |
| B-2 `/api/me` | Confirmed no consumer in web, MCP, or CLI; web uses `/api/session`. Only generated public docs/contracts referenced it. | Fixed: deleted controller, regenerated `contracts/openapi.json`, web client, and public docs output. `/api/me` and `MeResponse` are absent. |
| B-3 embedding port reuse | Confirmed three app-shell embedding paths and repeated profile construction remain. | Deferred: changes route/profile/dimension enforcement across API and worker; A-8 supplies the safe allocation improvement. |
| B-4 scheduler drain loop | Confirmed each scheduler claims at most one job per fixed-delay tick. | Deferred: shutdown responsiveness, starvation, and lease-heartbeat behavior require concurrency tests. |
| B-5 extraction semaphore | Confirmed fixed windows and per-window ordered wait create a barrier. | Deferred: changes timeout, ordering, cancellation, and lease-refresh semantics. |
| B-6 SCIM scope invariant | Confirmed duplicate users-only checks with `ApiRequestException` versus `IllegalArgumentException`. | Deferred: service ownership would change public error mapping/code. |
| B-7 per-user count | Confirmed single-user update executes organization-wide mapping aggregation plus a one-user identity lookup. | Deferred: low priority and requires a new repository/service query contract. |
| B-8 JSON escaper | Confirmed hand-written 28-line escaping path; error JSON has no byte-level contract and existing control-character test validates semantic round-trip. | Fixed: use Jackson 3 `JsonStringEncoder`. |
| B-9 observability conformance | Confirmed observation tests are identical after package substitution; provider tests are near-identical. | Deferred: broad test-infrastructure move, not a low-risk app behavior fix. |
| B-10 SQL fixture | Confirmed API fixture is 112 lines, worker fixture is a 51-line drifted subset with shared stable IDs. | Deferred: shared test-resource topology change. |
| B-11 Bean Validation | Confirmed manual request guards coexist with validation handling; web does not key on `request.invalid`. | Deferred: still changes externally observable HTTP error codes and bodies. |
| B-12 defaults helpers | Confirmed repeated blank/default/positive rules and `strip`/`trim` nuance. | Deferred: Unicode semantics and the proposed graph-rag validation dependency make this non-mechanical. |
| B-13 connector scope lookup | Confirmed `describe` then `resolveCredential` for the same connection. | Deferred: needs a new service transaction/view contract. |
| B-14 namespace helper | Confirmed `KnowledgeProjectionNamespaces.forSpace` returns exactly `(organizationId, "default", knowledgeSpaceId.toString())`. | Fixed: byte/tuple-identical helper substitution only. |
| B-15 fixture reparsing | Confirmed every JSON fixture is parsed before the runner checks completed checkpoints. | Deferred: staging-only optimization with cache invalidation complexity. |

## Tier C (Report Only)

- C-1 confirmed upload ACL policy is a private worker switch; no change.
- C-2 confirmed worker-owned `createdAt + 23h` freshness horizon; no change.
- C-3 confirmed duplicated embedding defaults/route guards. The actual `1536` default is a reduced dimension, not the model's native `3072`; no change.
- C-4 confirmed worker-private vector IDs and manifest fingerprint construction; no bytes or ownership changed.
- C-5 confirmed provider preset catalog lives in the controller while core enum has no metadata; no change.
- C-6 confirmed permission/explainable catalogs live in the controller; only the verified `can_manage_ai` omission was corrected under D-1.
- C-7 confirmed browser controller inspects `OAuth2AuthenticationToken`, but the precondition "no bearer manifest consumer" is false: `AssetDeliveryController` exposes bearer-authorized manifest endpoints and CLI uses manifest links. No security-chain move.
- C-8 confirmed HTTP controller owns begin/stream/complete and delete/clear choreography; no change.
- C-9 confirmed REST controller owns the 3600-second crawl default; no change.
- C-10 confirmed composite/failure-tolerant sink construction is repeated in API/core and both worker processors; no change.

## Possible Defects

| Item | Concrete verification | Result |
|---|---|---|
| D-1 missing `can_manage_ai` | Confirmed OpenFGA/admin guard and AI spec use the permission, while API catalog and web explanation choices omitted it. | Fixed in API catalog, web label, and access inspector. |
| D-2 editability drift | Confirmed both current sites allow exactly `ASSISTANT_CHAT` and `PROMPT_EXECUTION`. | False positive as a current defect; deferred predicate ownership as design work. |
| D-3 incomplete assistant turns | Confirmed: an upstream error prevents `parts(...).doOnComplete`, then `UiMessageStream.encode` converts the error into opaque error + done frames; the begun user turn remains without completion. Cancellation likewise skips completion. | Confirmed defect, deferred because safe behavior (abandon versus partial persist) requires the C-8 lifecycle decision/facade; persisting truncation in transport would be unsafe. |
| D-4 misleading scheduler binding | Confirmed record fields and scheduler annotations used the same keys, but only annotations controlled behavior. | Fixed by A-5 removal. |

## Conflict and False-Positive Checks

1. Chose B-2 deletion, not the fallback role-query optimization.
2. Chose A-5 field deletion, not SpEL rewiring.
3. Deferred B-3, therefore applied A-8 at the two listed sites.
4. Rejected graph-rag validation coupling for B-12.
5. Confirmed A-11 was test-only across the worktree; removed its test-only path overload too.
6. Recorded the 1536 reduced-dimension nuance; made no profile change.
7. Found legitimate bearer manifest surfaces in `AssetDeliveryController`/CLI; C-7 cannot be generalized as proposed.

## Generated Contracts

- `OpenApiContractTests` refreshed `contracts/openapi.json` with `ORGMEMORY_OPENAPI_WRITE=true`.
- Hey API regeneration completed; generated web client is gitignored and contains no `/api/me` operation afterward.
- Public docs regeneration updated `apps/docs/generated/openapi.public.json` and `platform.mdx`.
- Docs check reports 113 public paths across seven endpoint groups.

## Verification Gates

- Focused worker/API tests passed after implementation.
- `ORGMEMORY_OPENAPI_WRITE=true .\gradlew.bat :apps:api:test --tests "*OpenApiContractTests*"` — passed.
- `.\gradlew.bat --no-daemon --no-build-cache :apps:api:cleanTest :apps:api:test` — passed, tests executed, 3m37s.
- `.\gradlew.bat --no-daemon --no-build-cache :apps:worker:cleanTest :apps:worker:test` — passed, tests executed, 1m51s.
- `pnpm --filter @orgmemory/web lint` — passed.
- `pnpm --filter @orgmemory/web typecheck` — passed.
- `pnpm --filter @orgmemory/docs check:api` — passed.
- `git diff --check HEAD~3..HEAD` — passed.
- Node commands emitted the existing engine warning (`v23.11.1` versus repository requirement `>=24`) but completed successfully.
- Root `spotlessApply` was not available because the repository defines no such task; no formatter changes were attempted.

## Commits

- `85a9558a` `refactor(apps): remove verified redundant paths`
- `c7517fd7` `refactor(api): remove duplicate identity endpoint`
- `73c010ee` `fix(admin): publish AI management permission`

## Handoff Note

`origin/main` advanced by six commits during the run (PRs #193 and #194 / knowledge retrieval Modulith extraction). The branch started from the requested current base and is now `ahead 3, behind 6`; no merge or push was performed after the late upstream movement. Rebase/merge should be handled by the coordinator before publishing.
