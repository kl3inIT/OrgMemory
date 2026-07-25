package com.orgmemory.core.assetregistry;

import java.util.UUID;

record AssetAuthorizationTarget(
        UUID organizationId,
        UUID assetId,
        UUID knowledgeSpaceId,
        boolean authorizationReady) {
}
