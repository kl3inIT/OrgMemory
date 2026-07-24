package com.orgmemory.graphrag.postgres;

import com.orgmemory.graphrag.authorization.AuthorizedEvidenceScope;
import com.orgmemory.graphrag.storage.ProjectionBatch;
import com.orgmemory.graphrag.storage.ProjectionKind;
import com.orgmemory.graphrag.storage.ProjectionSnapshot;
import com.orgmemory.graphrag.storage.VectorIndex;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

public final class PostgresVectorIndex implements VectorIndex {

    private static final String COPY_PREDECESSOR = """
            INSERT INTO projection_vector_records (
                batch_id, record_id, subject_id, organization_id,
                knowledge_asset_id, source_revision_id, chunk_id,
                acl_snapshot_id, acl_generation, vector_kind,
                embedding_profile_id, model, dimensions, embedding, metadata)
            SELECT
                :batchId, record_id, subject_id, organization_id,
                knowledge_asset_id, source_revision_id, chunk_id,
                acl_snapshot_id, acl_generation, vector_kind,
                embedding_profile_id, model, dimensions, embedding, metadata
            FROM projection_vector_records
            WHERE batch_id = :predecessorBatchId
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final PostgresProjectionSupport support;

    public PostgresVectorIndex(
            NamedParameterJdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            PostgresProjectionPublicationStore publications) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.support =
                new PostgresProjectionSupport(jdbc, transactionManager, publications);
    }

    @Override
    public void stageUpsert(
            ProjectionBatch batch,
            Collection<VectorRecord> records) {
        List<VectorRecord> immutableRecords =
                List.copyOf(Objects.requireNonNull(records, "records"));
        immutableRecords.forEach(record ->
                PostgresProjectionSupport.requireSameOrganization(
                        batch, record.evidence()));
        support.stage(batch, ProjectionKind.VECTOR, COPY_PREDECESSOR, () -> {
            for (VectorRecord record : immutableRecords) {
                jdbc.update(
                        """
                        INSERT INTO projection_vector_records (
                            batch_id, record_id, subject_id, organization_id,
                            knowledge_asset_id, source_revision_id, chunk_id,
                            acl_snapshot_id, acl_generation, vector_kind,
                            embedding_profile_id, model, dimensions, embedding, metadata)
                        VALUES (
                            :batchId, :recordId, :subjectId, :organizationId,
                            :knowledgeAssetId, :sourceRevisionId, :chunkId,
                            :aclSnapshotId, :aclGeneration, :vectorKind,
                            :embeddingProfileId, :model, :dimensions,
                            CAST(:embedding AS vector), :metadata)
                        ON CONFLICT (batch_id, record_id)
                        DO UPDATE SET
                            subject_id = EXCLUDED.subject_id,
                            organization_id = EXCLUDED.organization_id,
                            knowledge_asset_id = EXCLUDED.knowledge_asset_id,
                            source_revision_id = EXCLUDED.source_revision_id,
                            chunk_id = EXCLUDED.chunk_id,
                            acl_snapshot_id = EXCLUDED.acl_snapshot_id,
                            acl_generation = EXCLUDED.acl_generation,
                            vector_kind = EXCLUDED.vector_kind,
                            embedding_profile_id = EXCLUDED.embedding_profile_id,
                            model = EXCLUDED.model,
                            dimensions = EXCLUDED.dimensions,
                            embedding = EXCLUDED.embedding,
                            metadata = EXCLUDED.metadata
                        """,
                        parameters(batch, record));
            }
        });
    }

    @Override
    public void stageDelete(
            ProjectionBatch batch,
            Collection<String> ids) {
        PostgresProjectionSupport.requireIds(ids);
        List<String> immutableIds = List.copyOf(ids);
        support.stage(batch, ProjectionKind.VECTOR, COPY_PREDECESSOR, () -> {
            if (!immutableIds.isEmpty()) {
                jdbc.update(
                        """
                        DELETE FROM projection_vector_records
                        WHERE batch_id = :batchId AND record_id IN (:ids)
                        """,
                        Map.of("batchId", batch.id(), "ids", immutableIds));
            }
        });
    }

    @Override
    public List<VectorRecord> get(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<String> ids) {
        PostgresProjectionSupport.requireIds(ids);
        support.requireReadable(scope, snapshot, ProjectionKind.VECTOR);
        if (ids.isEmpty() || PostgresProjectionSupport.noAuthorizedAssets(scope)) {
            return List.of();
        }
        return jdbc.query(
                """
                SELECT *, embedding::text AS embedding_text
                FROM projection_vector_records
                WHERE batch_id = :batchId
                  AND organization_id = :organizationId
                  AND knowledge_asset_id IN (:authorizedAssetIds)
                  AND record_id IN (:ids)
                ORDER BY record_id
                """,
                PostgresProjectionSupport.visibilityParameters(scope, snapshot)
                        .addValue("ids", ids),
                (resultSet, rowNumber) -> vectorRecord(resultSet));
    }

    @Override
    public List<VectorHit> search(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            SearchRequest request) {
        Objects.requireNonNull(request, "request");
        support.requireReadable(scope, snapshot, ProjectionKind.VECTOR);
        if (PostgresProjectionSupport.noAuthorizedAssets(scope)) {
            return List.of();
        }
        MapSqlParameterSource parameters =
                PostgresProjectionSupport.visibilityParameters(scope, snapshot)
                        .addValue("embeddingProfileId", request.embeddingProfileId())
                        .addValue(
                                "kinds",
                                request.kinds().stream().map(Enum::name).toList())
                        .addValue(
                                "queryVector",
                                PostgresProjectionCodec.encodeVector(request.queryVector()))
                        .addValue("dimensions", request.dimensions())
                        .addValue("minimumSimilarity", request.minimumSimilarity())
                        .addValue("limit", request.limit());
        String candidatePredicate = "";
        if (!request.candidateIds().isEmpty()) {
            candidatePredicate = "AND subject_id IN (:candidateIds)";
            parameters.addValue("candidateIds", request.candidateIds());
        }
        String typedVector =
                "vector(" + request.dimensions() + ")";
        return jdbc.query(
                """
                WITH ranked AS (
                    SELECT
                        record_id,
                        subject_id,
                        organization_id,
                        knowledge_asset_id,
                        source_revision_id,
                        chunk_id,
                        acl_snapshot_id,
                        acl_generation,
                        vector_kind,
                        1.0 - (
                            embedding::"""
                        + typedVector
                        + " <=> CAST(:queryVector AS "
                        + typedVector
                        + """
                            )
                        ) AS similarity
                    FROM projection_vector_records
                    WHERE batch_id = :batchId
                      AND organization_id = :organizationId
                      AND knowledge_asset_id IN (:authorizedAssetIds)
                      AND embedding_profile_id = :embeddingProfileId
                      AND vector_kind IN (:kinds)
                      AND dimensions = :dimensions
                """
                        + candidatePredicate
                        + """
                )
                SELECT *
                FROM ranked
                WHERE similarity >= :minimumSimilarity
                ORDER BY similarity DESC, record_id
                LIMIT :limit
                """,
                parameters,
                (resultSet, rowNumber) -> vectorHit(resultSet));
    }

    @Override
    public void discard(ProjectionBatch batch) {
        support.discard(
                batch,
                ProjectionKind.VECTOR,
                List.of("""
                        DELETE FROM projection_vector_records
                        WHERE batch_id = :batchId
                        """));
    }

    private static MapSqlParameterSource parameters(
            ProjectionBatch batch,
            VectorRecord record) {
        return PostgresProjectionSupport.evidenceParameters(record.evidence())
                .addValue("batchId", batch.id())
                .addValue("recordId", record.id())
                .addValue("subjectId", record.subjectId())
                .addValue("vectorKind", record.kind().name())
                .addValue("embeddingProfileId", record.embeddingProfileId())
                .addValue("model", record.model())
                .addValue("dimensions", record.vector().dimensions())
                .addValue(
                        "embedding",
                        PostgresProjectionCodec.encodeVector(record.vector()))
                .addValue(
                        "metadata",
                        PostgresProjectionCodec.encodeMap(record.metadata()));
    }

    private static VectorRecord vectorRecord(ResultSet resultSet)
            throws SQLException {
        return new VectorRecord(
                resultSet.getString("record_id"),
                resultSet.getString("subject_id"),
                PostgresProjectionSupport.evidence(resultSet),
                VectorKind.valueOf(resultSet.getString("vector_kind")),
                resultSet.getObject("embedding_profile_id", java.util.UUID.class),
                resultSet.getString("model"),
                PostgresProjectionCodec.decodeVector(
                        resultSet.getString("embedding_text")),
                PostgresProjectionCodec.decodeMap(resultSet.getString("metadata")));
    }

    private static VectorHit vectorHit(ResultSet resultSet)
            throws SQLException {
        return new VectorHit(
                resultSet.getString("record_id"),
                resultSet.getString("subject_id"),
                PostgresProjectionSupport.evidence(resultSet),
                VectorKind.valueOf(resultSet.getString("vector_kind")),
                resultSet.getDouble("similarity"));
    }
}
