package com.orgmemory.core.assetregistry;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AssetDraftRepository extends JpaRepository<AssetDraft, UUID> {

    Optional<AssetDraft> findByAssetIdAndOrganizationId(UUID assetId, UUID organizationId);
}
