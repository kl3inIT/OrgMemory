package com.orgmemory.core.knowledge.retrieval;

import java.util.UUID;

public record SecureRetrievalCandidate(
        UUID organizationId,
        UUID chunkId,
        UUID knowledgeAssetId,
        UUID sourceObjectId,
        UUID sourceRevisionId,
        String title,
        String content,
        String sourceUri,
        Integer startPage,
        Integer endPage,
        String heading,
        double score,
        UUID ingestionAclSnapshotId,
        UUID currentAclSnapshotId,
        String authorizationModelId,
        UUID embeddingProfileId,
        long projectionGeneration) {
}
