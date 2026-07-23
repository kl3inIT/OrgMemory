package com.orgmemory.core.knowledge;

import java.util.Optional;
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
            select coalesce(max(version.versionNumber), 0)
            from KnowledgeAssetVersion version
            where version.knowledgeAssetId = :knowledgeAssetId
            """)
    long maximumVersionNumber(@Param("knowledgeAssetId") UUID knowledgeAssetId);
}
