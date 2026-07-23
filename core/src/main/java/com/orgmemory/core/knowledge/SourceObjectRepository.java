package com.orgmemory.core.knowledge;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SourceObjectRepository extends JpaRepository<SourceObject, UUID> {

    List<SourceObject> findAllByOrganizationIdAndCreatedByUserIdOrderByUpdatedAtDesc(
            UUID organizationId, UUID createdByUserId);

    List<SourceObject> findAllByOrganizationIdAndIdInOrderByUpdatedAtDesc(
            UUID organizationId, Collection<UUID> ids);

    Optional<SourceObject> findByOrganizationIdAndSourceSystemAndSourceConnectionKeyAndExternalObjectId(
            UUID organizationId, String sourceSystem, String sourceConnectionKey, String externalObjectId);

    /** The external ids a connection currently has in retrieval, for diffing against a crawl. */
    @Query("""
            SELECT source.externalObjectId
            FROM SourceObject source
            WHERE source.organizationId = :organizationId
              AND source.sourceSystem = :sourceSystem
              AND source.sourceConnectionKey = :sourceConnectionKey
              AND source.status = com.orgmemory.core.knowledge.SourceObjectStatus.ACTIVE
            """)
    List<String> findActiveExternalObjectIds(
            @Param("organizationId") UUID organizationId,
            @Param("sourceSystem") String sourceSystem,
            @Param("sourceConnectionKey") String sourceConnectionKey);
}
