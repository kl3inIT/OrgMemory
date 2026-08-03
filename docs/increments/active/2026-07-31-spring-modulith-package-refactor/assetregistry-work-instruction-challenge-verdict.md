# Asset Registry Work Instruction Boundary Challenge Verdict

Date: 2026-08-03  
Reviewed baseline: `ad794dfbc20083a8bb812c1a244f733e414fe40d`

## Review Execution

Claude Fable 5 was unavailable: the configured account reported 100% weekly
usage and Orca had no other active Claude account. The required fallback ran
independently and read-only with `gpt-5.6-sol` at `ultra` reasoning. The first
fallback invocation hit the Windows read-only sandbox startup failure before
reading the repository. The resumed fallback inspected governing docs, current
source and tests, external consumers, Spring Modulith 2.1 source, and the pinned
Onyx and Langfuse references. No reviewer mutated the worktree.

## Final Verdict

Accept one immediately closed `assetregistry.workinstruction` module, with
binding corrections:

- use separate parent-owned `assetregistry::work-instruction` and
  `assetregistry::work-instruction-relations` interfaces because operations
  mutate acknowledgement state while delivery relation resolution is read-only;
- keep concrete `WorkInstructionSpec` and `WorkInstructionView` records in the
  parent contract, with their wire shape and validation unchanged;
- do not create `WorkInstructionUseQuery`; extend the existing
  `AssetReleaseUseQuery` with a typed exact-release helper and latest usable
  release lookup;
- keep generic dispatch, public `AssetRelationResolution`, and delivery audit
  in the parent while moving Work Instruction parsing and traversal into the
  child.

## Binding Ownership

The parent Asset Registry owns generic Asset identity, persistence, live
`can_use` authorization, delivery dispatch and audit. It also owns the public
Work Instruction contract records. The child owns payload parsing and profile
registration, follow/acknowledge orchestration, the acknowledgement entity and
repository, and Work Instruction relation traversal.

The child may resolve exact or latest usable releases only through
`assetregistry::consumption`, and exact visible Knowledge versions only through
`knowledge::catalog`. It must not import `AssetRegistryService`, parent
repositories/entities, authorization implementations, `knowledge.asset`, or
`knowledge.retrieval`.

Acknowledgement retains one transaction owner and the existing order:
authorize the exact release, verify Work Instruction type, read the
actor-scoped acknowledgement, perform the idempotent insert when absent,
re-read the row, and build the result. `follow` remains read-only and
`acknowledge` remains default read-write `REQUIRED`. No Flyway change is needed.

## Exact Parent Contracts

`assetregistry::work-instruction` contains exactly:

- `WorkInstructionOperations`
- `WorkInstructionView`
- `WorkInstructionSpec`

`assetregistry::work-instruction-relations` contains exactly:

- `WorkInstructionRelationResolver`
- `WorkInstructionRelations`

`WorkInstructionOperations` is consumed only by the REST controller, Core
Assistant service, and API Assistant wiring. `WorkInstructionRelationResolver`
and `WorkInstructionRelations` are consumed only by parent
`AssetDeliveryService`. The eventual child exposes zero public top-level types;
package-private beans implement the parent contracts.

Extend `AssetReleaseUseQuery` with:

- `workInstructionForUse(actor, assetId, releaseId)`, used only by the child
  operations implementation;
- `latestReleaseForUse(actor, assetId)`, used only by the child relation
  implementation outside the parent.

The relation result preserves step order, then Asset and Knowledge reference
order; kinds remain `REGISTRY_RELEASE` and `KNOWLEDGE`; `required` remains
false. Denied or absent relations expose only one opaque `accessGap` bit.

## Closed Child Dependencies

The exact future allowlist is:

- `assetregistry::api`
- `assetregistry::consumption`
- `assetregistry::profile`
- `assetregistry::work-instruction`
- `assetregistry::work-instruction-relations`
- `knowledge::catalog`
- `organization`
- `shared`

No unqualified parent dependency, implementation dependency, Assistant/API/MCP
dependency, or temporarily open module is permitted.

## Binding Delivery Sequence

### PR 1 — Parent contracts and behavior-preserving seams

Target at most 55 rename-aware paths and remain below the hard 100-file cap.
Add the two exact named interfaces, move the two concrete records into the
operations contract, extend the consumption query, route the existing service
through it, extract the relation resolver behind the second contract, and
rewire REST and Assistant. The implementation remains in the parent package;
transactions, endpoints, OpenAPI, schema, and behavior stay unchanged.

### PR 2 — Physical move and immediate closure

Target at most 45 rename-aware paths and remain below the hard 100-file cap.
Move the service, profile, acknowledgement persistence, and relation resolver
into `assetregistry.workinstruction`; make implementations package-private;
add the exact closed-module allowlist and boundary/caller tests; reconcile
current-state docs and pass all focused and terminating gates.

Both PRs contain production code and are independently mergeable. Release is
deferred until the complete modular refactor goal is done.

## Strongest Counterargument

Only six production types are Work Instruction-specific today, so an ordinary
subpackage would reduce root-file pressure with fewer interfaces. Onyx and
Langfuse also group feature code without exclusive persistence ownership.

That alternative loses here because Work Instruction already has coherent
schema semantics, an immutable actor/release acknowledgement ledger, and
relation interpretation, with distinct REST, Assistant, and generic-delivery
consumers. The boundary is justified by enforceable ownership, not file count.

## Rejected Alternative

Reject exposing the concrete service/spec/view from a child while leaving
relation parsing in the parent. That would make top-level consumers depend on
a nested module, split schema interpretation, create parent-child-parent
coupling, and leave generic delivery parsing profile-specific JSON.

## Self-Counterattack

The weakest assumption is that parent-owned interfaces implemented by
package-private child beans preserve a meaningful static boundary and retain
Spring transaction proxies. Exact import guards, `ApplicationModules.verify()`,
and proxy-level transaction tests are therefore binding.

Reverse the decision if the parent must import child implementation or
persistence types, Modulith still reports a cycle, authorization no longer
precedes persistence, transaction/idempotency behavior changes, relation
resolution needs parent implementation facts or leaks denied data, OpenAPI
changes, or a migration becomes necessary.

To contain over-engineering, add no Work Instruction-specific release query,
aggregate, table, event, outbox, repository port, DTO duplication, third named
interface, new transaction, or open-module phase.
