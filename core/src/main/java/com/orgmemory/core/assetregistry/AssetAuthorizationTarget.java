package com.orgmemory.core.assetregistry;

import com.orgmemory.core.assetregistry.api.AssetType;
import java.util.UUID;

record AssetAuthorizationTarget(
        UUID organizationId,
        UUID assetId,
        UUID knowledgeSpaceId,
        AssetType type,
        boolean authorizationReady) {
}
