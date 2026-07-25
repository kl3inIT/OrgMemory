# LightRAG Graph Viewer Parity

## Goal

Port the active LightRAG `v1.5.4` Graph Viewer behavior into the OrgMemory
Sources workspace while keeping OrgMemory's authenticated, permission-scoped
graph API and append-only curation model.

The pinned reference is:

```text
D:\OrgMemory\tmp\upstream-lightrag-v1.5.4\lightrag_webui
```

## Scope

Port the active viewer behaviors:

- deterministic node positions before Sigma loads a graph;
- node and edge focus, selection, neighbor highlighting, and node dragging;
- local node search with camera focus;
- Circular, Circlepack, Random, Noverlaps, Force Directed, and Force Atlas
  layouts, with one active worker supervisor;
- zoom, reset, rotation, fullscreen, legend, counts, and loading state;
- node/edge labels, edge visibility/events/size, property panel, query bounds,
  and viewer preference controls;
- permission-scoped node expansion plus local hide/prune behavior;
- node and relation properties, permission-aware append-only edits, reversible
  aliases, and suppressions.

Assistant retrieval remains server-owned `MIX`. No query-mode selector or
client-supplied retrieval mode is added.

## Security Boundary

The following upstream mechanics are intentionally adapted rather than copied:

- LightRAG edit-in-place endpoints become OrgMemory `GraphCuration` records.
- Rename collisions become reversible aliases, never destructive merges.
- Label and graph search operate only on the permission-scoped graph returned
  for one Knowledge Space.
- Server configuration clamps depth and node limits. A wildcard never means
  the whole tenant-independent graph store.
- Entity-bearing viewer state is not persisted across sessions or actors.
- Graph data is invalidated on Knowledge Space, publication, or authorization
  scope changes.
- LightRAG API-key/local-storage authentication and health configuration
  exposure are excluded.
- LightRAG's development-only random graph generator is excluded from the
  product build.

## Independent Review

Claude Fable 5 reviewed this boundary on 2026-07-25. Its strongest
counterargument was that copying the upstream data layer would reintroduce
global label leakage, stale authorized graphs, destructive merge semantics, and
client-controlled unbounded graph reads. The accepted boundary ports the UI
behavior and component responsibilities while retaining OrgMemory delivery,
authorization, and curation contracts.

## Attribution

The reference implementation is MIT-licensed. Substantial adapted components
must retain LightRAG attribution in the repository's third-party notice.
