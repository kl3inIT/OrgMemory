package com.orgmemory.core.assetregistry;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AssetReviewCaseRepository extends JpaRepository<AssetReviewCase, UUID> {

    Optional<AssetReviewCase> findByIdAndAssetIdAndOrganizationId(
            UUID id, UUID assetId, UUID organizationId);

    Optional<AssetReviewCase> findByRevisionIdAndOrganizationId(
            UUID revisionId, UUID organizationId);

    List<AssetReviewCase> findByAssetIdAndOrganizationIdOrderByCreatedAtDesc(
            UUID assetId, UUID organizationId);

    boolean existsByAssetIdAndOrganizationIdAndState(
            UUID assetId, UUID organizationId, AssetReviewState state);
}
