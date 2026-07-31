package com.orgmemory.core.knowledge.asset;

import java.util.Objects;
import java.util.UUID;

public record KnowledgeAssetAuthorizationScope(UUID assetId, UUID knowledgeSpaceId) {

    public KnowledgeAssetAuthorizationScope {
        Objects.requireNonNull(assetId, "assetId");
        Objects.requireNonNull(knowledgeSpaceId, "knowledgeSpaceId");
    }
}
