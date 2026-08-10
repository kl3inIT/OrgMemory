package com.orgmemory.core.assetregistry;

import com.orgmemory.core.assetregistry.api.AssetPortfolioState;
import com.orgmemory.core.assetregistry.api.AssetType;
import com.orgmemory.core.assetregistry.api.AssetSharingState;
import java.time.Instant;
import java.util.UUID;

public record AssetSummary(
        UUID id,
        AssetType type,
        String namespace,
        String slug,
        String title,
        String summary,
        UUID knowledgeSpaceId,
        AssetPortfolioState portfolioState,
        UUID ownerUserId,
        AssetSharingState sharingState,
        Instant updatedAt) {
}
