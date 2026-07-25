package com.orgmemory.core.knowledge;

import java.util.Optional;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface KnowledgeAssetVersionRepository extends JpaRepository<KnowledgeAssetVersion, UUID> {

    Optional<KnowledgeAssetVersion> findByNormalizedRecordId(UUID normalizedRecordId);

    Optional<KnowledgeAssetVersion> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<KnowledgeAssetVersion> findByKnowledgeAssetIdAndStatus(
            UUID knowledgeAssetId, KnowledgeAssetVersionStatus status);

    @Query("""
            select new com.orgmemory.core.knowledge.KnowledgeCatalogItem(
                asset.id,
                version.id,
                version.versionNumber,
                version.knowledgeSpaceId,
                version.title,
                version.language,
                version.classification,
                version.contentSha256
            )
            from KnowledgeAssetVersion version
            join KnowledgeAsset asset
              on asset.id = version.knowledgeAssetId
             and asset.organizationId = version.organizationId
             and asset.currentVersionId = version.id
             and asset.archivedAt is null
            where version.organizationId = :organizationId
              and version.status = com.orgmemory.core.knowledge.KnowledgeAssetVersionStatus.ACTIVE
              and asset.id in :assetIds
            order by lower(version.title), asset.id
            """)
    List<KnowledgeCatalogItem> findCurrentCatalogItems(
            @Param("organizationId") UUID organizationId,
            @Param("assetIds") Collection<UUID> assetIds);

    @Query("""
            select new com.orgmemory.core.knowledge.KnowledgeCatalogItem(
                asset.id,
                version.id,
                version.versionNumber,
                version.knowledgeSpaceId,
                version.title,
                version.language,
                version.classification,
                version.contentSha256
            )
            from KnowledgeAssetVersion version
            join KnowledgeAsset asset
              on asset.id = version.knowledgeAssetId
             and asset.organizationId = version.organizationId
             and asset.currentVersionId = version.id
             and asset.archivedAt is null
            where version.organizationId = :organizationId
              and version.knowledgeAssetId = :assetId
              and version.id = :versionId
              and version.status = com.orgmemory.core.knowledge.KnowledgeAssetVersionStatus.ACTIVE
            """)
    Optional<KnowledgeCatalogItem> findCurrentCatalogItem(
            @Param("organizationId") UUID organizationId,
            @Param("assetId") UUID assetId,
            @Param("versionId") UUID versionId);

    @Query("""
            select coalesce(max(version.versionNumber), 0)
            from KnowledgeAssetVersion version
            where version.knowledgeAssetId = :knowledgeAssetId
            """)
    long maximumVersionNumber(@Param("knowledgeAssetId") UUID knowledgeAssetId);
}
