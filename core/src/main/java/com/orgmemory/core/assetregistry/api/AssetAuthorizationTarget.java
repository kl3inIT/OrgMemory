package com.orgmemory.core.assetregistry.api;

import java.util.UUID;

public record AssetAuthorizationTarget(
        UUID organizationId,
        UUID assetId,
        UUID knowledgeSpaceId,
        AssetType type,
        boolean authorizationReady) {
}
