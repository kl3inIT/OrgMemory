package com.orgmemory.core.capability;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetVersionRepository extends JpaRepository<AssetVersion, UUID> {

    List<AssetVersion> findByAssetIdOrderByVersionNumberDesc(UUID assetId);
}
