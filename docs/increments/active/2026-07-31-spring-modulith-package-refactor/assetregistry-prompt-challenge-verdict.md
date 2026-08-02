# Asset Registry Prompt Boundary Challenge Verdict

Date: 2026-08-02
Reviewed baseline: `9b88c33356515612b4ccb9e51e7e66769ddc2dc9`

## Verdict

ACCEPT WITH CHANGES. Introduce `assetregistry.prompt` as a closed nested
module, but do not add Prompt vocabulary to `assetregistry::api` and do not
make preparation a second execution protocol.

Claude Fable 5 was attempted twice through Orca. The account reported 98%
weekly usage and both attempts returned a blank zero-token response. The
required fallback challenge ran independently in Orca session
`019fc1b5-70b2-7a10-855b-15eb3e5f1eb6`; a counterattack and a factual
amendment corrected the topology and preparation semantics below.

## Binding Boundary

- `AssetRegistryService` remains Java-public in the parent base package and is
  therefore part of the unqualified `assetregistry` default API. It is not in
  `assetregistry::api`.
- `assetregistry::api` remains exactly its existing pinned set.
- `assetregistry::profile` exposes exactly `AssetPayloadProfile`.
- `assetregistry::consumption` exposes exactly `AssetReleaseUseQuery`,
  `AssetAvailability`, `AssetPublicationMode`, and
  `AssetConsumptionRelease`.
- `AssetRegistryService` implements `AssetReleaseUseQuery`; Prompt injects only
  that query and never imports the unqualified parent package.
- `assetregistry.prompt` may depend only on `assetregistry::api`,
  `assetregistry::profile`, `assetregistry::consumption`,
  `assetregistry::prompt`, `ai`, `knowledge::search`, `organization`, `shared`,
  and `shared::error`.

The exact public nested Prompt top-level surface is:

- `PromptExecutionService`
- `PromptEvaluationResult`
- `PromptEvaluationComparison`

Renderer, schema, profile implementation, coordinator, entities, repositories,
and status are internal. The parent-owned `assetregistry::prompt` interface
exports exactly `PromptAssistantOperations`, `PromptPreparationResult`,
`PromptRenderResult`, and `PromptRunResult`, together with only the result
records' existing nested values.

## Preparation Contract

The internal `PromptPreparationService` authorizes and resolves the exact
immutable release through `AssetReleaseUseQuery`,
parses the schema internally, and returns the existing form metadata:
objective, audience, variables, output contract, knowledge requirements, and
known limitations. It accepts no variable values, renders nothing, calls no
model, and persists nothing.

Assistant delegates through the parent-owned `assetregistry::prompt` contract;
an internal Prompt adapter invokes preparation or execution. Rendering remains
separate; execution independently authorizes the supplied release ID. Release
immutability supplies cross-call stability, so no digest token, prepared
command, or new execution precondition is introduced.

## Executable Verifier Amendment

The first implementation made the reviewed direct `assistant ->
assetregistry.prompt` edge and six-entry allowlist executable.
`ApplicationModules.verify()` rejected both assumptions. An unrelated top-level
Core module cannot consume a nested module directly; Prompt also necessarily
uses `assetregistry::api` through `AssetType` and
`AssetUnavailableException`, `shared::error` through business exceptions, and
unqualified `shared` through `BaseEntity` and `Digests`.

The independent fallback reviewer amended the topology in the same Orca
session. The parent now exposes exact named interface `assetregistry::prompt`
with one top-level `PromptAssistantOperations` contract. Assistant consumes
only that interface. A package-private adapter inside the closed Prompt module
implements it and delegates to the nested Prompt services.
This dependency inversion avoids the cycle a parent implementation facade
would create.

The first interface-projection attempt made the Modulith gate pass but failed
the live OpenAPI contract: Springdoc emitted an empty `FormVariable` schema.
The reviewer therefore moved the three immutable concrete results to the
parent interface. Assistant and Prompt now share one concrete Java/OpenAPI
shape, and the internal adapter returns the exact objects without mapping or
copying. Existing maps, strings, record components, property order, and enum
values stay unchanged. The preparation result's release digest remains release
identity for the existing trace reference, not an execution token or
precondition.

## Strongest Counterargument And Rejected Alternative

An ordinary subpackage would reduce the directory immediately with less API
surface. It loses because it cannot enforce the dependency direction or stop
Prompt persistence and schema types leaking to callers. An open nested module
is also rejected because the slice can close immediately through two small
parent-owned named interfaces.

The profile SPI remains intentionally limited to built-in Asset families.
Component discovery does not make the parent depend statically on Prompt, and
tests must freeze the known profile implementations rather than imply an
uncontrolled plugin contract.

## Comparable Evidence

- AgentRegistry pin `d8d3f4ef1ebeed70d58adafd26590ead6198addf`
  keeps typed Prompt schema and validation together while generic registry
  dispatch uses shared kind contracts.
- Onyx pin `618b5031bf21463f44e3bed9eb9d5073b806fec0`
  separates Prompt rendering from persistence and authorization access.

Neither reference justifies a second lifecycle, public repositories, mutable
release preparation, or weaker authorization in OrgMemory.

## Delivery And Executable Gates

The PR changes at most 69 file entries, contains code, closes Prompt in the
same PR, and changes no schema, Flyway migration, endpoint, wire contract, or
execution behavior. Tests must fail first on the unchanged code and then pass
for the exact named-interface surfaces, closed nine-entry Prompt allowlist,
exact three-type nested public surface, exact parent Prompt contract, Assistant isolation from
the nested module, built-in profile set, and repository-wide
`ApplicationModules.verify()`.

The completed review narrowed the nested public surface from four types to
three because preparation is reached only through the parent Prompt contract.
It also replaced name-based internal-to-contract enum conversion with an
exhaustive mapping and recursively froze nested JSON values in the preparation
result. The exact-surface and immutability tests make those corrections
executable without changing the wire schema.
