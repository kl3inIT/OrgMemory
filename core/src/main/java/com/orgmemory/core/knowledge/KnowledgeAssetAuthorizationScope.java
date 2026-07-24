package com.orgmemory.core.knowledge;

import java.util.Objects;
import java.util.UUID;

record KnowledgeAssetAuthorizationScope(UUID assetId, UUID knowledgeSpaceId) {

    KnowledgeAssetAuthorizationScope {
        Objects.requireNonNull(assetId, "assetId");
        Objects.requireNonNull(knowledgeSpaceId, "knowledgeSpaceId");
    }
}
