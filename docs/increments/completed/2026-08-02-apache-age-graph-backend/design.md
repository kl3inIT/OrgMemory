# Explicit Apache AGE graph backend

Date: 2026-08-02
Status: owner-directed conditional acceptance after two unavailable independent
review turns; `challenge-verdict.md` is binding.

## Problem

`graph-rag-postgres` exposes `apache-age-mode=DISABLED|OPTIONAL|REQUIRED`, and
the default says `REQUIRED`. The auto-configuration nevertheless always creates
`PostgresGraphStore`; it never constructs `ApacheAgeGraphTopologyProjection`.
The setting therefore does not select a runtime graph implementation and cannot
enforce the advertised production dependency.

That is worse than having no AGE option: an operator can believe Cypher
topology is required while every production read and write continues through
the relational implementation. The unused AGE projection is also revision-
mutable rather than publication-batch-pinned, so wiring it directly would
violate the durable publication and snapshot contracts completed in B23.

## Pinned LightRAG reference

LightRAG v1.5.4 at commit
`9a45b64c2ee25b1d806e90db926a8af37480bb16` selects an exact graph storage
implementation, for example `NetworkXStorage` or `PGGraphStorage`. Selecting
`PGGraphStorage` makes Apache AGE the implementation: initialization creates or
loads the extension and graph, and later graph operations execute AGE Cypher.
Initialization cannot silently replace the selected implementation with
NetworkX. Its Docker setup supplies a PostgreSQL image that already contains
AGE and pgvector.

OrgMemory adopts that explicit-selection and fail-closed behavior, not
LightRAG's authority model. OrgMemory must retain immutable publication
snapshots, contribution-level evidence, tenant scoping, and relational ACL
rechecks that LightRAG does not provide.

## Proposal under challenge

### Exact topology backend selection

Replace `ApacheAgeMode` with one typed property:

```text
orgmemory.graph-rag.postgres.topology-backend=APACHE_AGE|RELATIONAL
```

`APACHE_AGE` is the production default and selects a real AGE-backed topology
implementation. `RELATIONAL` is an explicit operator/test choice, not a runtime
fallback. There is no `OPTIONAL` value. If `APACHE_AGE` is selected and the
extension, catalog, or required privileges are unavailable, application context
creation fails with an actionable error.

The application still owns one `GraphStore` port. Both implementations retain
the relational canonical/evidence store because AGE is not an authorization or
provenance authority. The selected backend controls the snapshot-bound incident-
relation page used by the core authorized traversal coordinator:

- `RELATIONAL`: the existing relational page query;
- `APACHE_AGE`: AGE supplies the ordered topology page, after which the core and
  relational ledger retain all snapshot, seed, endpoint, evidence, and ACL
  checks.

There is no automatic downgrade from AGE to relational traversal.

### Publication-batch-safe AGE topology

Replace the unused revision-mutable projection with an `ApacheAgeGraphStore`
decorator around `PostgresGraphStore`.

The relational delegate first stages the exact target batch, including B23
copy-forward and mutations. AGE then rebuilds only fixed-size topology identity
for that target batch from the staged relational rows inside one transaction and
under a batch advisory lock. Entity nodes are keyed by `(batch_id, entity_id)`;
relation-contribution edges carry fixed identifiers for batch, canonical
relation, contribution, source, target, Knowledge Asset, and orientation. AGE
receives no descriptions, keywords, confidence, chunk text, ACL payload, or
provider output.

Each rebuild removes only the same unpublished batch's previous partial
topology, writes bounded pages, and creates a batch-ready marker last in the
same transaction. Replay is idempotent. A published snapshot is readable only
when the relational publication proof is valid and the matching AGE ready
marker exists. Missing or contradictory AGE state fails closed.

`discard` requires the existing store-issued `ProjectionDiscardPermit`, removes
only the exact batch topology, then delegates relational cleanup. Current or
historical winners therefore cannot be removed by this adapter.

The graph schema remains tenant-separated by organization. Batch identity in
every node/edge preserves historical snapshots without switching a mutable
organization graph underneath readers.

### Authorized AGE paging

`loadIncidentRelationPage` validates the relational snapshot first. Its Cypher
query binds the exact batch, requested entity IDs, pre-authorized Knowledge
Asset IDs, exclusive relation UUID cursor, and `pageSize + 1`. It returns only
fixed canonical topology fields, ordered by canonical relation UUID. The core
coordinator continues to validate monotonic pages, authorize candidate
endpoints, normalize minimum depth, and apply the global limit.

All entity loading, contribution loading, weights, degrees, export, curation,
and citation evidence remain relational. AGE cannot create a final retrieval
result or expand authorization.

### Delivery configuration

The pinned OrgMemory PostgreSQL image already installs AGE, pgvector, and the
role/session configuration required by the runtime. Production uses the default
`APACHE_AGE`. Unit and application tests that intentionally use mocked/H2
collaborators set `RELATIONAL` explicitly. The obsolete `apache-age-mode`
property is rejected by removal rather than retained as a misleading alias.

## Strongest counterargument

B22 deliberately made the pure-Java coordinator consume a backend-neutral,
relationally verifiable page source, and no production-scale latency corpus has
shown that AGE is faster for OrgMemory's permission-filtered traversal. A new
AGE page path adds Cypher escaping, publication-batch duplication, startup
dependency, discard behavior, and parity tests while relational traversal is
already correct. The safer decision is to delete the inert AGE setting and wait
for measurements.

The owner has explicitly selected the LightRAG-style model instead: a real
backend choice with hard dependency semantics. This increment therefore keeps
`RELATIONAL` as the explicit reference backend, makes AGE prove the exact same
core contract, and does not claim a performance win.

## Repository evidence

- `PostgresGraphRagAutoConfiguration` always returns `PostgresGraphStore`.
- `PostgresGraphRagProperties` defaults the unused `ApacheAgeMode` to
  `REQUIRED`.
- `ApacheAgeGraphTopologyProjection` is constructed only by its integration
  test and mutates one organization graph without publication batch identity.
- `ProjectionBatchLifecycle` and decision 0028 require exact attempts, durable
  commit/discard permits, and retention of ambiguous staging.
- `AuthorizedGraphTraversal` and decision 0027 keep final traversal semantics
  in core and accept only snapshot-bound ordered relation pages.
- the existing test image and deployment bootstrap install AGE and configure
  `session_preload_libraries` for the runtime role.

## Scope

- typed explicit topology-backend property and auto-configuration;
- snapshot-safe AGE topology staging, discard, validation, and incident paging;
- characterization, backend selection, missing-extension, publication replay,
  authorization-negative, historical snapshot, and relational parity tests;
- configuration migration in API/worker tests;
- architecture, Secure GraphRAG spec/test matrix, ADR, roadmap, and increment
  consolidation.

## Non-goals

- no claim that AGE outperforms relational traversal;
- no automatic backend fallback or dual-read comparison in production;
- no change to core traversal semantics, ACL resolution, ranking, citations,
  canonical evidence, or B23 publication authority;
- no corpus-wide rebuild, retention sweeper, Neo4j change, or direct writes to
  AGE catalog tables;
- no evidence content or authorization authority in AGE.

## Decision requested from challenge

Accept only if the selected AGE backend is a real snapshot-bound runtime path,
missing AGE fails application startup, unpublished/partial batches cannot be
read, discard remains permit-bound, and conformance proves the same authorized
result as the relational reference. Otherwise reject the implementation rather
than preserve a second operator-visible fiction.
