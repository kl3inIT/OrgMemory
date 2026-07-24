package com.orgmemory.graphrag.postgres;

import com.orgmemory.graphrag.authorization.AuthorizedEvidenceScope;
import com.orgmemory.graphrag.storage.ContentStore;
import com.orgmemory.graphrag.storage.ProjectionBatch;
import com.orgmemory.graphrag.storage.ProjectionKind;
import com.orgmemory.graphrag.storage.ProjectionSnapshot;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

public final class PostgresContentStore implements ContentStore {

    private static final String COPY_PREDECESSOR = """
            INSERT INTO projection_content_records (
                batch_id, record_id, organization_id, knowledge_asset_id,
                source_revision_id, chunk_id, acl_snapshot_id, acl_generation,
                content_kind, content, token_count, metadata)
            SELECT
                :batchId, record_id, organization_id, knowledge_asset_id,
                source_revision_id, chunk_id, acl_snapshot_id, acl_generation,
                content_kind, content, token_count, metadata
            FROM projection_content_records
            WHERE batch_id = :predecessorBatchId
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final PostgresProjectionSupport support;

    public PostgresContentStore(
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
            Collection<ContentRecord> records) {
        List<ContentRecord> immutableRecords =
                List.copyOf(Objects.requireNonNull(records, "records"));
        immutableRecords.forEach(record ->
                PostgresProjectionSupport.requireSameOrganization(
                        batch, record.evidence()));
        support.stage(batch, ProjectionKind.CONTENT, COPY_PREDECESSOR, () -> {
            for (ContentRecord record : immutableRecords) {
                jdbc.update(
                        """
                        INSERT INTO projection_content_records (
                            batch_id, record_id, organization_id, knowledge_asset_id,
                            source_revision_id, chunk_id, acl_snapshot_id, acl_generation,
                            content_kind, content, token_count, metadata)
                        VALUES (
                            :batchId, :recordId, :organizationId, :knowledgeAssetId,
                            :sourceRevisionId, :chunkId, :aclSnapshotId, :aclGeneration,
                            :contentKind, :content, :tokenCount, :metadata)
                        ON CONFLICT (batch_id, record_id)
                        DO UPDATE SET
                            organization_id = EXCLUDED.organization_id,
                            knowledge_asset_id = EXCLUDED.knowledge_asset_id,
                            source_revision_id = EXCLUDED.source_revision_id,
                            chunk_id = EXCLUDED.chunk_id,
                            acl_snapshot_id = EXCLUDED.acl_snapshot_id,
                            acl_generation = EXCLUDED.acl_generation,
                            content_kind = EXCLUDED.content_kind,
                            content = EXCLUDED.content,
                            token_count = EXCLUDED.token_count,
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
        support.stage(batch, ProjectionKind.CONTENT, COPY_PREDECESSOR, () -> {
            if (!immutableIds.isEmpty()) {
                jdbc.update(
                        """
                        DELETE FROM projection_content_records
                        WHERE batch_id = :batchId AND record_id IN (:ids)
                        """,
                        Map.of("batchId", batch.id(), "ids", immutableIds));
            }
        });
    }

    @Override
    public Optional<ContentRecord> get(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            String id) {
        return get(scope, snapshot, List.of(Objects.requireNonNull(id, "id")))
                .stream()
                .findFirst();
    }

    @Override
    public List<ContentRecord> get(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<String> ids) {
        PostgresProjectionSupport.requireIds(ids);
        support.requireReadable(scope, snapshot, ProjectionKind.CONTENT);
        if (ids.isEmpty() || PostgresProjectionSupport.noAuthorizedAssets(scope)) {
            return List.of();
        }
        MapSqlParameterSource parameters =
                PostgresProjectionSupport.visibilityParameters(scope, snapshot)
                        .addValue("ids", ids);
        return jdbc.query(
                """
                SELECT *
                FROM projection_content_records
                WHERE batch_id = :batchId
                  AND organization_id = :organizationId
                  AND knowledge_asset_id IN (:authorizedAssetIds)
                  AND record_id IN (:ids)
                ORDER BY record_id
                """,
                parameters,
                (resultSet, rowNumber) -> contentRecord(resultSet));
    }

    @Override
    public void discard(ProjectionBatch batch) {
        support.discard(
                batch,
                ProjectionKind.CONTENT,
                List.of("""
                        DELETE FROM projection_content_records
                        WHERE batch_id = :batchId
                        """));
    }

    private static MapSqlParameterSource parameters(
            ProjectionBatch batch,
            ContentRecord record) {
        return PostgresProjectionSupport.evidenceParameters(record.evidence())
                .addValue("batchId", batch.id())
                .addValue("recordId", record.id())
                .addValue("contentKind", record.kind().name())
                .addValue("content", record.content())
                .addValue("tokenCount", record.tokenCount())
                .addValue(
                        "metadata",
                        PostgresProjectionCodec.encodeMap(record.metadata()));
    }

    private static ContentRecord contentRecord(ResultSet resultSet)
            throws SQLException {
        return new ContentRecord(
                resultSet.getString("record_id"),
                PostgresProjectionSupport.evidence(resultSet),
                ContentKind.valueOf(resultSet.getString("content_kind")),
                resultSet.getString("content"),
                resultSet.getInt("token_count"),
                PostgresProjectionCodec.decodeMap(resultSet.getString("metadata")));
    }
}
