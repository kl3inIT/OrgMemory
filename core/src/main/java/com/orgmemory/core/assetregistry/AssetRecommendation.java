package com.orgmemory.core.assetregistry;

import com.orgmemory.core.assetregistry.api.AssetPortfolioState;
import com.orgmemory.core.assetregistry.api.AssetType;
import java.time.Instant;
import java.util.UUID;

public record AssetRecommendation(
        UUID assetId,
        AssetType type,
        String namespace,
        String slug,
        String title,
        String summary,
        UUID knowledgeSpaceId,
        AssetPortfolioState portfolioState,
        UUID releaseId,
        String versionLabel,
        String releaseDigest,
        AssetAvailability availability,
        Instant releasedAt) {
}
