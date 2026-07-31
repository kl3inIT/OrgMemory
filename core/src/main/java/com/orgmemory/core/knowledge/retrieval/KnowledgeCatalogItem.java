package com.orgmemory.core.knowledge.retrieval;

import com.orgmemory.core.permission.KnowledgeClassification;
import java.util.UUID;

public record KnowledgeCatalogItem(
        UUID knowledgeAssetId,
        UUID knowledgeVersionId,
        long versionNumber,
        UUID knowledgeSpaceId,
        String title,
        String language,
        KnowledgeClassification classification,
        String contentDigest) {
}
