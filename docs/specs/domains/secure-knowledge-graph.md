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

The Assistant does not yet select graph retrieval, and the Sources UI keeps the
Knowledge Graph tab disabled. No graph data is exposed to users until secure
retrieval can prefilter the authorized stable asset set and canonically recheck
every returned citation.

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
