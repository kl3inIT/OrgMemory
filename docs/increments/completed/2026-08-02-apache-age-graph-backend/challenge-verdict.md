# Architecture challenge verdict

Date: 2026-08-02
Result: owner-directed conditional acceptance after reviewer unavailability

## Review availability

Two independent Codex `gpt-5.6-sol ultra` review turns were dispatched with a
bounded evidence set. Neither returned a verdict or wrote an artifact before
being stopped after repeated conclusion requests. No output from those turns is
represented as an independent review.

Repository guidance permits owner direction when the configured reviewer is
unavailable, provided the fact is recorded. The project owner explicitly chose
the LightRAG-style real backend selection after the alternatives were explained.
This verdict therefore makes the already independent decisions 0027 (core-owned
authorized traversal) and 0028 (durable publication permits) binding on that
owner-selected direction. It is not described as reviewer approval.

## Proposal

Replace the inert three-state AGE mode with exact
`APACHE_AGE|RELATIONAL` topology-backend selection. Selecting `APACHE_AGE`
must create a real AGE-backed snapshot page source, validate the dependency at
startup, stage immutable publication-batch topology, and fail closed. Relational
evidence and the pure-Java traversal coordinator remain authoritative for ACL,
snapshot, endpoint, ordering, and final-result semantics.

## Strongest counterargument

There is no production latency corpus showing AGE improves OrgMemory's bounded,
permission-filtered traversal. The relational implementation already passes the
shared contract. A new AGE path adds a startup dependency, Cypher correctness,
duplicated snapshot topology, and cleanup/recovery surface. Deleting the inert
setting and retaining relational traversal would be smaller and safer.

The owner rejected that alternative and chose an explicit real backend. The
implementation is accepted only as a correctness-equivalent backend choice; it
must not claim a performance benefit or weaken the relational reference path.

## Repository evidence

- `PostgresGraphRagAutoConfiguration` always creates `PostgresGraphStore`; the
  current AGE mode never selects a runtime implementation.
- `ApacheAgeGraphTopologyProjection` is instantiated only by its integration
  test and mutates organization topology without publication batch identity.
- `PostgresGraphStore` owns batch-pinned canonical topology and evidence and
  applies authorization before page limits.
- decision 0027 makes the pure-Java coordinator the sole final traversal owner
  and requires complete, ordered, snapshot-bound relation pages.
- decision 0028 requires exact physical attempts, irrevocable commit authority,
  ambiguity-preserving recovery, and store-issued discard permits.
- the PostgreSQL test/deployment image provisions AGE and pgvector; runtime code
  does not need extension-creation privileges.
- Apache AGE writes participate in PostgreSQL transactions, so relational
  staged rows, AGE topology, and the ready marker can commit or roll back as one
  database transaction.

## Binding must-fix list

1. **True selection, no cosmetic bean.** `APACHE_AGE` must return a distinct
   `GraphStore` runtime whose `loadIncidentRelationPage` executes AGE Cypher.
   A write-only mirror or unused projection fails this verdict.
2. **No optional fallback.** Only `APACHE_AGE` and `RELATIONAL` are valid.
   Missing extension, catalog, preload, or privileges under `APACHE_AGE` must
   fail application context creation with an actionable bounded error. Runtime
   code validates but does not attempt privileged extension installation.
3. **Reject obsolete configuration.** Remove `apache-age-mode`, bind the typed
   properties with unknown-field rejection, migrate every repository-owned
   override, and test that the old key cannot be silently ignored.
4. **Exact batch isolation.** Every AGE vertex, edge, and ready marker must bind
   the exact publication batch. Entity identity is `(batch_id, entity_id)`;
   relation contribution edges bind batch, canonical relation, contribution,
   asset, endpoints, and orientation. No unpublished mutation may affect a
   current or historical batch.
5. **One transactional preparation.** An outer PostgreSQL transaction and
   batch advisory lock must cover relational staging, exact-batch AGE cleanup,
   bounded AGE rebuild, and ready-marker creation. The marker is written last.
   A throw at any point rolls the entire preparation back, and only the durable
   publication lifecycle may record the projection receipt afterward.
6. **Permit-bound atomic discard.** Validate the existing exact
   `ProjectionDiscardPermit` before mutation and remove AGE plus relational
   staging in one joined transaction. Current, historical, foreign, or
   ambiguous attempts must retain topology.
7. **Bounded rebuild.** Read staged relational topology in stable UUID pages no
   larger than the configured write batch. AGE payload contains fixed-size
   identity fields only; evidence text, descriptions, keywords, prompts, ACL
   payload, and model output are forbidden. Retry clears and rebuilds only the
   same batch under the same lock.
8. **Ready marker is necessary, not authority.** A read first validates the
   relational published snapshot, then requires the exact AGE ready marker.
   Missing, duplicate, malformed, or mismatched marker state fails closed. The
   marker never replaces the namespace publication head.
9. **Authorization before page limit.** AGE Cypher must constrain exact batch,
   incident entity IDs, authorized Knowledge Asset IDs, and exclusive relation
   UUID cursor before deduplication, canonical ordering, and `pageSize + 1`.
   Core still validates endpoints and page progression and applies the one
   global traversal limit.
10. **Differential conformance.** Run the shared graph-store/traversal contract
    against both backends and add real-AGE tests for hidden contributions,
    input permutations, multi-contribution deduplication, high-degree paging,
    cursor progression, empty/zero requests, invalid snapshots, historical
    snapshots, replay, mid-stage rollback, marker loss, discard, and missing
    dependency. AGE remains production-default only if these gates pass.
11. **Honest delivery.** Document AGE as a selected topology backend, not an
    authorization authority or proven accelerator. No performance claim is
    allowed until the deferred production-scale latency corpus exists.

## Final choice

Proceed with the explicit backend design subject to every must-fix above.
`APACHE_AGE` is the production default because the owner selected the
LightRAG-style dependency model and the pinned runtime image supplies AGE.
`RELATIONAL` remains the explicit reference/test backend and the safe operator
choice, never an automatic fallback.

## Rejected alternative

Reject retaining `DISABLED|OPTIONAL|REQUIRED` and merely constructing the old
projection when `REQUIRED`. That would preserve ambiguous selection, mutable
non-batch topology, and a path that can become write-only after decision 0027.
Also reject replacing relational evidence with AGE: it would collapse the ACL,
provenance, publication, and citation boundaries that distinguish OrgMemory
from LightRAG.
