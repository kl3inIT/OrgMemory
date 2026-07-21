package com.orgmemory.core.knowledge;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SourceAclHeadRepository extends JpaRepository<SourceAclHead, UUID> {

    Optional<SourceAclHead> findByOrganizationIdAndSourceSystemAndSourceConnectionKeyAndExternalObjectId(
            UUID organizationId,
            String sourceSystem,
            String sourceConnectionKey,
            String externalObjectId);

    @Query(value = """
            SELECT sah.*
            FROM source_acl_heads sah
            JOIN raw_source_objects rso
              ON rso.organization_id = sah.organization_id
             AND rso.source_system = sah.source_system
             AND rso.source_connection_key = sah.source_connection_key
             AND rso.external_object_id = sah.external_object_id
            WHERE rso.id = :rawSourceObjectId
              AND rso.organization_id = :organizationId
            """, nativeQuery = true)
    Optional<SourceAclHead> findForRawSourceObject(
            @Param("rawSourceObjectId") UUID rawSourceObjectId,
            @Param("organizationId") UUID organizationId);
}
