package com.orgmemory.core.knowledge;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class SecureKnowledgeRetrievalStore {

    private static final String PRINCIPAL_MATCH = """
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
                OR (
                    sae.principal_type = 'SOURCE_USER'
                    AND EXISTS (
                        SELECT 1
                        FROM source_principal_mappings spm
                        WHERE spm.organization_id = sae.organization_id
                          AND spm.source_principal_id = sae.principal_key::uuid
                          AND spm.app_user_id = :actorUserId
                          AND spm.status = 'ACTIVE'
                    )
                )
                OR (
                    sae.principal_type = 'SOURCE_GROUP'
                    AND EXISTS (
                        SELECT 1
                        FROM source_acl_group_members sagm
                        JOIN source_principal_mappings spm
                          ON spm.organization_id = sagm.organization_id
                         AND spm.source_principal_id = sagm.member_principal_id
                         AND spm.app_user_id = :actorUserId
                         AND spm.status = 'ACTIVE'
                        WHERE sagm.organization_id = sae.organization_id
                          AND sagm.source_acl_snapshot_id = sae.source_acl_snapshot_id
                          AND sagm.group_principal_id = sae.principal_key::uuid
                    )
                )
            )
            """;

    private static final String ELIGIBLE_FROM = """
            FROM knowledge_chunks kc
            JOIN knowledge_assets ka
              ON ka.id = kc.knowledge_asset_id
             AND ka.organization_id = kc.organization_id
            JOIN source_objects so
              ON so.id = kc.source_object_id
             AND so.organization_id = kc.organization_id
             AND so.current_revision_id = kc.source_revision_id
            JOIN source_revisions sr
              ON sr.id = kc.source_revision_id
             AND sr.organization_id = kc.organization_id
             AND sr.source_object_id = so.id
             AND sr.knowledge_asset_id = ka.id
            JOIN raw_source_objects rso
              ON rso.id = ka.raw_source_object_id
             AND rso.organization_id = ka.organization_id
            JOIN normalized_records nr
              ON nr.id = ka.normalized_record_id
             AND nr.organization_id = ka.organization_id
            JOIN knowledge_asset_publication_outbox publication
              ON publication.knowledge_asset_id = ka.id
             AND publication.organization_id = ka.organization_id
             AND publication.source_revision_id = sr.id
             AND publication.projection_generation = kc.projection_generation
             AND publication.embedding_profile_id = kc.embedding_profile_id
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
            WHERE kc.organization_id = :organizationId
              AND kc.knowledge_asset_id IN (:authorizedAssetIds)
              AND kc.active
              AND ka.status = 'ACTIVE'
              AND so.status = 'ACTIVE'
              AND sr.status = 'READY'
              AND rso.status = 'NORMALIZED'
              AND nr.status = 'PROMOTED'
              AND publication.status = 'APPLIED'
              AND publication.authorization_model_id = :authorizationModelId
              AND ka.orgmemory_gate = 'ALLOW'
              AND ingestion_sas.capture_status = 'COMPLETE'
              AND current_sas.capture_status = 'COMPLETE'
              AND current_sas.valid_until > :evaluatedAt
              AND NOT EXISTS (
                  SELECT 1
                  FROM source_acl_entries sae
                  WHERE (
                            sae.source_acl_snapshot_id = current_sas.id
                            OR (so.source_type = 'UPLOAD' AND sae.source_acl_snapshot_id = ingestion_sas.id)
                        )
                    AND sae.organization_id = ka.organization_id
                    AND sae.gate = 'DENY'
                    AND
            """ + PRINCIPAL_MATCH + """
              )
              AND (
                  so.source_type <> 'UPLOAD'
                  OR ingestion_sas.default_gate = 'ALLOW'
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
                      AND (:actorExecutive OR ka.department_id = :actorDepartmentId)
                  )
                  OR (
                      ka.classification = 'RESTRICTED'
                      AND ka.declared_access = 'EXECUTIVE_ONLY'
                      AND :actorExecutive
                  )
              )
            """;

    private static final String SELECT_COLUMNS = """
            SELECT kc.id AS chunk_id,
                   kc.organization_id,
                   kc.knowledge_asset_id,
                   kc.source_object_id,
                   kc.source_revision_id,
                   ka.title,
                   kc.content,
                   rso.source_uri,
                   kc.start_page,
                   kc.end_page,
                   kc.heading,
                   ingestion_sas.id AS ingestion_acl_snapshot_id,
                   current_sas.id AS current_acl_snapshot_id,
                   publication.authorization_model_id,
                   kc.embedding_profile_id,
                   kc.projection_generation,
            """;

    private final NamedParameterJdbcTemplate jdbc;

    SecureKnowledgeRetrievalStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    List<SecureRetrievalCandidate> lexical(
            RetrievalScope scope,
            String query,
            int candidateLimit) {
        String sql = SELECT_COLUMNS + """
                   ts_rank_cd(kc.search_vector, websearch_to_tsquery('simple', :query)) AS retrieval_score
            """ + ELIGIBLE_FROM + """
              AND kc.search_vector @@ websearch_to_tsquery('simple', :query)
            ORDER BY retrieval_score DESC, kc.id
            LIMIT :candidateLimit
            """;
        MapSqlParameterSource parameters = parameters(scope)
                .addValue("query", query)
                .addValue("candidateLimit", candidateLimit);
        return jdbc.query(sql, parameters, SecureKnowledgeRetrievalStore::mapCandidate);
    }

    List<SecureRetrievalCandidate> semantic(
            RetrievalScope scope,
            QueryEmbedding embedding,
            int candidateLimit) {
        String sql = SELECT_COLUMNS + """
                   1 - (kc.embedding <=> CAST(:queryEmbedding AS vector)) AS retrieval_score
            """ + ELIGIBLE_FROM + """
              AND kc.embedding_profile_id = :embeddingProfileId
              AND kc.embedding_dimensions = :embeddingDimensions
            ORDER BY kc.embedding <=> CAST(:queryEmbedding AS vector), kc.id
            LIMIT :candidateLimit
            """;
        MapSqlParameterSource parameters = parameters(scope)
                .addValue("queryEmbedding", PgVectorLiteral.from(embedding.vector()))
                .addValue("embeddingProfileId", embedding.profileId())
                .addValue("embeddingDimensions", embedding.dimensions())
                .addValue("candidateLimit", candidateLimit);
        return jdbc.query(sql, parameters, SecureKnowledgeRetrievalStore::mapCandidate);
    }

    List<SecureRetrievalCandidate> recheck(
            RetrievalScope scope,
            Collection<UUID> chunkIds) {
        if (chunkIds.isEmpty()) {
            return List.of();
        }
        String sql = SELECT_COLUMNS + """
                   0::double precision AS retrieval_score
            """ + ELIGIBLE_FROM + """
              AND kc.id IN (:chunkIds)
            ORDER BY kc.id
            """;
        return jdbc.query(
                sql,
                parameters(scope).addValue("chunkIds", chunkIds),
                SecureKnowledgeRetrievalStore::mapCandidate);
    }

    List<UUID> visibleSourceObjectIds(RetrievalScope scope) {
        String sql = """
                SELECT DISTINCT kc.source_object_id
                """ + ELIGIBLE_FROM + """
                ORDER BY kc.source_object_id
                """;
        return jdbc.query(
                sql,
                parameters(scope),
                (result, rowNumber) -> result.getObject("source_object_id", UUID.class));
    }

    private static MapSqlParameterSource parameters(RetrievalScope scope) {
        return new MapSqlParameterSource()
                .addValue("organizationId", scope.organizationId())
                .addValue("authorizedAssetIds", scope.authorizedAssetIds())
                .addValue("authorizationModelId", scope.authorizationModelId())
                .addValue("evaluatedAt", OffsetDateTime.ofInstant(scope.evaluatedAt(), java.time.ZoneOffset.UTC),
                        Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("actorUserKey", scope.actorUserId().toString())
                .addValue("actorUserId", scope.actorUserId(), Types.OTHER)
                .addValue("actorDepartmentKey",
                        scope.actorDepartmentId() == null ? null : scope.actorDepartmentId().toString(),
                        Types.VARCHAR)
                .addValue("actorOrganizationKey", scope.organizationId().toString())
                .addValue("actorDepartmentId", scope.actorDepartmentId(), Types.OTHER)
                .addValue("actorExecutive", scope.actorExecutive());
    }

    private static SecureRetrievalCandidate mapCandidate(ResultSet result, int rowNumber) throws SQLException {
        return new SecureRetrievalCandidate(
                result.getObject("organization_id", UUID.class),
                result.getObject("chunk_id", UUID.class),
                result.getObject("knowledge_asset_id", UUID.class),
                result.getObject("source_object_id", UUID.class),
                result.getObject("source_revision_id", UUID.class),
                result.getString("title"),
                result.getString("content"),
                result.getString("source_uri"),
                nullableInteger(result, "start_page"),
                nullableInteger(result, "end_page"),
                result.getString("heading"),
                result.getDouble("retrieval_score"),
                result.getObject("ingestion_acl_snapshot_id", UUID.class),
                result.getObject("current_acl_snapshot_id", UUID.class),
                result.getString("authorization_model_id"),
                result.getObject("embedding_profile_id", UUID.class),
                result.getLong("projection_generation"));
    }

    private static Integer nullableInteger(ResultSet result, String column) throws SQLException {
        int value = result.getInt(column);
        return result.wasNull() ? null : value;
    }

    record RetrievalScope(
            UUID organizationId,
            UUID actorUserId,
            UUID actorDepartmentId,
            boolean actorExecutive,
            List<UUID> authorizedAssetIds,
            String authorizationModelId,
            Instant evaluatedAt) {

        RetrievalScope {
            authorizedAssetIds = List.copyOf(authorizedAssetIds);
        }
    }
}
