package com.orgmemory.core.knowledge;

import java.util.UUID;

record KnowledgeAssetPublicationState(
        UUID publicationId,
        UUID organizationId,
        UUID knowledgeSpaceId,
        UUID sourceRevisionId,
        UUID knowledgeAssetId,
        UUID knowledgeAssetVersionId,
        UUID ownerUserId,
        long projectionGeneration,
        KnowledgeAssetPublicationStatus status) {

    boolean applied() {
        return status == KnowledgeAssetPublicationStatus.APPLIED;
    }
}
