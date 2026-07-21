# Relational Capability Graph Spec

## Current Behavior

The graph endpoint derives a visualization from authorized Capability Assets,
owners, backup owners, departments, types, tags, and processes. It uses
relational registry data, not semantic entity/relation extraction. It is not
Neo4j, LightRAG, or the planned permission-scoped knowledge graph.

## Source Modules

- `apps.api.graph`
- current `web` graph route

## Related Decisions

- [0005](../../decisions/0005-secure-java-graph-kernel.md)
