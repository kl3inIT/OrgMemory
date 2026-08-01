package com.orgmemory.core.knowledge.asset;

import java.util.UUID;

/** Asset-owned embedding identity and dimensions needed to persist chunk projections. */
public record KnowledgeEmbeddingProfileRef(
        UUID id,
        UUID organizationId,
        int dimensions) {
}
