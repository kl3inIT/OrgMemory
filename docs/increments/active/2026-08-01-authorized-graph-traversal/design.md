# Authorized graph traversal coordinator

Date: 2026-08-01

## Outcome

Make authorized multi-source graph traversal one non-overridable core policy.
Storage adapters provide exact snapshot validation and stable, permission-scoped
relation pages; they no longer return final expanded entity identifiers.

The result contract is exact completion or failure. A bounded partial traversal
is not represented by the existing `List<UUID>` API and therefore cannot be
returned silently.

## Current defect

`GraphStore.expandEntityIds` is abstract. The production adapters and the
in-memory query double consequently disagree on observable behavior:

- Neo4j and OpenSearch reject zero limits while PostgreSQL and the in-memory
  implementation accept them;
- OpenSearch empty-seed and PostgreSQL zero-limit paths can return before the
  exact publication snapshot is validated;
- PostgreSQL orders by minimum depth then entity identity while Java paths can
  preserve seed order;
- Neo4j and OpenSearch Java traversal read one fixed relation prefix, whereas
  PostgreSQL's recursive CTE traverses a different edge set;
- successful OpenSearch PPL output is rechecked only as visible entities, so a
  native prefix can lose authorized results after hidden candidates are removed.

The shared conformance suite proves one hidden-edge case but does not pin the
guards, early-return validation, multi-seed ordering, one global limit,
high-degree completeness, or native/reference equivalence.

## Pinned reference study

LightRAG tag `v1.5.4`, commit
`9a45b64c2ee25b1d806e90db926a8af37480bb16`, separates normal retrieval from
graph exploration. Normal retrieval vector-selects nodes and batch-loads
one-hop adjacency in query orchestration. The explorer delegates multi-hop
traversal to backend-specific NetworkX, PPL, APOC, and PostgreSQL/AGE paths.

OrgMemory keeps that cheap one-hop query shape, but does not inherit the
explorer's backend-specific semantics. Authorization and publication-snapshot
behavior are part of the secure retrieval contract, not adapter policy.

## Selected design

### One result-producing coordinator

Add a framework-neutral `AuthorizedGraphTraversal` in graph-rag-core. It is the
only implementation that returns expanded entity identifiers. The authorized
query projection delegates to it; no storage adapter exposes a final-ID
traversal override.

The coordinator owns this sequence:

1. validate non-null inputs, null-free seeds, and non-negative depth and limit;
2. explicitly validate the exact graph publication snapshot;
3. return for zero limit or empty seeds only after snapshot validation;
4. deduplicate and authorize seeds through visible entity evidence;
5. perform level-synchronous multi-source breadth-first traversal;
6. drain the complete authorized relation page sequence for the whole frontier;
7. authorize candidate endpoints, retain their minimum depth, and sort by
   canonical entity UUID;
8. apply one global entity limit only after the complete level is normalized.

### Minimal storage source

Introduce an `AuthorizedGraphTraversalSource` storage contract. `GraphStore`
extends it and retains graph persistence/read operations, but loses
`expandEntityIds`.

The source exposes:

- explicit `validateSnapshot(scope, snapshot)`;
- authorized entity loading;
- incident-relation pages ordered by canonical relation UUID, using an
  exclusive relation-UUID cursor and an explicit next cursor.

Each page is bound to the supplied immutable snapshot. Page results require
visible relation contributions and visible endpoint entity contributions, so
hidden topology does not consume the traversal page stream. Adapters fetch one
extra row to distinguish a full final page from a truncated page. The
coordinator rejects non-increasing IDs, repeated cursors, cursors not matching
the final returned relation, and a next cursor on an empty page.

Paging bounds transport and per-page memory, not total semantic work. The
coordinator drains a complete level. If the adapter cannot prove completion or
the snapshot changes, traversal fails rather than returning a partial prefix.

### Native acceleration

The existing PostgreSQL recursive CTE and OpenSearch PPL final-ID paths are
disabled by this increment when adapter authority is removed. They may return
only behind a later internal accelerator contract carrying snapshot identity,
minimum-depth candidates, relation/path witnesses, and explicit completeness.
Core must re-authorize and reconstruct the result before normalization.

This increment does not retain an unverified accelerator merely to preserve a
fast path. PostgreSQL remains the production adapter, so focused integration and
performance evidence will decide whether an evidence-bearing CTE is restored
before closure. Neo4j uses the reference path until measured need justifies
APOC under the same contract.

## Architecture challenge

Claude was unavailable because its quota was exhausted. With the project
owner's explicit direction, two independent Codex Ultra sessions argued the
alternatives, rebutted each other, and a third fresh Ultra session judged only
a self-contained debate record with no repository or web access.

### Proposal selected by the judge

Move result-producing traversal policy into the non-overridable core
coordinator. Backends expose snapshot-bound authorized pages and optional
evidence-bearing acceleration only.

### Strongest counterargument

A core default implementation on the existing `GraphStore` would be a smaller
migration. No coordinator can prove that an adapter omitted a relation or lied
about completeness, so snapshot protocols, authoritative reads, and real-backend
differential tests remain required either way. Backend proof shapes also differ,
making a common accelerator evidence type a possible new drift surface.

### Repository evidence considered

- The current abstract final-result method has already produced four observable
  contracts.
- Existing relation reads have a numeric limit but no continuation or
  completeness signal.
- The authorized projection is already the composition point above replaceable
  stores.
- PostgreSQL/Neo4j relation visibility already requires visible relation
  evidence and visible endpoints, so a shared authorized page contract matches
  the established security model.
- The production query defaults to one hop, limiting the initial performance
  risk of a correct reference path.

### Final choice

Choose the coordinator. Tests can detect sampled adapter drift but cannot stop
an adapter final-result override from bypassing snapshot validation,
authorization recheck, complete-level normalization, or fail-closed behavior.
Removing that authority is worth the explicit source seam.

### Rejected alternative

Reject a `GraphStore` default method with native final-ID overrides. It retains
the semantic escape hatch that caused the defect. A native path may be restored
only as evidence consumed by core, never as the public result producer.

## Non-goals

- Changing public query strategies or exposing a multi-hop request parameter.
- Changing graph explorer authorization or UI.
- Making OpenSearch or Neo4j canonical evidence stores.
- Adding a generic distributed graph execution framework.
- Returning partial results under the current list contract.

## Exit proof

- Characterization tests first record all current guard, snapshot, ordering,
  and high-degree differences before implementation changes.
- One shared traversal conformance suite passes for in-memory, PostgreSQL,
  Neo4j, and OpenSearch.
- Zero limit and empty seeds still validate the exact snapshot.
- Random seed/page order produces the same minimum-depth/UUID result.
- High-degree traversal crosses multiple pages without silent truncation.
- Hidden relation or endpoint evidence cannot affect result identity, order,
  count, or error shape.
- No production adapter contains a final-ID traversal override.
- Focused integration gates, static checks, and the terminating clean test pass.

