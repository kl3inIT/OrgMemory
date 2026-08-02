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
The asset-owned projection store returns a graph-neutral
`KnowledgeChunkProjection`; `knowledge.graph` maps that value into
`GraphIndexChunk`, preventing a reciprocal Asset-to-Graph dependency.

## Eighth Delivery Slice

Retrieval is split across two code pull requests to keep each delivery below
the 100-file ceiling. The first pull request moves the provider-neutral query
embedding port, embedding profile model and registry, projection namespaces,
and embedding configuration into `knowledge.retrieval`. Runtime search,
authorization rechecks, evidence assembly, citation policy, and persistence
remain in the root Knowledge package for the second retrieval pull request.

The first half starts open because Asset, Connector, Graph, Source Ledger, API,
and Worker code already consume these contracts. A structural test pins both
the exact consumers and exact internal retrieval types they use, making that
temporary migration surface explicit until retrieval owns its runtime half and
can expose a smaller intentional interface.

The second retrieval pull request moves authorized hybrid search, canonical
authorization rechecks, evidence-scope resolution, catalog federation,
citation streaming, GraphRAG result assembly, retrieval policy/configuration,
and PostgreSQL-backed retrieval into the same nested module. The structural
guard is refreshed to the complete runtime boundary. The module stays open
until the later Knowledge closing phase replaces its broad sibling consumers
with intentional interfaces and declares bounded dependencies.

## Ninth Delivery Slice

The final physical Knowledge cleanup assigns the three remaining root-package
types to their actual owners: source group views to `knowledge.acl`, connection
identity trust to `knowledge.connector`, and bounded source failure messages to
`knowledge.sourceledger`. A structural test prevents domain types from
returning to the parent `knowledge` package.

A close-all verification probe demonstrated that openness cannot be removed by
annotation alone. The current implementation contains real cycles through
ACL/Connector crawl contracts, Source Ledger/Asset publication orchestration,
Asset/Retrieval value types, and Graph lifecycle coordination. Declaring those
dependencies as allowed would document the cycles without removing them, while
Spring Modulith still rejects the closed graph.

The closing phase therefore proceeds by seam rather than module name: first
replace reciprocal ACL/Connector types with owner-defined commands and lookup
facades; then remove Source Ledger's outward dependencies on Retrieval, Asset,
and Space; then make Asset own its catalog/chunk/publication value types so
Retrieval depends one way on Asset; finally route Graph lifecycle through the
resulting module APIs. Only after the graph is acyclic are `OPEN` annotations
removed and exact `allowedDependencies` declared.

## Asset Catalog Boundary Challenge

Closing Asset exposed a Spring Modulith boundary that the earlier ownership
move could not solve: top-level Asset Registry cannot consume a closed nested
Asset or Retrieval module. The independently challenged decision is to expose
a parent-owned `knowledge::catalog` named interface containing only
`KnowledgeCatalogQuery` and `KnowledgeCatalogEntry`. Retrieval implements that
query, retains canonical permission-scope resolution, and maps the Asset-owned
catalog persistence projection at the implementation boundary.

The strongest counterargument is that this could launder one capability across
three packages. The seam remains intentional only while the named interface is
exact, Asset Registry is structurally forbidden from bypassing it, and the
concrete Retrieval service and Asset projection remain outside it. The review
also found that version-only lookup read Asset persistence before resolving
authorization; Asset closure therefore requires an authorization-first query
over the current active version and authorized Asset-ID set.

The HTTP controller maps the public entry to an API-owned response retaining
the existing `KnowledgeCatalogItem` schema identity and eight-field wire shape.
Moving the concrete service or JPQL projection to the parent interface was
rejected because it would expose orchestration and persistence as a cross-domain
contract. This decision removes only catalog consumers from Retrieval; it does
not imply Retrieval closure while its search consumers and Asset persistence
edges remain.

See
[asset-catalog-boundary-challenge-verdict.md](asset-catalog-boundary-challenge-verdict.md)
for the reviewer failure, fallback verdict, counterattack, must-fix conditions,
and scope limit.

## Retrieval Closure Boundary Challenge

The independently challenged Retrieval sequence exposes a parent-owned
`knowledge::search` interface containing exactly the permission-aware search
contract, result, evidence, and verified-grounding values. Grounding remains in
that surface because it is part of the result invariant and carries the final
permission-verified model input; the concrete hybrid and GraphRAG services stay
inside Retrieval.

Moving those four values is only the first code slice. Retrieval cannot close
honestly while it imports Asset, Source Ledger, or Organization persistence,
while Graph consumes its JDBC store and candidate types, or while API and Worker
depend on concrete implementations. Those edges are removed through owner
queries and intentional adapter interfaces in separate code PRs below the
100-file ceiling. The multi-table canonical retrieval SQL remains an explicitly
documented security read model; this increment claims Java/domain/API closure,
not datastore autonomy.

## Retrieval Adapter Boundary

The API and Worker retain engine selection and provider wiring, but they inject
Retrieval contracts rather than implementation classes. The existing canonical
hybrid, GraphRAG, citation/source opening, authorization-resource, bounded
single-Asset inspection, and embedding-registry capabilities become interfaces
with unchanged method shapes. Full evidence-scope resolution stays
package-private so its internal scope model is not laundered into the API. The
default or JDBC implementations use distinct package-private types. A public canonical-engine configuration is the explicit
opt-in used by the API and by Worker integration tests; the production Worker
excludes that configuration because it does not serve interactive queries.

This is the adapter-interface slice already required by the independent
Retrieval closure verdict, not a new policy or ownership decision. Query and
embedding properties/value types remain intentional adapter configuration
contracts. Retrieval stays open until the remaining root-package persistence
and concrete types are internalized and the exact final dependency allowlist is
verified.

See
[retrieval-closure-challenge-verdict.md](retrieval-closure-challenge-verdict.md)
for reviewer availability, exact ownership, the counterattack, blocking
conditions, PR sequence, and verification requirements.

## Retrieval Closure

The final Retrieval slice follows the already challenged closure decision: the
module becomes closed with only its intentional public root contracts and an
exact outgoing dependency allowlist. Persistence and runtime collaboration
types that no external production consumer imports become package-private in
the module base package. This preserves JPA/Spring wiring and test-package
access without presenting those types as Modulith API or creating a mechanical
internal subpackage whose public Java types could still be imported by an
adapter.

The canonical multi-table security read model remains Retrieval-owned, exactly
as the challenge allowed. This closure therefore proves Java/domain/API
encapsulation; it does not claim datastore autonomy or change authorization,
ranking, persistence, endpoint, or provider behavior.

## First Cycle-Removal Slice

The ACL/Connector cycle is cut at the ownership boundary instead of hidden by
an allowlist. Membership sync provenance, active membership rows, capture
status, identity observations, and resolved-principal commands belong to ACL.
Connector translates crawl payloads into those ACL-owned commands and remains
the one-way caller.

Connection trust and configuration remain Connector-owned. The combined
connection administration service consumes a narrow ACL query that reports
principal kind, mapping state, and last-seen time without exposing ACL
repositories or entities. ACL mapping receives the semantic fact that a
connection vouches for email rather than importing Connector's trust enum. An
ArchUnit rule makes a future ACL-to-Connector dependency a build failure.

## Second Cycle-Removal Slice

Source Ledger no longer reaches into Retrieval for authorization queries,
embedding profile metadata, completion values, or a generic not-found error.
It owns narrow visibility and embedding-profile ports plus the stable profile
facts persisted with a completed revision. Retrieval implements those ports
and keeps OpenFGA/retrieval-store policy inside its boundary; Worker and
Connector translate richer retrieval profiles into the ledger completion ref.

The opaque knowledge-resource not-found error is shared across ACL, Space,
Graph, Source Ledger, and Retrieval, so it belongs to `shared.error` rather
than making every consumer depend on Retrieval. An ArchUnit rule now makes any
new Source Ledger-to-Retrieval dependency fail the build.

## Third Cycle-Removal Slice

Source Ledger no longer persists Asset entities or imports Asset lifecycle
types. It validates canonical source, revision, ACL, and normalization state,
then calls a Source-Ledger-owned promotion port. Asset implements that port,
creates its identity/version/evidence rows, and returns only the stable IDs
that Source Ledger records as provenance.

Asset deletion now retires its current version inside the Asset boundary.
The shared evidence content-type policy moves to Source Ledger, where uploads
originate, and Graph enqueue accepts stable Asset/version IDs before checking
the active version in its own Asset-facing adapter. An ArchUnit rule makes any
new Source Ledger-to-Asset dependency fail the build.

## Fourth Cycle-Removal Slice

Source Ledger no longer imports Space services or projection types. It owns a
narrow target port whose compact result contains only the Space ID and optional
department ID needed to validate and register evidence. Space implements the
port by retaining the existing active-space lookup and `can_create_asset`
authorization decision.

Promotion's organization check uses the same port, so Source Ledger does not
duplicate Space repository policy. An ArchUnit rule now makes any new Source
Ledger-to-Space dependency fail the build.

## Fifth Cycle-Removal Slice

The Source Ledger query for the current raw/ACL head no longer constructs a
Connector-owned projection. The compact head view belongs to Source Ledger,
whose repositories supply every field, while Connector consumes that view to
choose between content registration and ACL rotation.

Renaming the operation to `findSourceHead` removes Connector terminology from
the ledger API. An ArchUnit rule now makes any new Source Ledger-to-Connector
dependency fail the build.

## Sixth Cycle-Removal Slice

Source Ledger no longer calls the Graph queue implementation directly. It
publishes the stable source revision and Asset/version identities through an
outbound scheduling port; Graph implements that port and retains active-version
validation, processing-profile resolution, idempotency, and durable enqueue
semantics.

The port returns no Graph job type because Source Ledger does not consume the
job identity. An ArchUnit rule now makes any new Source Ledger-to-Graph
dependency fail the build.

## Seventh Cycle-Removal Slice

ACL no longer imports the Source Ledger entity or ingestion exception merely
to advance its own heads. ACL owns a compact source target value carrying only
the stable identity fields required by the head, while Source Ledger translates
its raw-source entity at the call boundary.

Generation conflicts retain the existing transport-neutral conflict category
and stable `knowledge-ingestion.conflict` code through the shared business
exception. An ArchUnit rule now makes any new ACL-to-Source Ledger dependency
fail the build. The reverse Source Ledger-to-ACL dependency remains explicit
for the next slice, where transaction orchestration can be moved behind an
ACL-owned API without mixing that larger change into this entity boundary.

## Eighth Cycle-Removal Slice

Source Ledger no longer coordinates ACL repositories or accepts ACL JPA
entities. An ACL-owned facade validates capture policy, computes canonical
hashes, persists entries and seals, advances the compare-and-set head, and
answers normalization/promotion readiness queries. Source Ledger retains the
canonical raw-source transaction lock and translates raw entities into the
compact ACL target introduced in the previous slice.

The facade returns immutable snapshot/head facts rather than persistence
objects. Source Ledger still consumes ACL-owned command/value contracts in the
intentional one-way direction; an exact Modulith dependency assertion pins
that surface so repositories or entities cannot leak back across the seam.

## First Module Closure

Source Ledger is the first extracted Knowledge slice to move from migration
state to a closed Spring Modulith module. Its outgoing dependency policy names
only ACL's public API, the parent Knowledge storage named interface,
organization, permission, and the shared base/error contracts.

The closed-module verification succeeds without publishing a new named
interface because Source Ledger's intentional consumer contracts already live
in its module base package, while no consumer reaches an internal subpackage.
The closure test and `modules.verify()` make both that API visibility and the
outgoing allowlist executable constraints.

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
