package com.orgmemory.core.capability;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssetUsageEventRepository extends JpaRepository<AssetUsageEvent, UUID> {

    long countByAssetId(UUID assetId);

    @Query("""
            select event.assetId as assetId, count(event) as usageCount
            from AssetUsageEvent event
            where event.assetId in :assetIds
            group by event.assetId
            """)
    List<AssetUsageTotal> summarizeByAssetIds(@Param("assetIds") Collection<UUID> assetIds);
}
