package com.orgmemory.core.assetregistry;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AssetReleaseRepository extends JpaRepository<AssetRelease, UUID> {

    Optional<AssetRelease> findByIdAndAssetIdAndOrganizationId(
            UUID id, UUID assetId, UUID organizationId);

    Optional<AssetRelease> findByRevisionIdAndOrganizationId(
            UUID revisionId, UUID organizationId);

    List<AssetRelease> findByAssetIdAndOrganizationIdOrderBySequenceDesc(
            UUID assetId, UUID organizationId);

    @Query("""
            select coalesce(max(release.sequence), 0)
            from AssetRelease release
            where release.assetId = :assetId
              and release.organizationId = :organizationId
            """)
    long maxSequence(
            @Param("assetId") UUID assetId,
            @Param("organizationId") UUID organizationId);
}
