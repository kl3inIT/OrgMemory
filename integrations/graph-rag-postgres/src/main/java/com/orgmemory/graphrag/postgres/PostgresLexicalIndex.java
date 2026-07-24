package com.orgmemory.graphrag.postgres;

import com.orgmemory.graphrag.authorization.AuthorizedEvidenceScope;
import com.orgmemory.graphrag.storage.LexicalIndex;
import com.orgmemory.graphrag.storage.ProjectionBatch;
import com.orgmemory.graphrag.storage.ProjectionKind;
import com.orgmemory.graphrag.storage.ProjectionSnapshot;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

public final class PostgresLexicalIndex implements LexicalIndex {

    private static final String COPY_PREDECESSOR = """
            INSERT INTO projection_lexical_documents (
                batch_id, document_id, organization_id, knowledge_asset_id,
                source_revision_id, chunk_id, acl_snapshot_id, acl_generation,
                content, fields)
            SELECT
                :batchId, document_id, organization_id, knowledge_asset_id,
                source_revision_id, chunk_id, acl_snapshot_id, acl_generation,
                content, fields
            FROM projection_lexical_documents
            WHERE batch_id = :predecessorBatchId
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final PostgresProjectionSupport support;

    public PostgresLexicalIndex(
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
            Collection<LexicalDocument> documents) {
        List<LexicalDocument> immutableDocuments =
                List.copyOf(Objects.requireNonNull(documents, "documents"));
        immutableDocuments.forEach(document ->
                PostgresProjectionSupport.requireSameOrganization(
                        batch, document.evidence()));
        support.stage(batch, ProjectionKind.LEXICAL, COPY_PREDECESSOR, () -> {
            for (LexicalDocument document : immutableDocuments) {
                jdbc.update(
                        """
                        INSERT INTO projection_lexical_documents (
                            batch_id, document_id, organization_id, knowledge_asset_id,
                            source_revision_id, chunk_id, acl_snapshot_id, acl_generation,
                            content, fields)
                        VALUES (
                            :batchId, :documentId, :organizationId, :knowledgeAssetId,
                            :sourceRevisionId, :chunkId, :aclSnapshotId, :aclGeneration,
                            :content, :fields)
                        ON CONFLICT (batch_id, document_id)
                        DO UPDATE SET
                            organization_id = EXCLUDED.organization_id,
                            knowledge_asset_id = EXCLUDED.knowledge_asset_id,
                            source_revision_id = EXCLUDED.source_revision_id,
                            chunk_id = EXCLUDED.chunk_id,
                            acl_snapshot_id = EXCLUDED.acl_snapshot_id,
                            acl_generation = EXCLUDED.acl_generation,
                            content = EXCLUDED.content,
                            fields = EXCLUDED.fields
                        """,
                        parameters(batch, document));
            }
        });
    }

    @Override
    public void stageDelete(
            ProjectionBatch batch,
            Collection<String> ids) {
        PostgresProjectionSupport.requireIds(ids);
        List<String> immutableIds = List.copyOf(ids);
        support.stage(batch, ProjectionKind.LEXICAL, COPY_PREDECESSOR, () -> {
            if (!immutableIds.isEmpty()) {
                jdbc.update(
                        """
                        DELETE FROM projection_lexical_documents
                        WHERE batch_id = :batchId AND document_id IN (:ids)
                        """,
                        Map.of("batchId", batch.id(), "ids", immutableIds));
            }
        });
    }

    @Override
    public SearchPage search(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            SearchRequest request) {
        Objects.requireNonNull(request, "request");
        support.requireReadable(scope, snapshot, ProjectionKind.LEXICAL);
        if (PostgresProjectionSupport.noAuthorizedAssets(scope)) {
            return new SearchPage(List.of(), null);
        }
        Cursor cursor = decodeCursor(request.cursor());
        MapSqlParameterSource parameters =
                PostgresProjectionSupport.visibilityParameters(scope, snapshot)
                        .addValue("query", request.query())
                        .addValue("minimumScore", request.minimumScore())
                        .addValue("limit", request.limit() + 1)
                        .addValue("cursorScore", cursor == null ? null : cursor.score())
                        .addValue("cursorId", cursor == null ? null : cursor.id());
        List<SearchHit> results = jdbc.query(
                """
                WITH ranked AS (
                    SELECT
                        document.*,
                        ts_rank_cd(
                            document.search_vector,
                            plainto_tsquery('simple', :query))::float8 AS score
                    FROM projection_lexical_documents document
                    WHERE document.batch_id = :batchId
                      AND document.organization_id = :organizationId
                      AND document.knowledge_asset_id IN (:authorizedAssetIds)
                      AND document.search_vector
                          @@ plainto_tsquery('simple', :query)
                )
                SELECT *
                FROM ranked
                WHERE score >= :minimumScore
                  AND (
                      CAST(:cursorScore AS double precision) IS NULL
                      OR score < CAST(:cursorScore AS double precision)
                      OR (
                          score = CAST(:cursorScore AS double precision)
                          AND document_id > CAST(:cursorId AS text)
                      )
                  )
                ORDER BY score DESC, document_id
                LIMIT :limit
                """,
                parameters,
                (resultSet, rowNumber) -> searchHit(resultSet));
        if (results.size() <= request.limit()) {
            return new SearchPage(results, null);
        }
        List<SearchHit> page = List.copyOf(results.subList(0, request.limit()));
        SearchHit last = page.getLast();
        return new SearchPage(page, encodeCursor(last.score(), last.id()));
    }

    @Override
    public void discard(ProjectionBatch batch) {
        support.discard(
                batch,
                ProjectionKind.LEXICAL,
                List.of("""
                        DELETE FROM projection_lexical_documents
                        WHERE batch_id = :batchId
                        """));
    }

    private static MapSqlParameterSource parameters(
            ProjectionBatch batch,
            LexicalDocument document) {
        return PostgresProjectionSupport.evidenceParameters(document.evidence())
                .addValue("batchId", batch.id())
                .addValue("documentId", document.id())
                .addValue("content", document.content())
                .addValue(
                        "fields",
                        PostgresProjectionCodec.searchableValues(document.fields()));
    }

    private static SearchHit searchHit(ResultSet resultSet)
            throws SQLException {
        double score = resultSet.getDouble("score");
        return new SearchHit(
                resultSet.getString("document_id"),
                PostgresProjectionSupport.evidence(resultSet),
                score,
                Map.of("postgres_fts", score));
    }

    private static String encodeCursor(double score, String id) {
        String value = Double.toHexString(score) + "\n" + id;
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static Cursor decodeCursor(String encoded) {
        if (encoded == null) {
            return null;
        }
        try {
            String value = new String(
                    Base64.getUrlDecoder().decode(encoded),
                    StandardCharsets.UTF_8);
            int separator = value.indexOf('\n');
            if (separator < 1 || separator == value.length() - 1) {
                throw new IllegalArgumentException("invalid cursor");
            }
            return new Cursor(
                    Double.valueOf(value.substring(0, separator)),
                    value.substring(separator + 1));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("invalid lexical cursor", exception);
        }
    }

    private record Cursor(double score, String id) {}
}
