package com.orgmemory.core.assetregistry.skill;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AssetSkillActivationRepository extends JpaRepository<AssetSkillActivation, UUID> {

    Optional<AssetSkillActivation> findByOrganizationIdAndAssetIdAndUserId(
            UUID organizationId, UUID assetId, UUID userId);
}
