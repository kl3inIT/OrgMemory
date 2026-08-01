# Phase 1 Codex Verification and Fix Report

Date: 2026-08-01  
Worktree: `D:/OrgMemory-worktrees/full-codebase-review`  
Branch: `review/full-codebase-main`  
Verified base: fast-forwarded to `origin/main` at `c893635` before changes  

## Executive summary

- Independently checked every Tier A, Tier B, Tier C, and defect item against the current source rather than accepting reviewer claims at face value.
- Fixed the confirmed critical/major defects, the confirmed CSV defect, and the Tier A changes that were actually mechanical and behavior-preserving.
- Did not change canonical framing or any bytes fed into persisted digests, fingerprints, manifests, chunk identities, or cache keys. The SHA-256 refactor delegates already-prepared strings to one implementation without changing caller-side canonicalization.
- Made no persisted-model or Flyway changes and left `ddl-auto=validate` intact.
- No Tier B candidate was sufficiently low-risk after source verification; all are explicitly deferred below. Tier C is report-only as required.

## Commits

1. `6dca7602 fix(core): recover interrupted indexing and publication`
2. `6fa2d793 refactor(core): remove verified duplicate and dead paths`
3. `e54f8a12 refactor(core): centralize sha256 implementation`

## Defects

| ID | Verification | Disposition |
|---|---|---|
| DEF-1 | **Confirmed critical.** A cancellation flag on a `PROCESSING` job could select the same job repeatedly after lease expiry; the cancellation transition was rolled back with `GraphIndexingStoppedException`, so the globally ordered queue could remain wedged. | **Fixed.** Cancelled candidates are terminalized during claim, and stop exceptions no longer roll back the required lifecycle transitions. Added a regression test for the transactional failure path. |
| DEF-2 | **Confirmed major.** Once `stage(...)` committed its blob reference in a `REQUIRES_NEW` transaction, the outer catch still deleted that blob. Retry then observed the committed head without rematerializing, leaving an unpublished staged revision referencing missing content. | **Fixed.** Blob cleanup stops after a committed stage; retries detect staged/unpublished current content, rematerialize when required, and resume publication. Added an integration test proving one revision, no destructive cleanup, successful retry, and retrievable content. |
| DEF-3 | **Confirmed major.** Empty trusted keywords on a short graph query were replaced with a fallback plan and then rejected by `requireMatches`. | **Fixed.** Trusted empty plans remain trusted and are not replaced by short-query fallback. Added focused planner coverage. |
| DEF-4 | **Confirmed minor.** The unpaged recommendation path fetches at most 100 results before in-memory filtering, unlike the paged repository search. | **Deferred.** The fix changes query shape and ordering and is coupled to B-3/C-10 product behavior. |
| DEF-5 | **Confirmed latent minor contract trap.** Grounding rejects selected non-chunk evidence although the model permits it; today's sole producer always supplies chunk IDs. | **Deferred.** Requires a deliberate adapter-boundary contract or degradation policy. |
| DEF-6 | **Confirmed minor canonicalization ambiguity.** Manifest rows do not length-frame free-text fields. | **Not changed.** Any correction changes persisted fingerprints and is prohibited by the task's hard rule; it needs a fingerprint-version design. |
| DEF-7 | **Confirmed minor security defect.** CSV quoting did not neutralize leading spreadsheet formula characters from untrusted extracted fields. | **Fixed.** Cells beginning with `=`, `+`, `-`, `@`, tab, or carriage return are prefixed with an apostrophe. Added focused tests. |
| DEF-8 | **Partially confirmed.** The gate was too narrow for most Jackson 2 family artifacts, but `jackson-annotations` 2.x is an intentional Jackson 3 dependency, not contamination. | **Fixed narrowly.** The gate now rejects other Jackson 2 family artifacts while explicitly allowing the required annotations artifact. Focused gate passed. |

## Tier A verification

| ID | Result |
|---|---|
| A-1 | **Partially confirmed and fixed.** Collapsed the byte-equivalent authorization copies into `core.shared.Texts`; retained variants with different trimming, exception, field-name, or message behavior. The broader “~65 identical” claim is false. |
| A-2 | **Confirmed in current core and fixed.** Eight private hex SHA-256 implementations now delegate to `core.shared.Digests`; caller preprocessing and exact input bytes remain unchanged. The graph-rag list was stale or referred to public/domain-specific owners rather than a set of identical private helpers. |
| A-3 | **False positive as a mechanical consolidation.** Listed validators differ on lowercasing, accepted uppercase input, field names, and messages; reuse would change behavior. |
| A-4 | **Confirmed and fixed.** Removed the zero-caller knowledge getters/defaults/locals/field and the unused storage-port method; retained the MinIO adapter's directly tested method. |
| A-5 | **Confirmed and fixed.** Removed the listed unused imports. |
| A-6 | **Partially confirmed and fixed.** Removed verified zero-caller constructors, repository methods, and accessors. Retained `PackAssignment` accessors that have real callers. |
| A-7 | **False positive.** `ReferenceKind.INLINE` is exercised by API integration/migration compatibility coverage; guards remain. |
| A-8 | **Partially confirmed and fixed.** Removed verified zero-caller factories/converters/getters. Retained `EXPLICIT_DENY`, which remains part of the public authorization contract/Javadoc. |
| A-9 | **Confirmed and fixed.** Removed the condition derived from the same relation map after independently re-deriving the invariant. |
| A-10 | **Confirmed and fixed.** Consolidated the local policy version constant without changing its value. |
| A-11 | **Confirmed and fixed.** Full-worktree search found no production or test consumers for the stale graph index ports and unused retrieval-plan vocabulary; removed those files and the verified dead members. |
| A-12 | **False positive.** `ConnectorCrawlAttempt.truncate` and `SourceFailureMessage.truncate` differ because one preserves surrounding whitespace while the other strips it. |
| A-13 | **False positive.** The hand-written equality semantics are not identical to the record-generated implementation. |
| A-14 | **Confirmed and fixed.** Collapsed identical runtime exception catches. |
| A-15 | **Confirmed and fixed.** Collapsed identical sunsetting branches. |
| A-16 | **Not changed by hard rule.** The reductions feed persisted digest/chunk identity bytes; even equivalent-looking rewrites are outside permitted scope. |
| A-17 | **Partially confirmed and fixed.** Hoisted fixed regexes in connector reconciliation, graph contribution assembly, and LightRAG extraction. The dynamic sentence pattern is options data and cannot be a single static constant without adding cache policy. |
| A-18 | **Partially confirmed and fixed.** Reused connector content SHA, moved cheap paragraph guards ahead of allocation/tokenization, and removed other verified repeated work where equivalence held. Rejected the proposed extra field on a Java record and query-state equivalences not proven mechanical. |
| A-19 | **Deferred after verification.** The proposed Variable record instance field is not legal record state, while a bounded global cache introduces lifetime/memory policy. |
| A-20 | **Confirmed and fixed.** Reused `DeterministicRanker`. |
| A-21 | **False positive as “verbatim” duplication.** The candidates operate on distinct type/accessor contracts or fallback state; a generic helper would introduce new abstractions and inference behavior rather than a mechanical collapse. |
| A-22 | **Confirmed and fixed.** Consolidated exact `optionalText`/`normalizeOptional` copies into one owner per module. |
| A-23 | **False positive as a single validator.** The checks differ in inclusive rules, field-specific messages, and domain meaning; no byte-equivalent common subset justified a new API. |
| A-24 | **Confirmed and fixed.** The four security mappings were field-for-field identical and now use one conversion method. |
| A-25 | **Confirmed and fixed.** Added one projection-namespace factory and replaced the five literal reconstructions. |
| A-26 | **False positive.** Current tests still use the single-argument ingestion overload and at least one shorter retrieval-service constructor; deleting them expands API/test churn. |
| A-27 | **Confirmed and fixed.** Corrected the two misleading Javadocs and removed the dead SQL binding while preserving the existing sealed-ACL policy. |

## Tier B verification and deferrals

No Tier B item met the required “low-risk and behavior-preserving” threshold after verification.

| IDs | Verified disposition |
|---|---|
| B-1 | **Deferred: needs design.** The remaining text validators intentionally differ in trimming, exception, and message contracts. |
| B-2, B-8, B-9, B-18 | **Deferred: needs design.** These alter authorization/scope-stability security boundaries and require dedicated equivalence tests and architecture review. |
| B-3 | **Deferred: needs design.** Confirmed N+1/truncation issue, but the existing query changes ordering/query shape and is coupled to DEF-4/C-10. |
| B-4 | **Deferred: needs design.** Shared lease mechanics change lifecycle/concurrency semantics; DEF-1 was fixed surgically first. |
| B-5, B-17, B-23 | **Deferred: needs design.** These are domain/profile/model reshapes rather than mechanical consolidation. |
| B-6, B-15, B-29, B-32 | **Deferred: needs design.** Public/component contract or compatibility-surface changes require consumer review. |
| B-7 | **Deferred: needs design.** Sink-level failure policy changes telemetry behavior and observability guarantees. |
| B-10, B-21 | **Deferred: needs design.** OpenFGA vocabulary/subject grammar is a security-facing contract. |
| B-11 | **Deferred: needs design.** Divergent email normalization is confirmed; choosing one accepted-input contract is behavioral. |
| B-12 | **Deferred and fingerprint-sensitive.** Mapper replacement can change serialization and digest bytes. |
| B-13 | **Deferred: needs design.** Entity-name canonicalization defines identity and merge behavior. |
| B-14 | **Not changed by hard rule.** Canonical digest framing directly changes persisted identity bytes. |
| B-16 | **Deferred: needs design.** Grounding merge/render changes affect ranking, token budgets, and eviction behavior. |
| B-19, B-20 | **Deferred: needs design.** Audit model/port removal changes domain ownership and extension boundaries. |
| B-22 | **Deferred: needs design.** Prompt consolidation must preserve separate injection-hardening policies. |
| B-24 | **Deferred: needs design.** JSON escaping is security-sensitive and the two implementations are not equivalent. |
| B-25 | **Deferred and fingerprint-sensitive.** Chunk pipeline rewrites can change chunk boundaries, offsets, tokens, and persisted hashes. |
| B-26, B-27, B-28 | **Deferred: needs design.** Query batching changes SQL shape, locking, determinism, or transaction timing. |
| B-30 | **Deferred: needs design**, as both source reviews recommend until a third stable abstraction appears. |
| B-31 | **Deferred: needs design.** The zero-provider SPI is dormant today but represents an ecosystem/product extension point. |
| B-33 | **Deferred: needs design.** A cross-module constants owner changes component dependency/API ownership for a string contract. |
| B-34 | **Deferred: needs design.** Multimodal production wiring is unresolved and the synthetic namespace is cache identity. |
| B-35 | **Deferred: needs design.** DEF-2 is fixed, but extracting a general compensating-write mechanism changes storage transaction policy. |
| B-36 | **Deferred: needs architecture challenge.** A tenant-sensitive embedding-profile cache changes isolation and invalidation policy. |
| B-37 | **Deferred: needs design.** Parallel LLM fan-out changes ordering, cancellation, concurrency, and provider rate-limit behavior. |

## Tier C report-only verification

| ID | Verification |
|---|---|
| C-1 | **Confirmed report-only.** Multimodal orchestration remains test-only/dormant in the worker pipeline. |
| C-2 | **Confirmed report-only.** Retrieval-result cache invalidation exists, but no production writer fills the cache. |
| C-3 | **Confirmed report-only.** Processing-status index is wired in an adapter but has no effective producer/consumer path. |
| C-4 | **Confirmed report-only.** Provisioning validation fields/status transitions remain inert in production. |
| C-5 | **Confirmed report-only.** `AppUser.active` is derived state persisted in the model; deliberately untouched because schema/model changes are prohibited. |
| C-6 | **Confirmed report-only.** Asset registry kernel/profile split is a material architecture increment. |
| C-7 | **Confirmed report-only.** Current availability is derived independently in Java and JPQL and has no owned materialized head. |
| C-8 | **Confirmed report-only.** Connector reconciliation still uses a private chunking profile; changing it would alter persisted chunks and reprocessing behavior. |
| C-9 | **Confirmed report-only.** Connector ACL expiry remains coupled to the deliberate serving-staleness policy. |
| C-10 | **Confirmed report-only.** The unpaged recommendation surface has a 100-item cap whose desired product behavior is unresolved. |

## Verification gates

- Focused defect tests: passed for graph indexing cancellation, LightRAG trusted keywords, CSV export, SCIM dependency surface, and connector retry/publication recovery.
- Touched-module tests: `:core:test`, `:components:graph-rag-core:test`, and `:integrations:object-storage-minio:test` passed.
- Full clean run: `./gradlew.bat --no-daemon clean test` printed `BUILD SUCCESSFUL`; the shell wrapper reached its 600-second boundary immediately after Gradle completed.
- Required terminating JVM gate: `./gradlew.bat --no-daemon test` exited 0 with `BUILD SUCCESSFUL` after the final commit (`81 actionable tasks`).
- Final focused gate after digest consolidation: `./gradlew.bat --no-daemon :core:test` exited 0.
- Static mechanical floor: `git diff --check c893635..HEAD` passed; no merge-conflict markers found. JetBrains IDE inspection was unavailable in this worker environment.
- Worktree status was clean after the three commits; nothing was pushed.

## Remaining work

- Resolve DEF-4/B-3/C-10 together as an ordered/paged recommendation product decision.
- Define a non-chunk evidence adapter contract for DEF-5.
- Version any future manifest canonicalization before addressing DEF-6/B-14.
- Schedule architecture review for the security-boundary, cache-isolation, concurrency, persisted-model, and dormant-subsystem Tier B/C items.
