package com.orgmemory.api.graph;

record KnowledgeGraphNodeResponse(
        String id,
        String label,
        GraphNodeKind kind,
        String detail,
        String assetId,
        String assetType,
        String status,
        int weight
) {
}
