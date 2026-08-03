# Asset Registry Work Instruction Boundary Challenge

Date: 2026-08-03  
Base commit: `ad794dfbc20083a8bb812c1a244f733e414fe40d`

## Reviewer Mandate

Attack this proposal. Do not validate it by default. Read the cited source and
the current repository before reaching a verdict. Identify any hidden module
cycle, persistence-owner split, authorization regression, transaction change,
wire/schema change, or public surface that is broader than its consumers need.
Every claim in the verdict must cite repository evidence.

Before reviewing, read `CLAUDE.md`, `docs/conventions.md`,
`docs/specs/domains/asset-registry.md`,
`docs/tests/domains/asset-registry.md`, the filenames under `docs/decisions`,
and the source paths cited below. This is a read-only review: do not edit files,
run mutating commands, or enter plan mode.

## Product Promise At Stake

OrgMemory is a governed organizational memory layer for enterprise AI work.
A Work Instruction is an immutable authorized release whose steps may point to
other exact Assets and Knowledge versions. Acknowledgement is actor-derived,
idempotent evidence pinned to that release. Refactoring the package must reduce
the 62-file Asset Registry root without weakening authorization, changing the
REST/OpenAPI shape, moving generic Asset lifecycle ownership, or detaching the
acknowledgement ledger from the semantics it proves.

## Exact Proposal Under Review

> Create one closed `assetregistry.workinstruction` nested module. Move the
> Work Instruction profile, specification, acknowledgement entity/repository,
> orchestration, and relation-resolution semantics into it. Keep generic Asset
> identity, release authorization, delivery dispatch, and audit in the parent.
> Replace the child's dependency on root `AssetRegistryService` with the exact
> parent `assetregistry::consumption` query. Expose delivery adapters and the
> top-level Assistant through a parent-owned `assetregistry::work-instruction`
> named interface rather than public child implementations. The proposed exact
> contract surface is `WorkInstructionOperations`,
> `WorkInstructionRelationResolver`, `WorkInstructionView`,
> `WorkInstructionSpec`, and `WorkInstructionRelations`. The closed child has
> no public top-level implementation types and depends only on
> `assetregistry::api`, `assetregistry::consumption`,
> `assetregistry::profile`, `assetregistry::work-instruction`,
> `knowledge::catalog`, `organization`, and `shared`. No migration, endpoint,
> transaction, authorization, or JSON schema changes are allowed.

The principal alternative is to expose `WorkInstructionService`,
`WorkInstructionView`, and `WorkInstructionSpec` directly from the child and
leave relation parsing in the parent behind another narrow parser contract.
A second alternative is to move only files into an ordinary non-Modulith
subpackage and defer closure. The reviewer must decide whether the proposal is
actually the smallest enforceable boundary and give the exact corrected
surface/allowlist if it is not.

## Current Repository Evidence

| Fact | Evidence | Consequence to challenge |
| --- | --- | --- |
| Asset Registry still has 62 Java files directly in its root after Prompt and Skill closure. Six Work Instruction-specific production types remain there. | `core/src/main/java/com/orgmemory/core/assetregistry/WorkInstructionService.java`; `WorkInstructionProfile.java`; `WorkInstructionSpec.java`; `WorkInstructionView.java`; `WorkInstructionAcknowledgement.java`; `WorkInstructionAcknowledgementRepository.java` | Directory pressure is real, but file count alone does not prove a domain boundary. |
| Follow and acknowledge authorize an exact Work Instruction release before reading or inserting actor-scoped acknowledgement state. Insert is idempotent and the result is re-read. | `WorkInstructionService.java:30-88`; `WorkInstructionAcknowledgementRepository.java:11-39` | Moving orchestration and persistence separately could break the authority/transaction sequence. |
| The acknowledgement table is type-specific and pins organization, Asset, Release, release digest, actor, and timestamp. | `WorkInstructionAcknowledgement.java:11-43`; Flyway table/constraint references found by `rg work_instruction_acknowledgements core/src/main/resources` | Keeping this ledger in the generic parent requires a reason stronger than existing package location. Moving it must not create a migration. |
| Parent `AssetDeliveryService` parses Work Instruction steps and resolves related Assets and Knowledge versions while collapsing denied references into one access-gap flag. | `AssetDeliveryService.java:84-145` | A naive move creates `assetregistry -> workinstruction -> assetregistry` or leaks parent implementation into the child. |
| API and top-level Assistant currently import root `WorkInstructionService` and `WorkInstructionView`. | `apps/api/src/main/java/com/orgmemory/api/assetregistry/AssetConsumptionController.java:17-20,47-55,171-195`; `core/src/main/java/com/orgmemory/core/assistant/AssistantAssetToolService.java:10-11,31-39,188-206` | The verdict must name exact consumer-facing contracts and avoid the invalid direct nested reference previously found in the Prompt slice. |
| Prompt solved its top-level consumer seam with a parent-owned named interface, while Skill exposes child contracts only where the parent never consumes the child. | `core/src/main/java/com/orgmemory/core/assetregistry/promptcontract/package-info.java`; `PromptAssistantOperations.java`; `core/src/main/java/com/orgmemory/core/assetregistry/skill/package-info.java`; `docs/increments/active/2026-07-31-spring-modulith-package-refactor/assetregistry-prompt-challenge-verdict.md`; `assetregistry-skill-challenge-verdict.md` | These are precedents, not proof that either topology fits Work Instruction relation dispatch. |
| Current coverage pins schema validation, idempotent acknowledgement, exact-release authorization, relation access gaps, second-user flow, and committed OpenAPI. | `docs/tests/domains/asset-registry.md:23,61,70,88,99`; `AssetRegistryIntegrationTests`; `AssetConsumptionControllerTests`; `OpenApiContractTests` | A package-only refactor must preserve all of these executable contracts. |

## Comparable-System Evidence

| Source | Observed source fact | Relevance and limit |
| --- | --- | --- |
| Spring Modulith 2.1.0 | A dependency must target an exposed type, and nested-module references are constrained by the parent relationship; named interfaces are the mechanism for an explicit parent surface. | Local source JAR `C:/Users/admin/.gradle/caches/modules-2/files-2.1/org.springframework.modulith/spring-modulith-core/2.1.0/e237a88bcedc6bc9813f98e209eb0e4e0899bbc/spring-modulith-core-2.1.0-sources.jar`, entry `org/springframework/modulith/core/ApplicationModule.java`; current executable examples are `promptcontract/package-info.java` and `ModulithVerificationTests`. This constrains topology but does not choose business ownership. |
| Onyx `618b5031bf21463f44e3bed9eb9d5073b806fec0` | Input-prompt HTTP use cases are grouped under one feature API/model package but call dedicated DB functions and models directly. | `D:/OrgMemory/tmp/onyx/backend/onyx/server/features/input_prompt/api.py:1-80`; `server/features/input_prompt/models.py:1-35`; `db/input_prompt.py:15-179`. It supports cohesive feature semantics and persistence operations, but Onyx has no Spring Modulith closure contract and therefore cannot justify a broad public child API. |
| Langfuse `c4eaf1f4c0cd1851f0c9dbea2802c243cf787a09` | Prompt routes group feature actions but consume shared DB types and a shared `PromptService`. | `D:/OrgMemory/tmp/langfuse-prompt-reference/web/src/features/prompts/server/routers/promptRouter.ts:1-45,600-625`. This is counterevidence to blindly equating a feature folder with exclusive persistence ownership; it does not solve OrgMemory's parent/child cycle. |

## Observed Cost And Failure Mode

The root remains a 62-file change surface where profile validation,
actor-derived acknowledgement persistence, generic lifecycle state, Pack
journeys, audit, and delivery are mixed. A simple move is not safe: the parent
currently parses Work Instruction relations, while the Work Instruction service
calls a parent implementation. Closing both directions would form a module
cycle. The earlier Prompt closure also proved that a top-level Core module's
direct nested reference is rejected, so external consumer imports must be part
of this decision rather than fixed after the move.

## Required Verdict

Return plain Markdown with:

1. `VERDICT`: accept, accept with binding corrections, or reject.
2. The exact owner of acknowledgement persistence and transaction semantics.
3. The exact owner of relation parsing/resolution and generic delivery audit.
4. The exact public top-level type set and exact outgoing dependency allowlist.
5. Exact permitted consumer sets for every contract.
6. Must-fix tests, including the failing-first Modulith tests.
7. The strongest counterargument and why it wins or loses.
8. A file-count-safe PR sequence below the hard 100-file cap.

Then challenge your own verdict with at least three concrete contradictions:
one module-cycle contradiction, one authorization/transaction contradiction,
and one API/OpenAPI compatibility contradiction. Revise the verdict if any
contradiction survives.
