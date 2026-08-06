package com.orgmemory.core.knowledge.asset;

import com.orgmemory.graphrag.storage.ProjectionNamespace;
import java.util.UUID;

/** Stable projection namespace for one organization's Knowledge Space. */
public final class KnowledgeProjectionNamespaces {

    private KnowledgeProjectionNamespaces() {
    }

    public static ProjectionNamespace forSpace(UUID organizationId, UUID knowledgeSpaceId) {
        return new ProjectionNamespace(organizationId, "default", knowledgeSpaceId.toString());
    }

    public static ProjectionNamespace forCanonicalQuery(UUID organizationId) {
        return new ProjectionNamespace(organizationId, "default", "canonical-query");
    }
}
