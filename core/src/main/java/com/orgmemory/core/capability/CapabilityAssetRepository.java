package com.orgmemory.core.capability;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CapabilityAssetRepository extends JpaRepository<CapabilityAsset, UUID> {

    List<CapabilityAsset> findByOrganizationIdAndStatusOrderByUpdatedAtDesc(UUID organizationId, AssetStatus status);

    List<CapabilityAsset> findByOrganizationIdOrderByUpdatedAtDesc(UUID organizationId);

    Optional<CapabilityAsset> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
