# Phase 4 Codex Report — MCP, CLI, and Contracts

Date: 2026-08-01  
Worktree: `D:/OrgMemory-worktrees/full-codebase-review`  
Branch: `review/phase4-mcp-cli-contracts`

## Outcome

Verified every Tier A, Tier B, Tier C, and possible-defect item against the current branch before editing. Implemented all confirmed Tier A fixes, low-risk Tier B items B1/B2/B4/B6, D1, and the live D2 parity defect. Left Tier C untouched, rejected B5's proposed optimization because its parity precondition is false, and deferred B3 because the replacement changes the user-visible timeout error.

Commits:

- `b83e0256 refactor(mcp): simplify gateway internals`
- `99ef3a78 refactor(cli): share package safety helpers`

No push was performed.

## Tier A Verification

| Item | Verification | Result |
| --- | --- | --- |
| A1 | The `OAuth2AuthorizedClientProvider` local was used once. | Fixed by passing `tokenExchange` directly. |
| A2 | Both UUID parsers enforced the same message and `UUID.fromString` boundary, although the “byte-identical” claim was overstated because neither current copy preserved the cause. | Consolidated in `McpValues.assetIdentifier`; the sanitized MCP boundary still prevents the cause from reaching callers. |
| A3 | Both CLI sites manually allocated and copied all chunks. | Replaced with `Buffer.concat(chunks, length)` / `Buffer.concat(chunks, fileSize)`. |
| A4 | The same `ENOENT` predicate appeared three times. | Consolidated as `isENOENT` in `apps/cli/src/shared.ts`. |
| A5 | `NAMESPACE` duplicated exported-contract behavior except `namespaceSchema` was not exported. | Exported and reused `namespaceSchema.safeParse`. |
| A6 | `new URL("/skill-publications", serverUrl)` makes the constant path/origin/search/hash guards unreachable. | Reduced `publicationUrl` to the direct constructor and renamed the misleading test. |
| A7 | Spring profile-specific property sources override individual flattened keys and retain absent base keys; official Spring Boot external-configuration docs confirm profile files have higher precedence. | Removed duplicate registration, timeout, audience, endpoint-exposure, and rate-limit properties; added a profile-source merge test. Context7 was attempted first but quota was exhausted, so official Spring docs were used as the required fallback. |
| A8 | `Uint8Array.from` cloned deterministic ZIP bytes solely for `Blob`. | Passed the archive directly; refined the produced archive type to `Uint8Array<ArrayBuffer>` so strict Node typings remain valid without a clone. |
| A9 | `blankToNull` and the completion inline expression were equivalent. | Consolidated as `McpValues.optionalText` without adding a `core` dependency. |

## Tier B Verification

| Item | Verification | Result |
| --- | --- | --- |
| B1 | The two internal exception classes had the same shape; Asset and Knowledge clients duplicated the same null/status/transport boundary; auth threw the asset-specific type. No class name is serialized outward. | Replaced them with `McpGatewayException` and `GatewayRequests`. Knowledge HTTP 400 now maps deliberately to `The knowledge search request is invalid`; a focused test pins sanitization and the separate branch. `SkillPublicationApiClient` remains separate. |
| B2 | The two path validators accept/reject the same path set despite ordering/message drift. Both atomic writers used PID + 6 random bytes, `0o600`, rename-over, and forced temp cleanup. Both SHA helpers were identical. | Added `shared.ts` with `requireSafeRelativePath`, `sha256`, `atomicWriteJson`, and `isENOENT`; characterization tests cover path sets, digest, JSON formatting, temp cleanup, and POSIX mode. |
| B3 | Node 24.15.0 reproduces the behavior change: manual abort is `AbortError: This operation was aborted`; `AbortSignal.timeout` is `TimeoutError: The operation was aborted due to timeout`. The CLI prints `error.message` directly. | Confirmed but not changed because this is an observable CLI UX change, so it is outside the low-risk subset. |
| B4 | `trackedCallerCount` had one test-only caller. The test already proves two caller/client buckets are independently admitted by verifying two downstream invocations. | Removed the hook and its structural assertion. |
| B5 | The canonical delivery query searches `namespace`, `slug`, `title`, and `summary`. MCP local completion additionally matches asset UUID, release UUID, and `versionLabel`. Passing the typed prefix as `q` would remove valid UUID/version completions before residual filtering. | Rejected the proposed change because the stated field-parity condition is false. The resolved-release `getAsset` fast path should be considered separately with explicit completion-contract tests. |
| B6 | Root stripping of an already safe, unique path cannot introduce traversal, but the second check is deliberate defense-in-depth. | Kept the check, pointed it at the shared validator, and documented the invariant. |

## Tier C Verification

All six items are live design concerns and received no code change:

- C1 confirmed: MCP response records and CLI Zod models are hand-maintained mirrors; generation requires an owned/published gateway contract and an architecture decision.
- C2 confirmed: package constraints are repeated across core, MCP controller/body cap, CLI authoring, and CLI install-contract validation; deleting MCP validation remains sign-off gated.
- C3 confirmed: CLI derives the Governance SPA route and assumes a shared origin; moving ownership into the publication response is a cross-service contract decision.
- C4 confirmed: the crawl-batch JSON Schema is hand-maintained; connector fixture tests deserialize Java but do not validate fixtures against the committed schema, and no JSON Schema validator dependency exists.
- C5 confirmed: `SkillPackageController` loads the manifest for headers and then calls `copySkillPackage`, causing two authorized downstream calls; collapsing them requires canonical response-header contract work.
- C6 is the same delegate-vs-local validation-removal decision as C2(b), so it remains report-only.

## Possible Defects

### D1 — confirmed and fixed

`McpApiAuthorization` claimed exchanged-token caching while `NonPersistingAuthorizedClientRepository` always returns `null` and intentionally saves nothing. The Javadoc now states the per-request exchange and subject-token-binding reason. Token-exchange behavior was not changed.

### D2 — one live drift found and fixed

The archive limits, file count, `SKILL.md` limit, allowed frontmatter fields, name regex, string limits, classification set, manifest limits, and 20 MiB package cap agree. The MCP 23 MiB value is intentionally the multipart request-body cap, not a competing archive cap.

One real mismatch existed: core rejects whitespace-only metadata keys with `String.isBlank()`, while CLI checked only `!key` and accepted them. CLI now checks `!key.trim()`, and a regression test pins parity with the canonical inspector. A full generated/shared five-encoding parity gate remains the C2 architecture decision.

## Off-Limits Audit

- Per-tool-call downstream token exchange remains non-persisting and unchanged.
- Install-time package/per-file SHA-256 verification remains unchanged; only the helper location changed.
- Deterministic sorted package walk and fixed-time ZIP digest behavior remain unchanged.
- OAuth state-file SHA-256 key derivation remains unchanged.

## Verification Gates

- Java compile: `:apps:mcp:compileJava :apps:mcp:compileTestJava` passed.
- MCP terminating clean module gate: `:apps:mcp:clean :apps:mcp:test` passed, 59 tests.
- CLI lint equivalent: Oxlint from the existing `apps/web` workspace package against `apps/cli/src` passed; `apps/cli` has no own `lint` script/dependency.
- CLI typecheck: `pnpm typecheck` passed on Node 24.15.0.
- CLI tests: `pnpm test` passed, 41 tests across 5 files.
- CLI build: `pnpm build` passed.
- Mechanical checks: `git diff --check`, Java package-line scan, zero-byte Java/YAML scan, and stale-duplicate symbol scan passed.
- JetBrains MCP inspection was unavailable in this worker session; Gradle compile/test gates were used.
- The module has no Spotless/formatter task (`:apps:mcp:spotlessJavaCheck` does not exist), so no formatter gate was claimed.

## Remaining Risks / Follow-up

1. Decide whether B3's improved but changed timeout message is acceptable, or normalize it explicitly before adopting `AbortSignal.timeout`.
2. Do not implement B5 until the API query contract includes UUID and `versionLabel` parity or completion gets a separately tested scoped lookup design.
3. Run the required architecture challenge before any C1–C6 contract, validation-boundary, or publication redesign.
4. C2 still needs a durable cross-language parity mechanism; the immediate drift is fixed, but the structural risk remains.
5. `origin/main` advanced by four commits during the worker run; the branch is clean and ahead by the two commits above, but the coordinator should reconcile the new base before integration.
