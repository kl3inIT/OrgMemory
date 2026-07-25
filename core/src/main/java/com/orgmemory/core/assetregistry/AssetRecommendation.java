package com.orgmemory.core.assetregistry;

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
        AssetAvailability availability) {
}
