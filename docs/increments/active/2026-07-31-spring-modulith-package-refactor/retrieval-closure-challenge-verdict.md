# Retrieval Closure Architecture Challenge Verdict

Date: 2026-08-01

Baseline: `13697ff9`

Configured reviewer: Claude Fable 5

Fallback reviewer: Codex `gpt-5.6-sol`, ultra reasoning, session
`019fbe4a-2626-7132-b723-f26c74c0ff63`

## Reviewer Availability

Fable 5 could not run because the configured account returned:

> You've hit your monthly spend limit. Run /usage-credits to manage your limit
> and keep using Fable 5 or switch models to continue this chat.

The fallback reviewer independently inspected the repository in a read-only
session. It did not use Northstar or memory and did not modify the worktree.

## Verdict

`ACCEPT WITH CHANGES`

Expose the stable search capability through a parent-owned
`knowledge::search` named interface containing exactly:

- `PermissionAwareKnowledgeSearch`;
- `SecureKnowledgeSearchResult`;
- `RetrievedKnowledgeEvidence`;
- `VerifiedKnowledgeGrounding`.

Retrieval continues to own the concrete hybrid and GraphRAG implementations,
authorization orchestration, grounding assembly, ranking, and persistence. AI
continues to own `ChatGenerationRequest`, and Organization continues to own
`CurrentActor`. The parent package is a contract surface, not a new nested
module, and must contain no policies, implementations, configuration, or
persistence types.

## Grounding Decision

`VerifiedKnowledgeGrounding` belongs in the exact search interface without a
shape change. `SecureKnowledgeSearchResult` owns it directly and enforces that
its citations equal the returned evidence. Assistant consumes its finalized
`ChatGenerationRequest` only after GraphRAG has verified the complete evidence
closure. Leaving the grounding value in Retrieval would preserve the forbidden
top-level dependency; duplicating it would introduce authorization-sensitive
mapping and drift.

The AI dependency is accepted because it preserves one permission-verified
model input. Splitting evidence search from Assistant grounding remains a
possible future API redesign, not part of this package-only refactor.

## Conditions Before Retrieval Closure

Java/domain/API closure requires all of these changes before `Type.OPEN` is
removed:

1. Asset owns a retrieval query that replaces direct use of
   `KnowledgeAssetRepository` and `KnowledgeAssetVersionRepository` by catalog,
   evidence-scope, and authorization-resource lookup. It preserves tenant,
   active/current, archived-resource, and authorized-ID semantics.
2. Organization owns subject and resource queries that replace `AppUser`,
   `AppUserRepository`, `UserRole`, `OrganizationRepository`, and
   `DepartmentRepository`. Retrieval must reload the canonical active subject,
   department, and Executive flag instead of trusting actor-supplied values.
3. Source Ledger owns a citation-evidence query that internalizes tenant,
   `READY` revision, Asset match, `BASIC_VALIDATED` blob, and integrity metadata
   checks. Retrieval retains authorization-first ordering, storage access,
   opaque absence, integrity validation, and audit.
4. Graph consumes a Retrieval-owned canonical-evidence verifier instead of
   `SecureKnowledgeRetrievalStore`, `RetrievalScope`, and
   `SecureRetrievalCandidate`.
5. Retrieval persistence and concrete implementation types become internal;
   API and Worker consume only declared parent or adapter-facing interfaces.

The multi-table JDBC security read model may remain Retrieval-owned because it
applies lifecycle, publication, classification, ACL, and authorization
predicates atomically before ranking. This verdict certifies Java/domain/API
closure, not datastore autonomy. Graph may not consume that store, and SQL
security behavior remains contract-tested.

## Delivery Sequence

Each pull request contains production code and stays below 100 changed files:

1. Add `knowledge::search`, move its four unchanged contracts, migrate Core/API
   consumers, and pin the exact surface.
2. Add the Asset retrieval query and migrate catalog, scope, and Asset existence
   reads.
3. Add Organization subject/resource queries and migrate scope, visibility, and
   resource-directory reads.
4. Add the Source Ledger citation-evidence query and migrate citation content.
5. Add the Graph canonical-evidence verifier and internalize Retrieval
   persistence types.
6. Publish intentional embedding/query adapter contracts and remove concrete
   Retrieval imports from API and Worker.
7. Close Retrieval with its exact allowlist, zero-open assertion, and durable
   documentation reconciliation.

The current repository has 99 Java files that mention or declare Retrieval, so
combining these slices would violate the reviewability ceiling after tests and
documentation are added.

## Strongest Counterargument And Counterattack

The four-type seam risks laundering Assistant-specific grounding into the
parent Knowledge API because `VerifiedKnowledgeGrounding` embeds an AI request.
Keeping a 17-table SQL read model also means package closure is not datastore
autonomy, and owner facades would be cosmetic if concrete implementations stay
public or adapter-facing.

The counterattack did not overturn the decision. The current result invariant
makes the four types indivisible without behavioral redesign; the decision
explicitly limits the closure claim; and concrete-type internalization, Graph
store removal, and adapter import rules are blocking conditions rather than
follow-up suggestions.

## Rejected Alternative

Do not close Retrieval mechanically with a broad allowlist or expose its entire
root package. That would publish its JPA entity/repository, JDBC store,
persistence scope/candidate values, concrete services, registry, and
configuration while leaving Graph persistence access and invisible API/Worker
imports in place. Keeping Retrieval open violates the zero-open endpoint, while
duplicate parent wrappers add mapping drift without a distinct contract.

## Required Verification

- exact four-type `knowledge::search` interface and exact top-level consumers;
- no Assistant or Asset Registry dependency on `knowledge.retrieval`;
- exact owner-query surfaces and no foreign repository/entity imports;
- exact Graph and Connector Retrieval surfaces, with no persistence store,
  scope, or candidate leakage;
- API/Worker rules forbidding concrete Retrieval implementations;
- exact final outgoing allowlist and zero open modules;
- existing search/grounding, authorization ordering, opaque absence,
  citation-integrity, GraphRAG revocation/closure, and canonical SQL security
  tests, plus focused owner-query characterization tests.

## Scope

This verdict approves the first parent-search code slice now. It does not
certify Retrieval closure until every condition and final gate above passes.
