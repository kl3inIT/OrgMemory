package com.orgmemory.core.capability;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CapabilityAssetRepository extends JpaRepository<CapabilityAsset, UUID> {

    List<CapabilityAsset> findByStatusOrderByUpdatedAtDesc(AssetStatus status);

    List<CapabilityAsset> findAllByOrderByUpdatedAtDesc();
}
