package com.orgmemory.core.knowledge;

import com.orgmemory.core.permission.DeclaredAccessScope;
import com.orgmemory.core.permission.KnowledgeClassification;
import java.time.Instant;
import java.util.UUID;

public record ClaimedSourceRevision(
        UUID jobId,
        UUID organizationId,
        UUID knowledgeSpaceId,
        UUID sourceObjectId,
        UUID sourceRevisionId,
        UUID evidenceBlobId,
        UUID createdByUserId,
        UUID departmentId,
        String sourceConnectionKey,
        String externalObjectId,
        String fileName,
        String mediaType,
        long contentLength,
        String contentSha256,
        String objectKey,
        KnowledgeClassification classification,
        DeclaredAccessScope declaredAccess,
        int attempt,
        Instant createdAt) {
}
