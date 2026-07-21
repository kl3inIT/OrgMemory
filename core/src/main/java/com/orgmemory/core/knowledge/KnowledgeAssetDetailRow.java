package com.orgmemory.core.knowledge;

import java.time.Instant;

interface KnowledgeAssetDetailRow extends KnowledgeAssetAccessRow {

    String getTitle();

    String getContent();

    String getLanguage();

    String getSourceSystem();

    String getExternalObjectId();

    String getSourceUri();

    Instant getActivatedAt();

    Instant getUpdatedAt();
}
