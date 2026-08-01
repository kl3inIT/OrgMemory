package com.orgmemory.core.knowledge.asset;

import com.orgmemory.core.permission.KnowledgeClassification;
import java.util.UUID;

/** Asset-owned catalog projection for one current or historical version. */
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
