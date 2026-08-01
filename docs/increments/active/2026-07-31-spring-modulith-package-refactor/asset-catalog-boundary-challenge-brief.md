# Asset Catalog Boundary Challenge Brief

Date: 2026-08-01

Baseline: `origin/main` at `c67effe2`; the current working tree contains only
the failing-first Asset-closure test and the candidate closed-module metadata.

## Reviewer Mandate

Attack the proposal rather than validate it. Verify every claim in the current
repository and return one explicit verdict, a must-fix list, and repository
evidence for each conclusion. Work read-only: do not edit files, run destructive
commands, or enter plan mode. Read `CLAUDE.md`, `docs/conventions.md`,
`docs/specs/domains/asset-registry.md`, this increment's `design.md` and
`challenge-verdict.md`, and relevant filenames under `docs/decisions` before
judging.

## Product Promise at Stake

OrgMemory is a governed organizational-memory layer for enterprise AI. Its
Knowledge domain owns permission-filtered source-derived assets and retrieval;
the side-by-side Asset Registry owns reusable governed artifacts such as Skill
Packages and Capability Packs. A pack may reference a visible Knowledge version,
but that federation must not copy Knowledge authorization or let Asset Registry
reach into Knowledge persistence. Closing the nested Knowledge Asset module must
preserve this permission ceiling and the two top-level domain boundaries.

## Exact Decision Under Review

Choose the public seam for `assetregistry -> knowledge` catalog reads so
`knowledge.asset` and later `knowledge.retrieval` can close.

Candidate proposal:

> Add a parent-owned `com.orgmemory.core.knowledge.catalog` package annotated
> `@NamedInterface("catalog")`. Expose a small `KnowledgeCatalogQuery` and an
> immutable `KnowledgeCatalogEntry` there. Keep `KnowledgeCatalogService` in
> `knowledge.retrieval` as the adapter implementing that query and map the
> Asset-owned `KnowledgeCatalogItem` persistence projection to the public entry.
> Asset Registry consumes only `knowledge::catalog`; it does not import either
> nested module. Keep `KnowledgeCatalogItem` Asset-owned and internal to
> Knowledge orchestration.

Enforcement and current evidence:

- `core/src/main/java/com/orgmemory/core/knowledge/asset/package-info.java`
  contains the candidate `CLOSED` metadata and six-entry outgoing allowlist.
- `core/src/test/java/com/orgmemory/core/ModulithVerificationTests.java`
  contains the failing-first closure regression and `modules.verify()`.
- `core/src/main/java/com/orgmemory/core/assetregistry/AssetDeliveryService.java`
  injects the nested Retrieval service directly.
- `core/src/main/java/com/orgmemory/core/assetregistry/CapabilityPackService.java`
  imports both the nested Retrieval service and Asset-owned catalog projection.
- `core/src/main/java/com/orgmemory/core/knowledge/retrieval/KnowledgeCatalogService.java`
  applies the actor evidence scope before querying Asset versions.
- `core/src/main/java/com/orgmemory/core/knowledge/asset/KnowledgeAssetVersionRepository.java`
  constructs the Asset-owned projection from canonical persisted versions.
- The focused verification currently reports exactly two invalid sub-module
  references, both from Asset Registry to `KnowledgeCatalogItem`; leaving Asset
  open hides the corresponding Retrieval edge until Retrieval closes.

## Alternatives to Attack

1. Move `KnowledgeCatalogService` and `KnowledgeCatalogItem` wholesale into the
   parent named interface. This is smaller but makes a persistence projection
   and implementation service part of the public cross-domain contract.
2. Expose the existing nested Asset/Retrieval root packages directly. Spring
   Modulith 2.1 rejects this because Asset Registry and the nested modules do not
   share a parent or direct parent relationship.
3. Keep Asset and Retrieval open. This violates the accepted deadline of zero
   open modules and leaves the architecture unenforced.
4. Duplicate catalog authorization and reads inside Asset Registry. This risks
   permission drift and violates the canonical Knowledge ownership boundary.

## Comparable-System Evidence

| System | Observed mechanism | Evidence |
| --- | --- | --- |
| Spring Modulith 2.1.0 | A dependency first has to target an exposed type, then nested-module references are rejected unless source and target have the same parent or a direct parent relationship. Named interfaces are the framework mechanism for an explicit parent-module surface. | Gradle source JAR `spring-modulith-core-2.1.0-sources.jar`, entry `org/springframework/modulith/core/ApplicationModule.java`, lines 1377-1394; `core/src/main/java/com/orgmemory/core/knowledge/storage/package-info.java` shows the existing parent named-interface pattern. |
| Onyx pin `618b503` | Search orchestration depends on the abstract `DocumentIndex` contract, while concrete OpenSearch/Vespa implementations stay behind it; consumers do not take a concrete index implementation as their API. | `tmp/onyx/backend/onyx/document_index/interfaces_new.py` (`DocumentIndex`); `tmp/onyx/backend/onyx/context/search/retrieval/search_runner.py` (`DocumentIndex` parameter); concrete adapters under `tmp/onyx/backend/onyx/document_index/opensearch` and `vespa`. |

The Onyx mechanism is only directional evidence, not a permission model to
copy. OrgMemory's existing `KnowledgeEvidenceScopeResolver` remains authoritative
and the public query must stay fail-closed through that implementation.

## Required Verdict

Return:

1. `VERDICT: ACCEPT`, `ACCEPT WITH CHANGES`, or `REJECT`.
2. The strongest counterargument to the candidate.
3. A concrete public API shape and ownership placement.
4. Must-fix items required before Asset closes.
5. The rejected alternative and why it loses against repository evidence.
