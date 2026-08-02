package com.orgmemory.core.assetregistry.api;

import java.util.Objects;
import java.util.UUID;

public record AssetAuthorizationTarget(
        UUID organizationId,
        UUID assetId,
        UUID knowledgeSpaceId,
        AssetType type,
        boolean authorizationReady) {

    public AssetAuthorizationTarget {
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(assetId, "assetId");
        Objects.requireNonNull(knowledgeSpaceId, "knowledgeSpaceId");
        Objects.requireNonNull(type, "type");
    }
}
