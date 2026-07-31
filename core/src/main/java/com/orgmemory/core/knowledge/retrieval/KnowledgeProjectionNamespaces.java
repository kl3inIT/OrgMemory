package com.orgmemory.core.knowledge.retrieval;

import com.orgmemory.graphrag.storage.ProjectionNamespace;
import java.util.UUID;

public final class KnowledgeProjectionNamespaces {

    private KnowledgeProjectionNamespaces() {
    }

    public static ProjectionNamespace forSpace(UUID organizationId, UUID knowledgeSpaceId) {
        return new ProjectionNamespace(organizationId, "default", knowledgeSpaceId.toString());
    }
}
