package com.orgmemory.core.knowledge;

import java.time.Instant;

interface KnowledgeAssetSearchRow extends KnowledgeAssetAccessRow {

    String getTitle();

    Instant getUpdatedAt();
}
