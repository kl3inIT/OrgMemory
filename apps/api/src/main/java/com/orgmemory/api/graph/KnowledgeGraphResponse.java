package com.orgmemory.api.graph;

import java.util.List;

record KnowledgeGraphResponse(
        List<KnowledgeGraphNodeResponse> nodes,
        List<KnowledgeGraphEdgeResponse> edges,
        String focusNodeId,
        int depth
) {
}
