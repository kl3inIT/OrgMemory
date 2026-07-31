package com.orgmemory.core.assetregistry;

import java.util.List;

public record AssetSummaryPage(
        List<AssetSummary> items,
        long total,
        int page,
        int pageSize,
        int totalPages,
        AssetOwnedSort sort) {

    public AssetSummaryPage {
        items = List.copyOf(items);
    }

    static AssetSummaryPage empty(
            int page, int pageSize, AssetOwnedSort sort) {
        return new AssetSummaryPage(List.of(), 0, page, pageSize, 0, sort);
    }
}
