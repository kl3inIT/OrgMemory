# Phase 2 Integrations Verification and Fix Report

Date: 2026-08-01  
Worktree: `D:/OrgMemory-worktrees/full-codebase-review`  
Branch: `review/phase2-integrations`  
Verified starting base: `f99afb9f` (Phase 1 PR #189 merged into `origin/main`)  

`origin/main` advanced by eight commits while the clean gate was running. This branch deliberately remains based on the requested starting point; no late merge or push was performed.

## Executive summary

- Independently checked all 19 Tier A, 24 Tier B, eight Tier C, and six possible-defect items against current source and tests.
- Fixed all confirmed mechanical Tier A findings except the reviewer claims disproved by source/tests, and selected only three genuinely low-risk Tier B refactors.
- Preserved every off-limits cursor, persisted digest, fingerprint, cache-key, and external-object-id input byte. Digest helper substitutions retain UTF-8, SHA-256, lowercase hex, and the exact caller-prepared string.
- Made no persisted-model, Flyway, or `ddl-auto` changes.

## Commits

1. `389644d5 refactor(integrations): remove verified duplicate and dead paths`
2. `3f40d3a1 fix(integrations): harden model keys and storage hot paths`

## Tier A verification

| ID | Result |
|---|---|
| A1 | **Confirmed and fixed.** Three connector cursor/key hashes delegate to `core.shared.Digests`; the OpenSearch namespace key delegates to the graph-rag SHA-256 owner. Canonical material was not changed. |
| A2 | **Partially confirmed and fixed.** Replaced eight exact `strip()` helpers with `TextValidation.requireText`. Retained `SpringAiTextEmbeddingPort.requireNonBlank`, whose `trim()` behavior differs on Unicode whitespace. |
| A3 | **Confirmed and fixed.** `AiModelCatalogProbe` now uses `Texts.optionalText`; output is identical. |
| A4 | **Confirmed and fixed.** Deleted zero-production-caller `BoundedBatcher`, its self-only tests, and unused batch configuration fields/options. Full-worktree search found no config/docs consumers. |
| A5 | **Confirmed and fixed.** Removed the zero-caller OpenSearch publication convenience constructor and its private factory. |
| A6 | **Confirmed and fixed.** Removed the zero-caller cache-store methods `invalidateAll` and `parseProjectionKinds`. |
| A7 | **Confirmed and fixed.** `exchange(...)` maps all HTTP error statuses to `ProbeHttpException`; unreachable `HttpClientErrorException` branches were removed. |
| A8 | **Partially false and partially fixed.** Tests call the three-argument `Routes` constructor, so the zero-caller claim was rejected and the constructor retained. `defaults()` now correctly delegates to compact-constructor fallbacks. |
| A9 | **Confirmed and fixed.** All fail-closed beans reuse the existing constants without changing values. |
| A10 | **Confirmed and fixed.** MinIO `stat` is private; the only direct test-only public call was removed. The off-limits `put()` pipeline was untouched. |
| A11 | **Confirmed and fixed.** Term-query construction now has one implementation on `OpenSearchStoreSupport`; the staged-index compatibility methods delegate to it. |
| A12 | **Confirmed and fixed.** The repeated Neo4j relation-contribution visibility predicate is a shared constant used in both authorized queries. Predicate fields and values are unchanged. |
| A13 | **Confirmed and fixed.** PostgreSQL namespace parameters now have one package-private owner. |
| A14 | **False positive.** The properties helper also validates URI structure and throws raw `IllegalArgumentException`; the endpoint-policy helper only strips before its own governed validation/error mapping. Consolidation would change accepted intermediate inputs and exception behavior. |
| A15 | **Confirmed and fixed.** Neo4j stage-state reads now select and return only status. |
| A16 | **Confirmed and fixed.** Vector staging ensures each distinct physical index once per batch. |
| A17 | **Confirmed and fixed.** Visible entity degrees are computed in one relation pass, with explicit self-loop protection to preserve one count per incident relation. |
| A18 | **Confirmed and fixed.** Slack display-name lookup is built once per crawl and reused by every rendered thread; rendered text and cursor material are unchanged. |
| A19 | **Confirmed and fixed.** Multimodal structured converters are static reusable instances. |

## Tier B verification

| ID | Result |
|---|---|
| B1 | **Deferred: needs design.** Exception collapsing crosses an authorization security boundary and must preserve six distinct result factories/codes and interrupt semantics. |
| B2 | **Deferred: off-limits persisted cursor material.** The duplication exists, but a shared owner requires characterization vectors before touching checkpoint bytes. |
| B3 | **Deferred: needs design.** Retry/backoff copies already differ in cap expression and exception factories; selecting one policy is behavioral. |
| B4 | **Deferred: needs design/security review.** Shared JWT minting changes cryptographic/error ownership. |
| B5 | **Deferred: needs design/security review.** PEM parsing differs by accepted formats and deliberate cause suppression. |
| B6 | **Deferred: off-limits cursor wire format.** Intra-module copies are real, but a codec swap needs byte-vector tests first. |
| B7 | **Deferred: needs design.** JDBC batching changes failure granularity, attribution, and batch sizing. |
| B8 | **Deferred: needs design.** Neo4j keyset pagination requires a proven total order and staging-concurrency argument. |
| B9 | **Deferred after verification.** Current scanner discards hits with null source, so simply setting `source(false)` would delete nothing; a metadata-only scanner contract and tests are required. |
| B10 | **Deferred after verification.** Building canonical metadata from `GetObjectResponse` headers requires an explicit MinIO header/version/content-length compatibility contract; `put()` remains off-limits. |
| B11 | **Deferred: needs design.** Client caching introduces concurrent lifecycle, connection identity, secret fingerprinting, and invalidation policy. |
| B12 | **Rejected as low-risk.** Two independently conditional cache beans allow a deployment to override only one port. One combined bean would suppress the other missing default and change auto-configuration behavior. |
| B13 | **Confirmed and fixed.** Both Spring AI caches now use one null-safe package `ModelKey`; added a regression test for superseding global/null-organization keys. |
| B14 | **Confirmed and fixed.** Staged-index scans delegate to the existing PIT/search-after scanner; duplicate loop removed. |
| B15 | **Confirmed and fixed.** Exact positive-duration guards now use `TextValidation.requirePositiveDuration`; the explicit name avoids shadowing Neo4j's integer overload. |
| B16 | **Deferred: persisted external-object IDs.** Encoding is off-limits without characterization tests across Slack/GitHub. |
| B17 | **Deferred: needs design/security review.** GitHub installation admission determines permission/scope verdict codes and should not be abstracted without focused contract tests. |
| B18 | **Deferred: needs design.** Three copy-forward protocols have materially different retry/retirement behavior. |
| B19 | **Deferred: needs design.** Server-side reindex changes heap/network, scripting, and publication failure semantics. |
| B20 | **Deferred: needs design.** Refresh policy is part of publication visibility correctness. |
| B21 | **Deferred: needs architecture challenge.** A shared polling driver reshapes connector SPI and touches off-limits cursors. |
| B22 | **Deferred: needs architecture challenge.** Backend traversal preconditions and deterministic ordering currently differ. |
| B23 | **Deferred: needs architecture challenge.** Publication lifecycle is safety-critical durable state. |
| B24 | **Confirmed live, not test-only; deferred.** Spring auto-configuration uses the convenience constructors that allocate private mappers. Switching to the Boot mapper changes parsing configuration and constructor wiring, so this is not mechanical. |

## Tier C report-only verification

| ID | Result |
|---|---|
| C1 | **Confirmed.** Apache AGE implementation/property are not wired outside their own tests; delete-versus-wire remains a product/roadmap decision. |
| C2 | **Confirmed.** Backend precedence is encoded through sibling class-name strings; deployment selection needs an owned provider property/order contract. |
| C3 | **Confirmed.** Property-configured gateways assume `OPENAI_COMPATIBLE`; adding protocol is a new configuration surface. |
| C4 | **Confirmed.** Hosted preset/SSRF facts remain duplicated; moving them changes core enum ownership and security coupling. |
| C5 | **Confirmed.** OpenFGA wording remains in provider-neutral refs; renaming is a broad port-contract decision. |
| C6 | **Confirmed.** Skill-package policy remains in the MinIO integration; moving it changes module boundaries. |
| C7 | **Confirmed.** Summarization prompt/hardening remains adapter-owned; prompt versioning ownership needs a design decision. |
| C8 | **Confirmed.** Contract-owned cursors/completeness would change persisted checkpoint semantics and stays off-limits. |

## Possible defects

| ID | Verification | Disposition |
|---|---|---|
| V1 | **Confirmed potential null fault.** Global ChatModelPort overloads intentionally pass `organizationId=null`, while the adapter's duplicated key used `organizationId.equals(...)` during supersession. | **Fixed through B13** with one null-safe key and a focused regression test. |
| V2 | **Confirmed ordering instability.** Slack content-cursor input follows source iteration order despite the stability comment. | **Not changed:** correction changes persisted cursor bytes and is explicitly off-limits without cursor versioning/design. |
| V3 | **Confirmed policy drift.** GitHub lacks Slack/Drive's mostly-failed-run abort threshold. | **Deferred:** deciding threshold and checkpoint behavior is connector product/reliability policy. |
| V4 | **Confirmed backend drift.** Zero-limit, empty-seed snapshot validation, and tie ordering differ across PostgreSQL, Neo4j, and OpenSearch. | **Deferred:** retrieval contract decision/architecture challenge (B22). |
| V5 | **Partially false.** A failed staged copy does leave its map entry, but the lock is always unlocked in `finally`; later calls can reuse it, so it is not a stuck lock/deadlock. Retry/retired-lock behavior still differs across adapters. | **Deferred:** choose one concurrency protocol under B18. |
| V6 | **Confirmed.** `apache-age-mode=REQUIRED` has no runtime wiring effect. | **Report-only** with C1; operator-visible config needs an owner decision. |

## Verification gates

- Touched-module test gate passed for graph-rag-core plus connectors, AI gateways, OpenFGA, Neo4j, OpenSearch, PostgreSQL, sidecar JSON, Spring AI, and MinIO.
- Required terminating clean gate passed: `./gradlew.bat --no-daemon clean test` exited 0 with `BUILD SUCCESSFUL in 9m 44s` (`99 actionable tasks`).
- `git diff --check f99afb9f..HEAD` passed and no merge-conflict markers were found.
- JetBrains IDE inspection was unavailable in this worker environment; compile/test gates covered all edited Java modules.
- Worktree is clean, commits are local, and nothing was pushed.

## Remaining owner decisions

- Version and stabilize connector cursor material before V2/B2/B6/B16/C8 changes.
- Decide GitHub partial-failure admission/checkpoint policy (V3).
- Specify one backend-neutral traversal contract (V4/B22).
- Select the correct OpenSearch copy-forward/retry protocol (V5/B18-B20).
- Decide Apache AGE delete-versus-wire and remove the currently inert `REQUIRED` promise (V6/C1).
