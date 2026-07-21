package com.orgmemory.core.knowledge;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface KnowledgeAssetRepository extends JpaRepository<KnowledgeAsset, UUID> {

    String ACCESS_COLUMNS = """
            ka.id AS id,
            ka.department_id AS "departmentId",
            ka.classification AS classification,
            ka.declared_access AS "declaredAccess",
            ka.orgmemory_gate AS "orgMemoryGate",
            ingestion_sas.id AS "ingestionSnapshotId",
            current_sas.id AS "currentSnapshotId"
            """;

    String SEARCH_COLUMNS = ACCESS_COLUMNS + """
            , ka.title AS title,
            ka.updated_at AS "updatedAt"
            """;

    String DETAIL_COLUMNS = ACCESS_COLUMNS + """
            , ka.title AS title,
            ka.content AS content,
            ka.language AS language,
            rso.source_system AS "sourceSystem",
            rso.external_object_id AS "externalObjectId",
            rso.source_uri AS "sourceUri",
            ka.activated_at AS "activatedAt",
            ka.updated_at AS "updatedAt"
            """;

    String SECURITY_COLUMNS = ACCESS_COLUMNS + """
            , ka.status AS "assetStatus",
            rso.status AS "rawStatus",
            nr.status AS "normalizedStatus"
            """;

    String PRINCIPAL_MATCH = """
            (
                (sae.principal_type = 'ORGMEMORY_USER' AND sae.principal_key = :actorUserKey)
                OR (
                    sae.principal_type = 'ORGMEMORY_DEPARTMENT'
                    AND :actorDepartmentKey IS NOT NULL
                    AND sae.principal_key = :actorDepartmentKey
                )
                OR (
                    sae.principal_type = 'ORGMEMORY_ORGANIZATION'
                    AND sae.principal_key = :actorOrganizationKey
                )
            )
            """;

    String AUTHORIZATION_PREDICATE = """
            ka.organization_id = :organizationId
            AND ka.status = 'ACTIVE'
            AND ka.orgmemory_gate = 'ALLOW'
            AND rso.status = 'NORMALIZED'
            AND nr.status = 'PROMOTED'
            AND ingestion_sas.capture_status = 'COMPLETE'
            AND current_sas.capture_status = 'COMPLETE'
            AND current_sas.valid_until > :evaluatedAt
            AND NOT EXISTS (
                SELECT 1
                FROM source_acl_entries sae
                WHERE sae.source_acl_snapshot_id IN (ingestion_sas.id, current_sas.id)
                  AND sae.organization_id = ka.organization_id
                  AND sae.gate = 'DENY'
                  AND
            """ + PRINCIPAL_MATCH + """
            )
            AND (
                ingestion_sas.default_gate = 'ALLOW'
                OR EXISTS (
                    SELECT 1
                    FROM source_acl_entries sae
                    WHERE sae.source_acl_snapshot_id = ingestion_sas.id
                      AND sae.organization_id = ka.organization_id
                      AND sae.gate = 'ALLOW'
                      AND
            """ + PRINCIPAL_MATCH + """
                )
            )
            AND (
                current_sas.default_gate = 'ALLOW'
                OR EXISTS (
                    SELECT 1
                    FROM source_acl_entries sae
                    WHERE sae.source_acl_snapshot_id = current_sas.id
                      AND sae.organization_id = ka.organization_id
                      AND sae.gate = 'ALLOW'
                      AND
            """ + PRINCIPAL_MATCH + """
                )
            )
            AND (
                (ka.classification = 'PUBLIC' AND ka.declared_access = 'ALL')
                OR (ka.classification = 'INTERNAL' AND ka.declared_access = 'ALL_EMPLOYEES')
                OR (
                    ka.classification = 'CONFIDENTIAL'
                    AND ka.declared_access = 'OWN_DEPARTMENT'
                    AND :actorDepartmentId IS NOT NULL
                    AND (:knowledgeRole = 'EXECUTIVE' OR ka.department_id = :actorDepartmentId)
                )
                OR (
                    ka.classification = 'RESTRICTED'
                    AND ka.declared_access = 'EXECUTIVE_ONLY'
                    AND :knowledgeRole = 'EXECUTIVE'
                )
            )
            """;

    String AUTHORIZED_FROM = """
            FROM knowledge_assets ka
            JOIN raw_source_objects rso
              ON rso.id = ka.raw_source_object_id
             AND rso.organization_id = ka.organization_id
            JOIN normalized_records nr
              ON nr.id = ka.normalized_record_id
             AND nr.organization_id = ka.organization_id
            JOIN source_acl_snapshots ingestion_sas
              ON ingestion_sas.id = ka.source_acl_snapshot_id
             AND ingestion_sas.organization_id = ka.organization_id
            JOIN source_acl_snapshot_seals ingestion_seal
              ON ingestion_seal.source_acl_snapshot_id = ingestion_sas.id
             AND ingestion_seal.organization_id = ka.organization_id
            JOIN source_acl_heads sah
              ON sah.organization_id = ka.organization_id
             AND sah.source_system = rso.source_system
             AND sah.source_connection_key = rso.source_connection_key
             AND sah.external_object_id = rso.external_object_id
            JOIN source_acl_snapshots current_sas
              ON current_sas.id = sah.current_snapshot_id
             AND current_sas.organization_id = ka.organization_id
            JOIN source_acl_snapshot_seals current_seal
              ON current_seal.source_acl_snapshot_id = current_sas.id
             AND current_seal.organization_id = ka.organization_id
            WHERE
            """;

    String SECURITY_FROM = """
            FROM knowledge_assets ka
            JOIN raw_source_objects rso
              ON rso.id = ka.raw_source_object_id
             AND rso.organization_id = ka.organization_id
            JOIN normalized_records nr
              ON nr.id = ka.normalized_record_id
             AND nr.organization_id = ka.organization_id
            JOIN source_acl_snapshots ingestion_sas
              ON ingestion_sas.id = ka.source_acl_snapshot_id
             AND ingestion_sas.organization_id = ka.organization_id
            LEFT JOIN source_acl_heads sah
              ON sah.organization_id = ka.organization_id
             AND sah.source_system = rso.source_system
             AND sah.source_connection_key = rso.source_connection_key
             AND sah.external_object_id = rso.external_object_id
            LEFT JOIN source_acl_snapshots current_sas
              ON current_sas.id = sah.current_snapshot_id
             AND current_sas.organization_id = ka.organization_id
            WHERE ka.organization_id = :organizationId
              AND ka.id = :assetId
            """;

    Optional<KnowledgeAsset> findByNormalizedRecordId(UUID normalizedRecordId);

    Optional<KnowledgeAsset> findByIdAndOrganizationId(UUID id, UUID organizationId);

    @Query(value = "SELECT " + DETAIL_COLUMNS + AUTHORIZED_FROM + AUTHORIZATION_PREDICATE
            + " AND ka.id = :assetId", nativeQuery = true)
    Optional<KnowledgeAssetDetailRow> findPermittedById(
            @Param("assetId") UUID assetId,
            @Param("organizationId") UUID organizationId,
            @Param("actorUserKey") String actorUserKey,
            @Param("actorDepartmentKey") String actorDepartmentKey,
            @Param("actorOrganizationKey") String actorOrganizationKey,
            @Param("actorDepartmentId") UUID actorDepartmentId,
            @Param("knowledgeRole") String knowledgeRole,
            @Param("evaluatedAt") Instant evaluatedAt);

    @Query(value = "SELECT " + SEARCH_COLUMNS + AUTHORIZED_FROM + AUTHORIZATION_PREDICATE + """
            AND (
                :queryPattern IS NULL
                OR lower(ka.title) LIKE :queryPattern ESCAPE '\\'
                OR lower(ka.content) LIKE :queryPattern ESCAPE '\\'
            )
            ORDER BY ka.updated_at DESC, ka.id
            LIMIT :resultLimit
            """, nativeQuery = true)
    List<KnowledgeAssetSearchRow> searchPermitted(
            @Param("organizationId") UUID organizationId,
            @Param("actorUserKey") String actorUserKey,
            @Param("actorDepartmentKey") String actorDepartmentKey,
            @Param("actorOrganizationKey") String actorOrganizationKey,
            @Param("actorDepartmentId") UUID actorDepartmentId,
            @Param("knowledgeRole") String knowledgeRole,
            @Param("evaluatedAt") Instant evaluatedAt,
            @Param("queryPattern") String queryPattern,
            @Param("resultLimit") int resultLimit);

    @Query(value = "SELECT " + SECURITY_COLUMNS + SECURITY_FROM, nativeQuery = true)
    Optional<KnowledgeAssetSecurityRow> findSecurityById(
            @Param("assetId") UUID assetId,
            @Param("organizationId") UUID organizationId);
}
