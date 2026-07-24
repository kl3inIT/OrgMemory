package com.orgmemory.graphrag.postgres;

import com.orgmemory.graphrag.authorization.AuthorizedEvidenceScope;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/**
 * Canonical Source ACL prefilter shared by every PostgreSQL graph read path.
 *
 * <p>OpenFGA resolves the candidate Knowledge Asset set. This SQL applies the
 * canonical publication, classification, current ACL-head, sealed-snapshot,
 * principal and source-authority gates before an identity, score, aggregate,
 * curation overlay or export can observe a contribution.
 */
final class PostgresAuthorizedGraphSql {

    private static final String PRINCIPAL_MATCH = """
            (
                (
                    entry.principal_type = 'ORGMEMORY_USER'
                    AND entry.principal_key = :actorUserKey
                )
                OR (
                    entry.principal_type = 'ORGMEMORY_DEPARTMENT'
                    AND :actorDepartmentKey IS NOT NULL
                    AND entry.principal_key = :actorDepartmentKey
                )
                OR (
                    entry.principal_type = 'ORGMEMORY_ORGANIZATION'
                    AND entry.principal_key = :actorOrganizationKey
                )
                OR (
                    entry.principal_type = 'SOURCE_USER'
                    AND EXISTS (
                        SELECT 1
                        FROM source_principal_mappings mapping
                        WHERE mapping.organization_id =
                                entry.organization_id
                          AND mapping.source_principal_id =
                                entry.principal_key::uuid
                          AND mapping.app_user_id = :actorUserId
                          AND mapping.status = 'ACTIVE'
                    )
                )
                OR (
                    entry.principal_type = 'SOURCE_GROUP'
                    AND EXISTS (
                        SELECT 1
                        FROM source_acl_group_members membership
                        JOIN source_principal_mappings mapping
                          ON mapping.organization_id =
                                membership.organization_id
                         AND mapping.source_principal_id =
                                membership.member_principal_id
                         AND mapping.app_user_id = :actorUserId
                         AND mapping.status = 'ACTIVE'
                        WHERE membership.organization_id =
                                entry.organization_id
                          AND membership.source_acl_snapshot_id =
                                entry.source_acl_snapshot_id
                          AND membership.group_principal_id =
                                entry.principal_key::uuid
                    )
                )
            )
            """;

    static final String VISIBLE_KNOWLEDGE_CHUNKS = """
            visible_knowledge_chunks AS (
                SELECT chunk.*,
                       ingestion_snapshot.id AS source_acl_snapshot_id,
                       ingestion_snapshot.acl_generation
                            AS ingestion_acl_generation
                FROM knowledge_chunks chunk
                JOIN knowledge_assets asset
                  ON asset.id = chunk.knowledge_asset_id
                 AND asset.organization_id = chunk.organization_id
                 AND asset.current_version_id =
                        chunk.knowledge_asset_version_id
                 AND asset.archived_at IS NULL
                JOIN knowledge_asset_versions asset_version
                  ON asset_version.id = chunk.knowledge_asset_version_id
                 AND asset_version.organization_id =
                        chunk.organization_id
                 AND asset_version.knowledge_asset_id = asset.id
                 AND asset_version.status = 'ACTIVE'
                JOIN source_objects source_object
                  ON source_object.id = chunk.source_object_id
                 AND source_object.organization_id =
                        chunk.organization_id
                 AND source_object.current_revision_id =
                        chunk.source_revision_id
                 AND source_object.status = 'ACTIVE'
                JOIN source_revisions revision
                  ON revision.id = chunk.source_revision_id
                 AND revision.organization_id = chunk.organization_id
                 AND revision.source_object_id = source_object.id
                 AND revision.knowledge_asset_id = asset.id
                 AND revision.knowledge_asset_version_id =
                        asset_version.id
                 AND revision.status = 'READY'
                JOIN raw_source_objects raw_source
                  ON raw_source.id = asset_version.raw_source_object_id
                 AND raw_source.organization_id = asset.organization_id
                 AND raw_source.status = 'NORMALIZED'
                JOIN normalized_records normalized
                  ON normalized.id = asset_version.normalized_record_id
                 AND normalized.organization_id = asset.organization_id
                 AND normalized.status = 'PROMOTED'
                JOIN knowledge_asset_publication_outbox publication
                  ON publication.knowledge_asset_id = asset.id
                 AND publication.knowledge_asset_version_id =
                        asset_version.id
                 AND publication.organization_id = asset.organization_id
                 AND publication.source_revision_id = revision.id
                 AND publication.projection_generation =
                        chunk.projection_generation
                 AND publication.embedding_profile_id =
                        chunk.embedding_profile_id
                 AND publication.status = 'APPLIED'
                 AND publication.authorization_model_id =
                        :authorizationModelId
                JOIN source_acl_snapshots ingestion_snapshot
                  ON ingestion_snapshot.id =
                        asset_version.source_acl_snapshot_id
                 AND ingestion_snapshot.organization_id =
                        asset.organization_id
                 AND ingestion_snapshot.capture_status = 'COMPLETE'
                JOIN source_acl_snapshot_seals ingestion_seal
                  ON ingestion_seal.source_acl_snapshot_id =
                        ingestion_snapshot.id
                 AND ingestion_seal.organization_id =
                        asset.organization_id
                JOIN source_acl_heads acl_head
                  ON acl_head.organization_id = asset.organization_id
                 AND acl_head.source_system = raw_source.source_system
                 AND acl_head.source_connection_key =
                        raw_source.source_connection_key
                 AND acl_head.external_object_id =
                        raw_source.external_object_id
                JOIN source_acl_snapshots current_snapshot
                  ON current_snapshot.id = acl_head.current_snapshot_id
                 AND current_snapshot.organization_id =
                        asset.organization_id
                 AND current_snapshot.capture_status = 'COMPLETE'
                 AND current_snapshot.valid_until > :evaluatedAt
                JOIN source_acl_snapshot_seals current_seal
                  ON current_seal.source_acl_snapshot_id =
                        current_snapshot.id
                 AND current_seal.organization_id =
                        asset.organization_id
                WHERE chunk.organization_id = :organizationId
                  AND chunk.knowledge_asset_id IN (:authorizedAssetIds)
                  AND chunk.active
                  AND asset_version.orgmemory_gate = 'ALLOW'
                  AND NOT EXISTS (
                      SELECT 1
                      FROM source_acl_entries entry
                      WHERE (
                                entry.source_acl_snapshot_id =
                                    current_snapshot.id
                                OR (
                                    source_object.acl_authority =
                                        'ORGMEMORY'
                                    AND entry.source_acl_snapshot_id =
                                        ingestion_snapshot.id
                                )
                            )
                        AND entry.organization_id = asset.organization_id
                        AND entry.gate = 'DENY'
                        AND
            """ + PRINCIPAL_MATCH + """
                  )
                  AND (
                      source_object.acl_authority = 'SOURCE'
                      OR ingestion_snapshot.default_gate = 'ALLOW'
                      OR EXISTS (
                          SELECT 1
                          FROM source_acl_entries entry
                          WHERE entry.source_acl_snapshot_id =
                                    ingestion_snapshot.id
                            AND entry.organization_id =
                                    asset.organization_id
                            AND entry.gate = 'ALLOW'
                            AND
            """ + PRINCIPAL_MATCH + """
                      )
                  )
                  AND (
                      current_snapshot.default_gate = 'ALLOW'
                      OR EXISTS (
                          SELECT 1
                          FROM source_acl_entries entry
                          WHERE entry.source_acl_snapshot_id =
                                    current_snapshot.id
                            AND entry.organization_id =
                                    asset.organization_id
                            AND entry.gate = 'ALLOW'
                            AND
            """ + PRINCIPAL_MATCH + """
                      )
                  )
                  AND (
                      (
                          asset_version.classification = 'PUBLIC'
                          AND asset_version.declared_access = 'ALL'
                      )
                      OR (
                          asset_version.classification = 'INTERNAL'
                          AND asset_version.declared_access =
                                'ALL_EMPLOYEES'
                      )
                      OR (
                          asset_version.classification = 'CONFIDENTIAL'
                          AND asset_version.declared_access =
                                'OWN_DEPARTMENT'
                          AND :actorDepartmentId IS NOT NULL
                          AND (
                              :actorExecutive
                              OR asset_version.department_id =
                                    :actorDepartmentId
                          )
                      )
                      OR (
                          asset_version.classification = 'RESTRICTED'
                          AND asset_version.declared_access =
                                'EXECUTIVE_ONLY'
                          AND :actorExecutive
                      )
                  )
            )
            """;

    static final String VISIBLE_ENTITY_CONTRIBUTIONS = """
            visible_entity_contributions AS (
                SELECT contribution.*
                FROM graph_entity_contributions contribution
                JOIN graph_projection_heads head
                  ON head.organization_id = contribution.organization_id
                 AND head.source_revision_id =
                        contribution.source_revision_id
                 AND head.knowledge_asset_id =
                        contribution.knowledge_asset_id
                 AND head.projection_generation =
                        contribution.projection_generation
                JOIN visible_knowledge_chunks chunk
                  ON chunk.organization_id = contribution.organization_id
                 AND chunk.knowledge_asset_id =
                        contribution.knowledge_asset_id
                 AND chunk.source_revision_id =
                        contribution.source_revision_id
                 AND chunk.id = contribution.chunk_id
                 AND chunk.source_acl_snapshot_id =
                        contribution.acl_snapshot_id
                 AND chunk.ingestion_acl_generation =
                        contribution.acl_generation
                 AND chunk.projection_generation =
                        contribution.projection_generation
                WHERE contribution.organization_id = :organizationId
                  AND contribution.knowledge_asset_id
                        IN (:authorizedAssetIds)
            )
            """;

    static final String VISIBLE_RELATION_CONTRIBUTIONS = """
            visible_relation_contributions AS (
                SELECT contribution.*
                FROM graph_relation_contributions contribution
                JOIN graph_projection_heads head
                  ON head.organization_id = contribution.organization_id
                 AND head.source_revision_id =
                        contribution.source_revision_id
                 AND head.knowledge_asset_id =
                        contribution.knowledge_asset_id
                 AND head.projection_generation =
                        contribution.projection_generation
                JOIN visible_knowledge_chunks chunk
                  ON chunk.organization_id = contribution.organization_id
                 AND chunk.knowledge_asset_id =
                        contribution.knowledge_asset_id
                 AND chunk.source_revision_id =
                        contribution.source_revision_id
                 AND chunk.id = contribution.chunk_id
                 AND chunk.source_acl_snapshot_id =
                        contribution.acl_snapshot_id
                 AND chunk.ingestion_acl_generation =
                        contribution.acl_generation
                 AND chunk.projection_generation =
                        contribution.projection_generation
                JOIN graph_relations relation
                  ON relation.organization_id =
                        contribution.organization_id
                 AND relation.id = contribution.relation_id
                WHERE contribution.organization_id = :organizationId
                  AND contribution.knowledge_asset_id
                        IN (:authorizedAssetIds)
                  AND EXISTS (
                      SELECT 1
                      FROM visible_entity_contributions source_evidence
                      WHERE source_evidence.entity_id =
                            relation.source_entity_id
                  )
                  AND EXISTS (
                      SELECT 1
                      FROM visible_entity_contributions target_evidence
                      WHERE target_evidence.entity_id =
                            relation.target_entity_id
                  )
            )
            """;

    private PostgresAuthorizedGraphSql() {
    }

    static MapSqlParameterSource scopeParameters(
            AuthorizedEvidenceScope scope) {
        Objects.requireNonNull(scope, "scope");
        return new MapSqlParameterSource()
                .addValue("organizationId", scope.organizationId())
                .addValue("authorizedAssetIds", scope.authorizedAssetIds())
                .addValue(
                        "authorizationModelId",
                        scope.authorizationModelId())
                .addValue(
                        "evaluatedAt",
                        OffsetDateTime.ofInstant(
                                scope.evaluatedAt(), ZoneOffset.UTC),
                        Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("actorUserKey", scope.actorUserId().toString())
                .addValue("actorUserId", scope.actorUserId(), Types.OTHER)
                .addValue(
                        "actorDepartmentKey",
                        scope.actorDepartmentId() == null
                                ? null
                                : scope.actorDepartmentId().toString(),
                        Types.VARCHAR)
                .addValue(
                        "actorOrganizationKey",
                        scope.organizationId().toString())
                .addValue(
                        "actorDepartmentId",
                        scope.actorDepartmentId(),
                        Types.OTHER)
                .addValue("actorExecutive", scope.actorExecutive());
    }
}
