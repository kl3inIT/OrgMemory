package com.orgmemory.core.knowledge;

import com.orgmemory.core.knowledge.space.KnowledgeSpaceAclGeneration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SourceAclSnapshotRepository extends JpaRepository<SourceAclSnapshot, UUID> {

    Optional<SourceAclSnapshot> findFirstByRawSourceObjectIdOrderByAclGenerationAsc(UUID rawSourceObjectId);

    Optional<SourceAclSnapshot> findByIdAndOrganizationId(UUID id, UUID organizationId);

    @Query(value = """
            SELECT
                asset.knowledge_space_id AS "knowledgeSpaceId",
                COALESCE(MAX(snapshot.acl_generation), 0) AS "aclGeneration"
            FROM source_acl_snapshots snapshot
            JOIN knowledge_asset_versions asset_version
              ON asset_version.source_acl_snapshot_id = snapshot.id
             AND asset_version.organization_id = snapshot.organization_id
            JOIN knowledge_assets asset
              ON asset.id = asset_version.knowledge_asset_id
             AND asset.organization_id = asset_version.organization_id
             AND asset.current_version_id = asset_version.id
             AND asset.archived_at IS NULL
            WHERE snapshot.organization_id = :organizationId
              AND asset.id IN (:assetIds)
            GROUP BY asset.knowledge_space_id
            """, nativeQuery = true)
    List<KnowledgeSpaceAclGeneration> maximumCurrentAclGenerations(
            @Param("organizationId") UUID organizationId,
            @Param("assetIds") Collection<UUID> assetIds);
}
