package com.orgmemory.core.knowledge;

import com.orgmemory.core.permission.KnowledgeClassification;
import java.time.Instant;
import java.util.UUID;

public record SourceSummary(
        UUID id,
        String title,
        SourceType sourceType,
        SourceRevisionStatus status,
        KnowledgeClassification classification,
        String fileName,
        String mediaType,
        long contentLength,
        String failureCode,
        String failureMessage,
        String embeddingProfileKey,
        String embeddingProvider,
        String embeddingModel,
        Integer embeddingDimensions,
        Instant createdAt,
        Instant updatedAt) {
}
