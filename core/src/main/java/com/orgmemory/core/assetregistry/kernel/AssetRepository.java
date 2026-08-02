package com.orgmemory.core.assetregistry.kernel;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AssetRepository extends JpaRepository<Asset, UUID> {

    Optional<Asset> findByIdAndOrganizationId(UUID id, UUID organizationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select asset
            from Asset asset
            where asset.id = :id
              and asset.organizationId = :organizationId
            """)
    Optional<Asset> findForUpdate(
            @Param("id") UUID id,
            @Param("organizationId") UUID organizationId);

    Optional<Asset> findByOrganizationIdAndNamespaceAndSlug(
            UUID organizationId, String namespace, String slug);
}
