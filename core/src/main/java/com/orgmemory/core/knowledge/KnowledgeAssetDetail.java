package com.orgmemory.core.knowledge;

import com.orgmemory.core.permission.KnowledgeClassification;
import java.time.Instant;
import java.util.UUID;

public record KnowledgeAssetDetail(
        UUID id,
        String title,
        String content,
        String language,
        UUID departmentId,
        KnowledgeClassification classification,
        String sourceSystem,
        String externalObjectId,
        String sourceUri,
        Instant activatedAt,
        Instant updatedAt) {
}
