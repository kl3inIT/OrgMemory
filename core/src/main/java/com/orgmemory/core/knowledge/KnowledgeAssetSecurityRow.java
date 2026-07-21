package com.orgmemory.core.knowledge;

interface KnowledgeAssetSecurityRow extends KnowledgeAssetAccessRow {

    String getAssetStatus();

    String getRawStatus();

    String getNormalizedStatus();
}
