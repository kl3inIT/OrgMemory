package com.orgmemory.core.assetregistry;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
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

    @Query("""
            select new com.orgmemory.core.assetregistry.AssetSummary(
                asset.id,
                asset.type,
                asset.namespace,
                asset.slug,
                draft.title,
                draft.summary,
                asset.knowledgeSpaceId,
                asset.portfolioState)
            from Asset asset
            join AssetDraft draft
              on draft.assetId = asset.id
             and draft.organizationId = asset.organizationId
            where asset.organizationId = :organizationId
              and asset.id in :ids
              and asset.authorizationReady = true
              and (:type is null or asset.type = :type)
              and (
                    :query = ''
                    or lower(concat(
                        asset.namespace, ' ', asset.slug, ' ',
                        draft.title, ' ', draft.summary))
                       like concat('%', :query, '%')
              )
            order by asset.namespace, asset.slug
            """)
    List<AssetSummary> searchAuthorized(
            @Param("organizationId") UUID organizationId,
            @Param("ids") Collection<UUID> ids,
            @Param("query") String query,
            @Param("type") AssetType type,
            Pageable pageable);
}
