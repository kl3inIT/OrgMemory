package com.orgmemory.api.source;

import com.orgmemory.core.knowledge.SourceSummary;
import java.time.Instant;
import java.util.UUID;

record SourceResponse(
        UUID id,
        String title,
        String sourceSystem,
        String aclAuthority,
        String status,
        String classification,
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

    static SourceResponse from(SourceSummary source) {
        return new SourceResponse(
                source.id(),
                source.title(),
                source.sourceSystem(),
                source.aclAuthority().name(),
                source.status().name(),
                source.classification().name(),
                source.fileName(),
                source.mediaType(),
                source.contentLength(),
                source.failureCode(),
                source.failureMessage(),
                source.embeddingProfileKey(),
                source.embeddingProvider(),
                source.embeddingModel(),
                source.embeddingDimensions(),
                source.createdAt(),
                source.updatedAt());
    }
}
