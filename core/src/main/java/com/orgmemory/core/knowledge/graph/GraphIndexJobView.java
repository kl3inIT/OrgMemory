package com.orgmemory.core.knowledge.graph;

import java.time.Instant;
import java.util.UUID;

public record GraphIndexJobView(
        UUID id,
        UUID knowledgeAssetId,
        UUID knowledgeAssetVersionId,
        UUID sourceRevisionId,
        long projectionGeneration,
        UUID graphProcessingProfileId,
        String graphProcessingProfileSha256,
        String status,
        int attempt,
        boolean cancellationRequested,
        Instant cancellationRequestedAt,
        String lastErrorCode,
        String lastErrorMessage,
        Instant completedAt) {
}
