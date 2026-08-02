# Asset Registry Prompt Boundary Challenge Brief

## Reviewer Mandate

Act as an adversarial, read-only architecture reviewer. Attack the proposal;
do not validate it by default. Verify every repository claim in the current
checkout. Do not edit files, create commits, change branches, or use plan mode.
Read `CLAUDE.md`, `docs/conventions.md`, the Asset Registry spec and test
matrix, relevant decision filenames, and the active increment design before
giving a verdict.

Return plain Markdown with:

1. `VERDICT`: accept, accept with changes, or reject;
2. the strongest counterargument;
3. a must-fix list tied to concrete repository evidence;
4. exact ownership, public surface, dependency allowlist, and delivery cap;
5. the rejected alternative and why it loses;
6. tests that must fail before implementation and pass afterward.

## Product Promise At Stake

OrgMemory is a governed organizational memory and reusable-asset layer for
enterprise AI. Prompt Templates are immutable, exact-release Assets: rendering
must stay deterministic, optional grounding must remain permission-aware, model
runs must pin route and digest, and persistence must omit raw sensitive inputs
and output. A package refactor may reduce a 90-file root package but must not
create a second lifecycle, weaken authorization, broaden persistence access, or
turn an internal profile SPI into an uncontrolled plugin surface.

## Reviewed Baseline And Observed Cost

Review commit `9b88c33356515612b4ccb9e51e7e66769ddc2dc9`.

After the merged Kernel/Authorization sequence,
`core/src/main/java/com/orgmemory/core/assetregistry` still has 90 Java files
directly in its root. Fourteen are one Prompt responsibility family:

- Prompt schema, profile, renderer, render/run/evaluation results;
- run/evaluation entities and repositories;
- `PromptRunCoordinator` and `PromptExecutionService`.

Prompt behavior is spread across the parent package while consumers in
`core.assistant` and `apps.api` import those types directly. The parent
`AssetTypeProfileRegistry` discovers `AssetPayloadProfile` implementations,
and Prompt execution calls broad `AssetRegistryService.releaseForUse` to obtain
`AssetConsumptionRelease`. A mechanical move can therefore create either a
parent/nested-module cycle or an unnecessarily broad nested-to-parent edge.

## Exact Proposal Under Review

> Introduce `assetregistry.prompt` as a closed nested module immediately. Move
> the fourteen Prompt production types and their focused tests together. The
> module must not import the unqualified Asset Registry parent package. Move
> the generic profile SPI and immutable exact-release consumption vocabulary
> needed by all profile families behind the existing exact parent-owned
> `assetregistry::api` named interface, and add a narrow release-use query
> implemented by `AssetRegistryService`. Prompt consumes that interface plus
> AI, `knowledge::search`, Organization, and shared contracts. Keep Draft,
> review, release, availability persistence, authorization, and generic catalog
> orchestration outside Prompt. Close the module in the same PR, pin its exact
> public surface and allowlist, forbid public JPA entities/repositories, retain
> behavior and schema unchanged, and keep the complete code PR below 70 paths.

Candidate API movement is deliberately contested. Today
`AssetPayloadProfile`, `AssetConsumptionRelease`, `AssetAvailability`, and
`AssetPublicationMode` live in the parent base package. The reviewer must
decide whether they belong in `assetregistry::api`, in a second exact named
interface such as `assetregistry::profile`, or should stay in the parent with a
different cycle-free seam. Do not accept `api` as a dumping ground merely
because it already exists.

Current enforcement and behavior paths:

- `core/src/main/java/com/orgmemory/core/assetregistry/AssetPayloadProfile.java`
- `core/src/main/java/com/orgmemory/core/assetregistry/AssetTypeProfileRegistry.java`
- `core/src/main/java/com/orgmemory/core/assetregistry/AssetRegistryService.java`
- `core/src/main/java/com/orgmemory/core/assetregistry/AssetConsumptionRelease.java`
- `core/src/main/java/com/orgmemory/core/assetregistry/PromptExecutionService.java`
- `core/src/main/java/com/orgmemory/core/assetregistry/PromptRunCoordinator.java`
- `core/src/test/java/com/orgmemory/core/ModulithVerificationTests.java`
- `apps/api/src/test/java/com/orgmemory/api/assetregistry/AssetRegistryIntegrationTests.java`

## Strongest Suspected Counterargument

Prompt execution is not an independent aggregate: it consumes an authorized
release selected by the parent lifecycle and implements one profile SPI
registered by the parent. A closed nested module may force stable-looking API
types for an internal implementation detail, expand the already exact
`assetregistry::api` surface, and make the other three profiles pay translation
cost merely to satisfy package symmetry. An ordinary `assetregistry.prompt`
subpackage, or an initially open nested module with documented edge debt,
could reduce folder size with less public API churn.

## Comparable Source Evidence

| System and pin | Observed mechanism | File-level evidence | Constraint for OrgMemory |
| --- | --- | --- | --- |
| AgentRegistry `d8d3f4ef1ebeed70d58adafd26590ead6198addf` | Prompt has its own typed schema and validation, while generic registry routing and per-kind hooks dispatch through shared contracts keyed by kind. | `pkg/api/v1alpha1/prompt.go`, `pkg/api/v1alpha1/prompt_validate.go`, `pkg/types/types.go`, `internal/registry/api/router/v0.go` | Supports keeping Prompt validation together behind a narrow generic registry seam; it does not justify exposing Prompt persistence or bypassing the shared lifecycle. |
| Onyx `618b5031bf21463f44e3bed9eb9d5073b806fec0` | Prompt template rendering is isolated from prompt persistence/authorization CRUD; authorization remains in the database access path rather than the rendering helper. | `backend/onyx/prompts/prompt_template.py`, `backend/onyx/db/input_prompt.py`, `backend/onyx/db/models.py` | Supports separating pure rendering from lifecycle access, but its simpler mutable prompt model is not evidence for weakening OrgMemory's immutable release and governed authorization rules. |

## Required Adversarial Checks

- Determine whether the proposal creates `assetregistry -> prompt ->
  assetregistry` through Spring component discovery or Java imports.
- Decide whether the profile SPI and release-use query are parent API, a
  distinct named interface, or evidence that Prompt should not be a module.
- Minimize the public Prompt surface; challenge why repositories, entities,
  `PromptRunCoordinator`, renderer, schema, or result DTOs must be public.
- Preserve `REQUIRES_NEW` run/evaluation persistence around external model
  calls and permission-aware Knowledge grounding.
- Reject any plan that moves generic release/availability persistence into
  Prompt or changes Flyway/schema/wire behavior.
- Require executable `ApplicationModules.verify()` and ArchUnit guards, not a
  directory-only success claim.
