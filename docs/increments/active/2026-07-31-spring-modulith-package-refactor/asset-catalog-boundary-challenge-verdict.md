# Asset Catalog Boundary Architecture Challenge Verdict

Date: 2026-08-01

Baseline: `c67effe2`

Configured reviewer: Claude Fable 5

Fallback reviewer: Codex `gpt-5.6-sol`, ultra reasoning, session
`019fbe08-f9cd-7ff2-9471-9cff3fe6af46`

## Reviewer Availability

Fable 5 could not run because the configured account returned:

> You've hit your monthly spend limit. Run /usage-credits to manage your limit
> and keep using Fable 5 or switch models to continue this chat.

The required fallback review ran in a fresh read-only session. It did not use
Northstar or memory and did not modify the repository.

## Verdict

`ACCEPT WITH CHANGES`

Expose catalog federation through a parent Knowledge named interface,
`knowledge::catalog`. The interface owns only `KnowledgeCatalogQuery` and the
immutable `KnowledgeCatalogEntry`. Retrieval implements the query and maps the
Asset-owned persistence projection. Asset Registry and the API consume the
parent interface rather than either nested module.

Asset may close only after the following conditions pass:

- version-only lookup resolves the canonical actor scope before any Asset
  persistence read and queries only current active versions within the
  authorized Asset set;
- missing and denied versions remain the same opaque absence, while
  authorization indeterminacy propagates as failure;
- the HTTP response retains the existing eight fields and OpenAPI component
  identity `KnowledgeCatalogItem` through an API-owned response;
- structural tests pin the exact named-interface surface, prevent Asset
  Registry bypasses into `knowledge.asset`, and remove the two catalog
  consumers from Retrieval's temporary incoming surface;
- the focused catalog, Modulith, API contract, Core, and terminating repository
  gates pass.

## Strongest Counterargument

A parent interface can become boundary laundering: a one-implementation
contract and duplicate-shaped value may hide a capability still split across
Retrieval authorization and Asset persistence. The baseline version-only path
made this concrete by loading an Asset version before resolving authorization.

The counterargument does not defeat the seam because Spring Modulith rejects
the real cross-top-level references from Asset Registry to closed nested
Knowledge modules. It does constrain the seam: it contains no service,
repository, entity, evidence scope, or persistence projection, and structural
tests reject bypasses.

## Counterattack

The fallback verdict was challenged against three contradictions:

1. Asset's outgoing allowlist cannot legalize incoming Asset Registry access.
2. The parent interface is cosmetic unless implementation and persistence stay
   outside it and direct bypasses are forbidden.
3. The candidate did not yet preserve authorization ordering or the public
   schema identity.

These contradictions changed the result from unconditional acceptance to
`ACCEPT WITH CHANGES`; the selected boundary survived with the conditions
above.

## Rejected Alternative

Do not move `KnowledgeCatalogService` or the Asset-owned
`KnowledgeCatalogItem` wholesale into the parent package. That would publish
authorization orchestration and a JPQL constructor projection as the
cross-domain contract. Direct references to the nested modules, continued
`OPEN` state, and duplicated Asset Registry authorization were also rejected.

## Scope Limit

This decision enables Asset closure and removes only the catalog portion of
Retrieval's incoming surface. It does not certify Retrieval closure or remove
its remaining Asset repository dependencies. `PromptExecutionService` still
consumes Retrieval search contracts and is handled by the Retrieval closing
cycle.
