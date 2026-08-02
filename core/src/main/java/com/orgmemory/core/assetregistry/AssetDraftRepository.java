package com.orgmemory.core.assetregistry;

import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AssetDraftRepository extends JpaRepository<AssetDraft, UUID> {

    Optional<AssetDraft> findByAssetIdAndOrganizationId(UUID assetId, UUID organizationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select draft from AssetDraft draft
            where draft.assetId = :assetId
              and draft.organizationId = :organizationId
            """)
    Optional<AssetDraft> findForUpdate(
            @Param("assetId") UUID assetId,
            @Param("organizationId") UUID organizationId);
}
