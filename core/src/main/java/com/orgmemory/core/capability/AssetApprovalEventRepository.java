package com.orgmemory.core.capability;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetApprovalEventRepository extends JpaRepository<AssetApprovalEvent, UUID> {

    List<AssetApprovalEvent> findByAssetIdOrderByCreatedAtDesc(UUID assetId);
}
