# Spring Modulith Package Refactor Design

## Intent

Reduce the hundreds of directly declared types in `core.knowledge` and
`core.assetregistry` without turning every aggregate or Asset profile into a
top-level module or Gradle project. Each pull request must move a coherent code
slice, keep behavior and persistence contracts unchanged, and stay below 100
changed files.

## Repository Evidence

At the reviewed `origin/main` baseline, `core.knowledge` contains 239 Java
files, 233 directly in its root package, while `core.assetregistry` contains 95
Java files directly in its root package. The existing Modulith verification
test checks the application model, but neither large module expresses its
internal responsibility boundaries.

The first slice is Knowledge Space administration and lookup. It owns the
`knowledge_spaces` aggregate, repository, authorization projection, commands,
queries, and errors. The existing `knowledge.storage` provider-neutral port is
also an intentional public surface consumed by object-storage adapters.

## Selected Structure

Keep `knowledge` and `assetregistry` as the existing top-level application
modules. Introduce responsibility-oriented nested application modules inside
them. A newly moved slice starts as an explicitly annotated
`@ApplicationModule(type = Type.OPEN)` module so imports can be repaired and
facades extracted incrementally. `OPEN` is migration state, not the final
architecture.

The target Knowledge slices are `space`, `sourceledger`, `acl`, `connector`,
`asset`, `retrieval`, and `graph`. Provider ports used by adapters remain named
interfaces, beginning with `knowledge::storage`. The target Asset Registry
slices are `kernel`, `authorization`, `prompt`, `skill`, `pack`, and
`workinstruction`; profiles remain inside the Asset Registry boundary.

Every moved type changes its Java package and therefore its fully qualified
name. Callers are updated in the same pull request; Modulith visibility does
not bypass Java compilation.

## First Delivery Slice

The first code pull request:

- declares `knowledge::storage` as a named interface;
- moves all eleven `KnowledgeSpace*` types to `knowledge.space`;
- marks `knowledge.space` as a nested `OPEN` module;
- updates production and test imports without changing runtime behavior;
- adds structural assertions for the nested module and named interface.

Package-private repository, projection, and service members that current
sibling code already calls become public only where Java compilation requires
it. These are recorded as edge debt; later slices replace repository access
with the owning module's facade before `space` becomes closed.

## Second Delivery Slice

The second code pull request moves the canonical source and revision ledger,
evidence blob, raw/normalized processing records, upload/query services, and
durable ingestion job into `knowledge.sourceledger`. Connector identity and
membership, source ACL, Knowledge Asset, retrieval, and graph types remain in
their owning future slices. As with `space`, the module starts open and every
compiler-forced visibility increase is recorded as edge debt.

The compiler-exposed edge debt is concentrated at the future ACL, Knowledge
Asset, graph, and retrieval boundaries. Existing sibling coordinators still
call source-ledger entity/repository members directly; the later closing slices
must replace those calls with intentional facades before removing `OPEN`.

## Third Delivery Slice

The third code pull request moves source ACL snapshots and heads, external
principal mappings, and source group-membership evidence into
`knowledge.acl`. Connector observations, crawl context, source connections,
and sync-run orchestration remain in the future `knowledge.connector` slice.
The ACL module starts open; compiler-forced connector edges are recorded for
facade extraction before either module is closed.

## Fourth Delivery Slice

The connector boundary is delivered in two code pull requests so each remains
below the 100-file ceiling. The first connector pull request moves the
provider-neutral contracts, crawl batch envelope and component state, source
profiles and registries, and ingestion/reconciliation orchestration into
`knowledge.connector`. Keeping the orchestration beside its package-private
contracts avoids widening internal implementation types merely to cross a
temporary package seam.

Connector crawl attempts and checkpoints, source connections and credentials,
identity observations, and membership sync runs remain in the root package for
the second connector pull request. The nested module starts open until that
runtime and persistence half joins the same boundary and its remaining sibling
dependencies can be replaced with intentional APIs.

## Fifth Delivery Slice

The second connector pull request moves the remaining crawl attempt and
checkpoint persistence, source connection administration and credentials,
identity observations, and membership sync runs into `knowledge.connector`.
This completes the physical connector extraction without widening additional
implementation details. The module remains open while direct source-ledger and
ACL calls are replaced with intentional module APIs and allowed dependencies
are declared.

## Sixth Delivery Slice

The Knowledge Asset pull request moves the asset aggregate and versions,
evidence links, lifecycle and publication/outbox orchestration, authorization
convergence, and chunk projection into `knowledge.asset`. Catalog federation
stays with the future retrieval slice because it resolves the actor's retrieval
evidence scope before reading asset versions.

Compiler-forced edge debt is limited to the asset authorization-scope
projection consumed by retrieval, three version attributes consumed by graph
indexing, and the PostgreSQL vector literal utility shared by chunk projection
and retrieval. These dependencies must be replaced or declared intentionally
before `knowledge.asset`, `knowledge.graph`, and `knowledge.retrieval` close.

## Seventh Delivery Slice

The Knowledge Graph pull request moves graph-index jobs and claiming,
processing-profile persistence and resolution, lifecycle orchestration,
curation, exploration, and export into `knowledge.graph`. GraphRAG query
retrieval remains in the future retrieval slice because it owns authorized
evidence resolution and result assembly rather than graph lifecycle.

The compiler exposes temporary graph edges to the embedding-profile registry,
retrieval evidence scope and canonical recheck, Knowledge Asset chunk
projection, connector reconciliation, and source ingestion. The nested module
starts open, and structural tests pin both its current consumer types and the
internal graph types they consume so this migration debt cannot grow silently.

## Strongest Counterargument

Ordinary internal subpackages would reduce directory size immediately and
avoid public visibility churn. Converting those packages into nested modules
only after all moves are complete would make each intermediate change smaller
and would not temporarily bless repository access as module API.

That approach is rejected because the intermediate architecture would remain
unenforced and regressions could accumulate throughout a long migration. An
explicit nested module makes the intended boundary visible to Spring Modulith
and to focused tests on the first code move. The temporary openness and every
widened edge are measurable debt with a mechanical exit gate.

## Independent Challenge

The boundary decision was challenged in two rounds with Claude Fable 5 and a
separate judge pass. The challenge corrected two assumptions: moving a public
type always requires Java import changes, and unannotated subpackages can
already affect top-level Modulith encapsulation. The final decision still
favored immediate nested modules because it supplies enforceable, inspectable
migration state. See [challenge verdict](challenge-verdict.md).

## Completion Gates

- Every delivery PR contains a coherent code slice and changes fewer than 100
  files.
- Each moved slice has focused structure tests and the repository-wide
  `ApplicationModules.verify()` gate remains green.
- No behavior, database mapping, endpoint, or provider contract changes solely
  because a type moved packages.
- Every temporary public member and sibling dependency is removed or routed
  through an intentional API.
- All nested modules are closed, declare bounded dependencies, and zero
  `Type.OPEN` annotations remain in these two domains.
