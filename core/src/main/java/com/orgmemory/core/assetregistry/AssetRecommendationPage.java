package com.orgmemory.core.assetregistry;

import java.util.List;

public record AssetRecommendationPage(
        List<AssetRecommendation> items,
        long total,
        int page,
        int pageSize,
        int totalPages,
        AssetCatalogSort sort) {

    public AssetRecommendationPage {
        items = List.copyOf(items);
    }

    static AssetRecommendationPage empty(
            int page, int pageSize, AssetCatalogSort sort) {
        return new AssetRecommendationPage(
                List.of(), 0, page, pageSize, 0, sort);
    }
}
