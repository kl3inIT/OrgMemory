package com.orgmemory.core.assetregistry;

import com.orgmemory.core.assetregistry.api.AssetPortfolioState;
import com.orgmemory.core.assetregistry.api.AssetSharingState;
import com.orgmemory.core.assetregistry.api.AssetType;
import java.util.List;
import java.util.UUID;

/** Released-only Asset projection safe for Viewer audiences. */
public record AssetReleasedView(
        UUID id,
        AssetType type,
        String namespace,
        String slug,
        UUID knowledgeSpaceId,
        AssetPortfolioState portfolioState,
        UUID ownerUserId,
        AssetSharingState sharingState,
        List<AssetView.Release> releases) {

    public AssetReleasedView {
        releases = List.copyOf(releases);
    }
}
