package com.orgmemory.core.capability;

import java.util.Objects;

public record CapabilityAssetListing(CapabilityAsset asset, long usageCount) {

    public CapabilityAssetListing {
        Objects.requireNonNull(asset, "asset");
        if (usageCount < 0) {
            throw new IllegalArgumentException("usageCount cannot be negative");
        }
    }
}
