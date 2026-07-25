package com.orgmemory.core.assetregistry;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AssetRevisionRepository extends JpaRepository<AssetRevision, UUID> {

    Optional<AssetRevision> findByIdAndAssetIdAndOrganizationId(
            UUID id, UUID assetId, UUID organizationId);

    List<AssetRevision> findByAssetIdAndOrganizationIdOrderBySequenceDesc(
            UUID assetId, UUID organizationId);

    @Query("""
            select coalesce(max(revision.sequence), 0)
            from AssetRevision revision
            where revision.assetId = :assetId
              and revision.organizationId = :organizationId
            """)
    long maxSequence(
            @Param("assetId") UUID assetId,
            @Param("organizationId") UUID organizationId);
}
