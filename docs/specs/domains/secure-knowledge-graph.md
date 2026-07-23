# Secure Knowledge Graph Spec

## Current Behavior

The framework-neutral graph kernel, Spring AI extraction adapter, and PostgreSQL
projection adapter are implemented and tested. Contributions pin organization,
stable Knowledge Asset, immutable Knowledge Asset version, source revision,
chunk, ACL/model provenance, extraction profile, and projection generation.
PostgreSQL/pgvector own evidence and ranking; Apache AGE is a rebuildable,
tenant-separated topology candidate index with a bounded relational fallback.

The worker does not yet run extraction/publication, the Assistant does not yet
select graph retrieval, and the Sources UI keeps the Knowledge Graph tab
disabled. No graph data is exposed until secure retrieval can prefilter the
authorized stable asset set and canonically recheck every returned citation.

## Source Modules

- `components.graph-rag-core`
- `components.graph-rag-testkit`
- `integrations.graph-rag-spring-ai`
- `integrations.graph-rag-postgres`

## Related Decisions

- [0005](../../decisions/0005-secure-java-graph-kernel.md)
- [0010](../../decisions/0010-internal-retrieval-strategies-one-hop-graph.md)
- [0011](../../decisions/0011-postgresql-multimodel-graph-projection.md)
- [0012](../../decisions/0012-stable-knowledge-assets-and-immutable-versions.md)
