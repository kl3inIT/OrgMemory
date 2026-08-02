package com.orgmemory.core.knowledge.sourceledger;

import com.orgmemory.core.knowledge.acl.AclAuthority;


import com.orgmemory.core.permission.KnowledgeClassification;
import java.time.Instant;
import java.util.UUID;

public record SourceSummary(
        UUID id,
        String title,
        String sourceSystem,
        AclAuthority aclAuthority,
        SourceRevisionStatus status,
        KnowledgeClassification classification,
        String fileName,
        String mediaType,
        long contentLength,
        String failureCode,
        String failureMessage,
        UUID knowledgeAssetId,
        boolean contentAvailable,
        boolean deletionAllowed,
        String embeddingProfileKey,
        String embeddingProvider,
        String embeddingModel,
        Integer embeddingDimensions,
        Instant createdAt,
        Instant updatedAt) {
}
