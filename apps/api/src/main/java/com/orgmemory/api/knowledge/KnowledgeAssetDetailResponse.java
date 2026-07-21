package com.orgmemory.api.knowledge;

import com.orgmemory.core.knowledge.KnowledgeAssetDetail;
import com.orgmemory.core.permission.KnowledgeClassification;
import java.time.Instant;
import java.util.UUID;

record KnowledgeAssetDetailResponse(
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

    static KnowledgeAssetDetailResponse from(KnowledgeAssetDetail asset) {
        return new KnowledgeAssetDetailResponse(
                asset.id(),
                asset.title(),
                asset.content(),
                asset.language(),
                asset.departmentId(),
                asset.classification(),
                asset.sourceSystem(),
                asset.externalObjectId(),
                asset.sourceUri(),
                asset.activatedAt(),
                asset.updatedAt());
    }
}
