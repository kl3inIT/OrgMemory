package com.orgmemory.core.capability;

import java.util.UUID;

public interface AssetUsageTotal {

    UUID getAssetId();

    long getUsageCount();
}
