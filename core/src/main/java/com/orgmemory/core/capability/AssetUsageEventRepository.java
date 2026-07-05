package com.orgmemory.core.capability;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetUsageEventRepository extends JpaRepository<AssetUsageEvent, UUID> {

    long countByAssetId(UUID assetId);
}
