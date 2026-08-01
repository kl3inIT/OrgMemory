# Retrieval Closure Architecture Challenge Brief

Date: 2026-08-01

Baseline: `13697ff9`

## Decision Required

Choose the smallest production-grade sequence that closes
`knowledge.retrieval` without exposing implementation/persistence types,
changing authorization behavior, or exceeding 100 changed files per code PR.

## Repository Facts

- `knowledge.retrieval` is an open nested module with 36 root-package Java
  files: one `package-info.java` and 35 top-level types, 28 of them public.
- Its top-level Core consumers are `assetregistry` and `assistant`. They consume
  `PermissionAwareKnowledgeSearch`, `SecureKnowledgeSearchResult`,
  `RetrievedKnowledgeEvidence`, and transitively
  `VerifiedKnowledgeGrounding`.
- Spring Modulith rejects those top-level-to-nested references when Retrieval is
  closed. Same-parent Knowledge sibling dependencies remain legal when they use
  the nested module's public root package.
- API and Worker adapters also import search, embedding, configuration, and
  provider-port types from Retrieval. The high-level Fable 5 verdict requires
  adapter-facing stable ports to be named interfaces.
- Retrieval still injects persistence from three owners:
  - Asset repositories in catalog, evidence-scope, and authorization-resource
    lookup;
  - Source Ledger revision/evidence repositories and entities in citation
    content;
  - Organization user/department/organization repositories and `AppUser` in
    evidence scope, source visibility, and authorization-resource lookup.
- The Asset catalog challenge explicitly approved only the catalog federation
  seam and did not certify Retrieval closure while these persistence edges
  remain.
- `knowledge.asset`, `knowledge.graph`, `knowledge.connector`, and
  `knowledge.sourceledger` are already closed. The target endpoint is zero open
  modules with a green `ApplicationModules.verify()`.

## Proposed Sequence

Use several code PRs, each below 100 changed files:

1. Add parent-owned `knowledge::search` and move the four stable query-contract
   types there: `PermissionAwareKnowledgeSearch`,
   `SecureKnowledgeSearchResult`, `RetrievedKnowledgeEvidence`, and
   `VerifiedKnowledgeGrounding`. Keep `CanonicalHybridKnowledgeSearch` and
   GraphRAG implementation in Retrieval. Migrate Core and adapter consumers to
   the parent interface without changing fields, JSON, or behavior.
2. Replace Retrieval's Asset, Source Ledger, and Organization repository/entity
   imports with narrow owner-defined read APIs and immutable values. Preserve
   tenant constraints, current/active lifecycle checks, authorization ordering,
   opaque absence, and citation-content validation.
3. Close Retrieval with an exact outgoing dependency allowlist and structural
   tests for its named interfaces, consumers, persistence isolation, and zero
   open modules.

## Strongest Counterargument To Challenge

Moving all four search values to the parent may turn `knowledge` into a dumping
ground and expose `VerifiedKnowledgeGrounding`, which embeds an AI
`ChatGenerationRequest`, as a broad domain contract. Conversely, duplicating
wrapper DTOs and mapping them would preserve Retrieval ownership but create two
nearly identical evidence/result models and extra authorization-sensitive
translation.

The reviewer must decide whether the four types form one coherent parent search
contract, whether grounding needs a separate seam, and whether direct owner
persistence must be removed before Retrieval can honestly close.

## Alternatives

1. Keep Retrieval open and document the consumers. This violates the judged
   zero-open-module endpoint.
2. Expose the entire Retrieval root as a named interface. This publishes
   repositories, concrete services, configuration, and internal scope types.
3. Add parent wrapper DTOs while retaining identical Retrieval DTOs. This adds
   mapping and drift unless the ownership distinction is materially useful.
4. Close Retrieval mechanically while retaining direct sibling repositories.
   This may satisfy Spring Modulith but contradicts the active design and the
   previous catalog verdict's explicit scope limit.

## Required Verdict

Return `ACCEPT`, `ACCEPT WITH CHANGES`, or `REJECT`, then state:

- exact parent named-interface type set and ownership;
- whether `VerifiedKnowledgeGrounding` belongs in that interface;
- which persistence edges are must-fix before closure;
- safe PR boundaries below 100 files;
- strongest counterargument, counterattack result, rejected alternative, and
  exact structural/security tests required.
