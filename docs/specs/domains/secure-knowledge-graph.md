# Secure Knowledge Graph Spec

## Current Behavior

The framework-neutral graph kernel, Spring AI extraction adapter, and PostgreSQL
projection adapter are implemented and tested. Contributions pin organization,
stable Knowledge Asset, immutable Knowledge Asset version, source revision,
chunk, ACL/model provenance, extraction profile, and projection generation.
PostgreSQL/pgvector own evidence and ranking; Apache AGE is a rebuildable,
tenant-separated topology candidate index with a bounded relational fallback.

The worker now runs durable, bounded extraction and atomically publishes one
complete graph generation for each current source-backed Knowledge Asset
version. Jobs are created only after the canonical source revision is `READY`;
they pin version, chunks, ACL generation, embedding profile, extractor route,
and prompt version. Replay is deterministic, stale versions are superseded, and
an embedding or publication failure cannot expose a partial generation.

The Assistant selects permission-scoped `MIX` retrieval by default. It closes
and rechecks every selected entity, relation, and chunk contribution before the
final prompt is rendered. The Sources UI exposes a bounded Knowledge Graph
explorer to the curator/admin authorization boundary; it reads the same
published projection and does not create node-owned ACLs or persist a global
permission-independent merged description.

## Source Modules

- `components.graph-rag-core`
- `components.graph-rag-testkit`
- `integrations.graph-rag-spring-ai`
- `integrations.graph-rag-postgres`
- `apps.worker`

## Related Decisions

- [0005](../../decisions/0005-secure-java-graph-kernel.md)
- [0010](../../decisions/0010-internal-retrieval-strategies-one-hop-graph.md)
- [0011](../../decisions/0011-postgresql-multimodel-graph-projection.md)
- [0012](../../decisions/0012-stable-knowledge-assets-and-immutable-versions.md)
