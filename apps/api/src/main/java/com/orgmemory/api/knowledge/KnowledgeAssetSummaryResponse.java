package com.orgmemory.api.knowledge;

import com.orgmemory.core.knowledge.KnowledgeAssetSummary;
import com.orgmemory.core.permission.KnowledgeClassification;
import java.time.Instant;
import java.util.UUID;

record KnowledgeAssetSummaryResponse(
        UUID id,
        String title,
        UUID departmentId,
        KnowledgeClassification classification,
        Instant updatedAt) {

    static KnowledgeAssetSummaryResponse from(KnowledgeAssetSummary asset) {
        return new KnowledgeAssetSummaryResponse(
                asset.id(),
                asset.title(),
                asset.departmentId(),
                asset.classification(),
                asset.updatedAt());
    }
}
