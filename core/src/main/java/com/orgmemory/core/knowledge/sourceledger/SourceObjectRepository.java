package com.orgmemory.core.knowledge.sourceledger;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SourceObjectRepository extends JpaRepository<SourceObject, UUID> {

    Optional<SourceObject> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<SourceObject> findAllByOrganizationIdAndCreatedByUserIdOrderByUpdatedAtDesc(
            UUID organizationId, UUID createdByUserId);

    List<SourceObject> findAllByOrganizationIdAndIdInOrderByUpdatedAtDesc(
            UUID organizationId, Collection<UUID> ids);

    @Query(value = """
            SELECT source.id
            FROM source_objects source
            WHERE source.organization_id = :organizationId
              AND source.created_by_user_id = :createdByUserId
              AND source.status = 'ACTIVE'
              AND source.latest_revision_id IS NOT NULL
            ORDER BY source.updated_at DESC, source.id
            LIMIT :limit
            """, nativeQuery = true)
    List<UUID> findActiveOwnedIds(
            @Param("organizationId") UUID organizationId,
            @Param("createdByUserId") UUID createdByUserId,
            @Param("limit") int limit);

    @Query(value = """
            SELECT source.*
            FROM source_objects source
            JOIN source_revisions revision
              ON revision.organization_id = source.organization_id
             AND revision.id = source.latest_revision_id
            WHERE source.organization_id = :organizationId
              AND source.status = 'ACTIVE'
              AND source.id IN (:authorizedIds)
              AND (CAST(:knowledgeSpaceId AS uuid) IS NULL OR source.knowledge_space_id = :knowledgeSpaceId)
              AND (CAST(:classification AS text) IS NULL OR source.classification = :classification)
              AND (
                    CAST(:status AS text) IS NULL
                 OR (:status = 'PROCESSING' AND revision.status IN (
                        'RECEIVED', 'VALIDATING', 'PARSING', 'CHUNKING', 'EMBEDDING', 'PUBLISHING'))
                 OR (:status = 'READY' AND revision.status = 'READY')
                 OR (:status = 'ATTENTION' AND revision.status IN ('FAILED', 'QUARANTINED'))
              )
              AND (
                    CAST(:query AS text) IS NULL
                 OR source.title ILIKE CONCAT('%', :query, '%')
                 OR revision.file_name ILIKE CONCAT('%', :query, '%')
              )
              AND (
                    CAST(:cursorUpdatedAt AS timestamptz) IS NULL
                 OR revision.updated_at < :cursorUpdatedAt
                 OR (revision.updated_at = :cursorUpdatedAt AND source.id > :cursorId)
              )
            ORDER BY revision.updated_at DESC, source.id
            LIMIT :limit
            """, nativeQuery = true)
    List<SourceObject> findVisiblePage(
            @Param("organizationId") UUID organizationId,
            @Param("authorizedIds") Collection<UUID> authorizedIds,
            @Param("knowledgeSpaceId") UUID knowledgeSpaceId,
            @Param("classification") String classification,
            @Param("status") String status,
            @Param("query") String query,
            @Param("cursorUpdatedAt") java.time.Instant cursorUpdatedAt,
            @Param("cursorId") UUID cursorId,
            @Param("limit") int limit);

    @Query(value = """
            SELECT
                COUNT(*) AS "total",
                COUNT(*) FILTER (WHERE revision.status IN (
                    'RECEIVED', 'VALIDATING', 'PARSING', 'CHUNKING', 'EMBEDDING', 'PUBLISHING'))
                    AS "processing",
                COUNT(*) FILTER (WHERE revision.status = 'READY') AS "ready",
                COUNT(*) FILTER (WHERE revision.status IN ('FAILED', 'QUARANTINED'))
                    AS "attention"
            FROM source_objects source
            JOIN source_revisions revision
              ON revision.organization_id = source.organization_id
             AND revision.id = source.latest_revision_id
            WHERE source.organization_id = :organizationId
              AND source.status = 'ACTIVE'
              AND source.id IN (:authorizedIds)
              AND (CAST(:knowledgeSpaceId AS uuid) IS NULL OR source.knowledge_space_id = :knowledgeSpaceId)
              AND (CAST(:classification AS text) IS NULL OR source.classification = :classification)
              AND (
                    CAST(:query AS text) IS NULL
                 OR source.title ILIKE CONCAT('%', :query, '%')
                 OR revision.file_name ILIKE CONCAT('%', :query, '%')
              )
            """, nativeQuery = true)
    SourceListingCountProjection countVisible(
            @Param("organizationId") UUID organizationId,
            @Param("authorizedIds") Collection<UUID> authorizedIds,
            @Param("knowledgeSpaceId") UUID knowledgeSpaceId,
            @Param("classification") String classification,
            @Param("query") String query);

    Optional<SourceObject> findByOrganizationIdAndSourceSystemAndSourceConnectionKeyAndExternalObjectId(
            UUID organizationId, String sourceSystem, String sourceConnectionKey, String externalObjectId);

    @Query("""
            SELECT count(source)
            FROM SourceObject source
            WHERE source.organizationId = :organizationId
              AND source.sourceSystem = :sourceSystem
              AND source.sourceConnectionKey = :sourceConnectionKey
              AND source.externalObjectId = :externalObjectId
              AND source.status = com.orgmemory.core.knowledge.sourceledger.SourceObjectStatus.ACTIVE
              AND source.currentRevisionId IS NOT NULL
            """)
    long countActiveCurrentRevision(
            @Param("organizationId") UUID organizationId,
            @Param("sourceSystem") String sourceSystem,
            @Param("sourceConnectionKey") String sourceConnectionKey,
            @Param("externalObjectId") String externalObjectId);

    /** The external ids a connection currently has in retrieval, for diffing against a crawl. */
    @Query("""
            SELECT source.externalObjectId
            FROM SourceObject source
            WHERE source.organizationId = :organizationId
              AND source.sourceSystem = :sourceSystem
              AND source.sourceConnectionKey = :sourceConnectionKey
              AND source.status = com.orgmemory.core.knowledge.sourceledger.SourceObjectStatus.ACTIVE
            """)
    List<String> findActiveExternalObjectIds(
            @Param("organizationId") UUID organizationId,
            @Param("sourceSystem") String sourceSystem,
            @Param("sourceConnectionKey") String sourceConnectionKey);

    /**
     * What a connection has in the ledger, counted rather than listed. An administration screen
     * asks how much arrived, not which objects; a connection can hold tens of thousands, and
     * loading them to call {@code size()} would be the same answer at a much worse price.
     */
    @Query("""
            SELECT new com.orgmemory.core.knowledge.sourceledger.SourceObjectStatusCount(
                source.status, count(source), max(source.updatedAt))
            FROM SourceObject source
            WHERE source.organizationId = :organizationId
              AND source.sourceSystem = :sourceSystem
              AND source.sourceConnectionKey = :sourceConnectionKey
            GROUP BY source.status
            """)
    List<SourceObjectStatusCount> countByStatus(
            @Param("organizationId") UUID organizationId,
            @Param("sourceSystem") String sourceSystem,
            @Param("sourceConnectionKey") String sourceConnectionKey);

    interface SourceListingCountProjection {

        long getTotal();

        long getProcessing();

        long getReady();

        long getAttention();
    }
}
