package com.orgmemory.core.knowledge;

import java.util.UUID;

public record RetrievedKnowledgeEvidence(
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
        double lexicalScore,
        double vectorScore,
        double relevanceScore,
        UUID ingestionAclSnapshotId,
        UUID currentAclSnapshotId,
        String authorizationModelId,
        UUID embeddingProfileId,
        long projectionGeneration) {
}
