package com.orgmemory.api.graph;

record KnowledgeGraphEdgeResponse(
        String id,
        String source,
        String target,
        GraphEdgeKind kind,
        String label,
        int weight
) {
}
