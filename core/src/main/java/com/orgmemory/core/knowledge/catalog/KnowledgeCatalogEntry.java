package com.orgmemory.core.knowledge.catalog;

import com.orgmemory.core.permission.KnowledgeClassification;
import java.util.UUID;

/** Immutable catalog value safe for consumers outside the Knowledge module. */
public record KnowledgeCatalogEntry(
        UUID knowledgeAssetId,
        UUID knowledgeVersionId,
        long versionNumber,
        UUID knowledgeSpaceId,
        String title,
        String language,
        KnowledgeClassification classification,
        String contentDigest) {
}
