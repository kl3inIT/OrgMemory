package com.orgmemory.core.knowledge;

import com.orgmemory.core.permission.KnowledgeClassification;
import java.time.Instant;
import java.util.UUID;

public record KnowledgeAssetSummary(
        UUID id,
        String title,
        UUID departmentId,
        KnowledgeClassification classification,
        Instant updatedAt) {
}
