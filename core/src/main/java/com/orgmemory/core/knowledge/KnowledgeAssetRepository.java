package com.orgmemory.core.knowledge;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface KnowledgeAssetRepository extends JpaRepository<KnowledgeAsset, UUID> {

    Optional<KnowledgeAsset> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<KnowledgeAsset> findByOrganizationIdAndSourceObjectId(
            UUID organizationId, UUID sourceObjectId);

    @Query("""
            select asset.id
            from KnowledgeAsset asset
            where asset.organizationId = :organizationId
              and asset.knowledgeSpaceId = :knowledgeSpaceId
              and asset.archivedAt is null
              and asset.id in :assetIds
            """)
    List<UUID> findActiveIdsInKnowledgeSpace(
            @Param("organizationId") UUID organizationId,
            @Param("knowledgeSpaceId") UUID knowledgeSpaceId,
            @Param("assetIds") Collection<UUID> assetIds);
}
