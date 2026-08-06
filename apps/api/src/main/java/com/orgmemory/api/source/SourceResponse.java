package com.orgmemory.api.source;

import com.orgmemory.core.knowledge.sourceledger.SourceSummary;
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
        UUID knowledgeAssetId,
        String knowledgeSpaceKey,
        String knowledgeSpaceName,
        String owningDepartmentName,
        String uploadedByName,
        boolean publicationComplete,
        boolean contentAvailable,
        boolean deletionAllowed,
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
                source.knowledgeAssetId(),
                source.knowledgeSpaceKey(),
                source.knowledgeSpaceName(),
                source.owningDepartmentName(),
                source.uploadedByName(),
                source.publicationComplete(),
                source.contentAvailable(),
                source.deletionAllowed(),
                source.embeddingProfileKey(),
                source.embeddingProvider(),
                source.embeddingModel(),
                source.embeddingDimensions(),
                source.createdAt(),
                source.updatedAt());
    }
}
