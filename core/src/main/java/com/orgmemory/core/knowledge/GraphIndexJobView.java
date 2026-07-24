package com.orgmemory.core.knowledge;

import java.time.Instant;
import java.util.UUID;

public record GraphIndexJobView(
        UUID id,
        UUID knowledgeAssetId,
        UUID knowledgeAssetVersionId,
        UUID sourceRevisionId,
        long projectionGeneration,
        String status,
        int attempt,
        boolean cancellationRequested,
        Instant cancellationRequestedAt,
        String lastErrorCode,
        String lastErrorMessage,
        Instant completedAt) {
}
