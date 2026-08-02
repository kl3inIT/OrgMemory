package com.orgmemory.core.assetregistry;

import com.orgmemory.core.assetregistry.consumption.AssetAvailability;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AssetReleaseRepository extends JpaRepository<AssetRelease, UUID> {

    Optional<AssetRelease> findByIdAndAssetIdAndOrganizationId(
            UUID id, UUID assetId, UUID organizationId);

    Optional<AssetRelease> findByRevisionIdAndOrganizationId(
            UUID revisionId, UUID organizationId);

    Optional<AssetRelease> findByAssetIdAndOrganizationIdAndVersionLabel(
            UUID assetId, UUID organizationId, String versionLabel);

    List<AssetRelease> findByAssetIdAndOrganizationIdOrderBySequenceDesc(
            UUID assetId, UUID organizationId);

    @Query("""
            select release
            from AssetRelease release
            where release.assetId = :assetId
              and release.organizationId = :organizationId
              and exists (
                select availabilityEvent.id
                from AssetReleaseAvailabilityEvent availabilityEvent
                where availabilityEvent.releaseId = release.id
                  and availabilityEvent.availability <> :withdrawn
                  and not exists (
                    select later.id
                    from AssetReleaseAvailabilityEvent later
                    where later.releaseId = availabilityEvent.releaseId
                      and (
                        later.effectiveAt > availabilityEvent.effectiveAt
                        or (
                          later.effectiveAt = availabilityEvent.effectiveAt
                          and later.createdAt > availabilityEvent.createdAt
                        )
                      )
                  )
              )
            order by release.sequence desc
            """)
    List<AssetRelease> findLatestUsable(
            @Param("assetId") UUID assetId,
            @Param("organizationId") UUID organizationId,
            @Param("withdrawn") AssetAvailability withdrawn,
            Pageable pageable);

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
