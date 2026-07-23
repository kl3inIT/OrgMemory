# 0011 — PostgreSQL Multi-model Graph Projection

## Status

Accepted on 2026-07-23.

## Context

The pure-Java graph kernel needs production storage for canonical identity,
evidence contributions, entity/relation embeddings, and bounded topology
expansion. LightRAG `v1.5.4` demonstrates an all-in-one PostgreSQL backend with
relational KV/status storage, pgvector, and Apache AGE. Its merged graph content
and workspace authorization model cannot be reused as OrgMemory's permission
boundary.

Running a separate graph server now would add synchronization, recovery, backup,
and authorization failure modes before a measured graph workload justifies them.

## Decision

PostgreSQL 18 is the first multi-model GraphRAG projection:

- relational tables are canonical for published heads, graph identity,
  immutable evidence contributions, ACL/model provenance, and recovery;
- pgvector stores contribution-level entity and relation embeddings beside the
  existing chunk vectors;
- Apache AGE stores tenant-separated topology identity and evidence identifiers
  for bounded Cypher candidate traversal;
- PostgreSQL FTS and a bounded recursive CTE remain deterministic fallback
  channels.

All graph ports receive an already-authorized scope. Organization and authorized
Knowledge Asset predicates apply before ranking, aggregation, traversal output,
and limits. AGE candidates are not evidence and must be reloaded through the
relational permission-scoped reader.

Vector index strategy is operational and rebuildable: exact, HNSW,
half-vector HNSW, IVFFlat, or optional VChordRQ. Embedding profile/generation
identity remains immutable. Writes use transaction advisory locks, atomic
replacement, monotonic generations, and record/payload-bounded batches.

The local deployment consolidates OrgMemory and OpenFGA onto one PostgreSQL
server while preserving separate databases and logins. OpenFGA remains the
relationship authorization service; application SQL never reads its tables.

## Consequences

The first deployment has one backup/HA boundary and no cross-database graph
projection lag. AGE failure can be configured as required and fail startup, or
disabled with the relational topology fallback.

A future Neo4j adapter may implement the same topology-candidate port and rebuild
from published relational contributions. It becomes justified only by measured
multi-hop latency, graph-algorithm, or operational requirements; it never owns
ACL, source truth, or citations.

VChordRQ is supported as an optional strategy but is not installed in the pinned
default image. Selecting it without the extension fails fast.
