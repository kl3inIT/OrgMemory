package com.orgmemory.core.knowledge;

import com.orgmemory.graphrag.storage.ProjectionNamespace;
import java.util.UUID;

final class KnowledgeProjectionNamespaces {

    private KnowledgeProjectionNamespaces() {
    }

    static ProjectionNamespace forSpace(UUID organizationId, UUID knowledgeSpaceId) {
        return new ProjectionNamespace(organizationId, "default", knowledgeSpaceId.toString());
    }
}
