package com.orgmemory.core.assetregistry;

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
        Instant updatedAt) {
}
