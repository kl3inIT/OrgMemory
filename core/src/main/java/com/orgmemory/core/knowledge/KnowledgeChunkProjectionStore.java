package com.orgmemory.core.knowledge;

import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class KnowledgeChunkProjectionStore {

    private static final String INSERT_CHUNK_SQL = """
            INSERT INTO knowledge_chunks (
                id, organization_id, source_object_id, source_revision_id,
                knowledge_asset_id, knowledge_asset_version_id,
                chunk_index, content, content_sha256,
                token_count, start_page, end_page, heading,
                source_start_char, source_end_char, source_block_indexes,
                canonical_text_sha256, embedding,
                embedding_profile_id, embedding_dimensions, pipeline_version,
                projection_generation, active, created_at
            ) VALUES (
                :id, :organizationId, :sourceObjectId, :sourceRevisionId,
                :knowledgeAssetId, :knowledgeAssetVersionId,
                :chunkIndex, :content, :contentSha256,
                :tokenCount, :startPage, :endPage, :heading,
                :sourceStartChar, :sourceEndChar, CAST(:sourceBlockIndexes AS integer[]),
                :canonicalTextSha256,
                CAST(:embedding AS vector), :embeddingProfileId,
                :embeddingDimensions, :pipelineVersion,
                :projectionGeneration, false, :createdAt
            )
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public KnowledgeChunkProjectionStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void replace(
            UUID organizationId,
            UUID sourceObjectId,
            UUID sourceRevisionId,
            UUID knowledgeAssetId,
            UUID knowledgeAssetVersionId,
            EmbeddingProfileRef embeddingProfile,
            String pipelineVersion,
            long projectionGeneration,
            List<KnowledgeChunkDraft> chunks) {
        if (embeddingProfile == null || !organizationId.equals(embeddingProfile.organizationId())) {
            throw new IllegalArgumentException("embedding profile must belong to the projection organization");
        }
        jdbc.update(
                """
                DELETE FROM knowledge_chunks
                WHERE organization_id = :organizationId
                  AND source_revision_id = :revisionId
                """,
                new MapSqlParameterSource()
                        .addValue("organizationId", organizationId)
                        .addValue("revisionId", sourceRevisionId));
        Instant now = Instant.now();
        SqlParameterSource[] batch = new SqlParameterSource[chunks.size()];
        int batchIndex = 0;
        for (KnowledgeChunkDraft chunk : chunks) {
            float[] embedding = chunk.embedding();
            if (embedding == null || embedding.length != embeddingProfile.dimensions()) {
                throw new IllegalArgumentException("chunk embedding dimensions do not match the projection");
            }
            batch[batchIndex++] = new MapSqlParameterSource()
                    .addValue("id", UUID.randomUUID())
                    .addValue("organizationId", organizationId)
                    .addValue("sourceObjectId", sourceObjectId)
                    .addValue("sourceRevisionId", sourceRevisionId)
                    .addValue("knowledgeAssetId", knowledgeAssetId)
                    .addValue("knowledgeAssetVersionId", knowledgeAssetVersionId)
                    .addValue("chunkIndex", chunk.index())
                    .addValue("content", chunk.content())
                    .addValue("contentSha256", chunk.contentSha256())
                    .addValue("tokenCount", chunk.tokenCount(), Types.INTEGER)
                    .addValue("startPage", chunk.startPage(), Types.INTEGER)
                    .addValue("endPage", chunk.endPage(), Types.INTEGER)
                    .addValue("heading", chunk.heading(), Types.VARCHAR)
                    .addValue("sourceStartChar", chunk.startChar(), Types.INTEGER)
                    .addValue("sourceEndChar", chunk.endChar(), Types.INTEGER)
                    .addValue("sourceBlockIndexes", pgIntegerArray(chunk.sourceBlockIndexes()))
                    .addValue(
                            "canonicalTextSha256",
                            chunk.canonicalTextSha256(),
                            Types.VARCHAR)
                    .addValue("embedding", PgVectorLiteral.from(embedding))
                    .addValue("embeddingProfileId", embeddingProfile.id())
                    .addValue("embeddingDimensions", embeddingProfile.dimensions())
                    .addValue("pipelineVersion", pipelineVersion)
                    .addValue("projectionGeneration", projectionGeneration)
                    .addValue(
                            "createdAt",
                            OffsetDateTime.ofInstant(now, ZoneOffset.UTC),
                            Types.TIMESTAMP_WITH_TIMEZONE);
        }
        jdbc.batchUpdate(INSERT_CHUNK_SQL, batch);
    }

    private static String pgIntegerArray(List<Integer> values) {
        if (values.isEmpty()) {
            return "{}";
        }
        return values.stream()
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(",", "{", "}"));
    }

    @Transactional
    public int activate(
            UUID organizationId,
            UUID sourceRevisionId,
            UUID knowledgeAssetId,
            UUID knowledgeAssetVersionId,
            long projectionGeneration) {
        return jdbc.update(
                """
                        UPDATE knowledge_chunks
                        SET active = true
                        WHERE organization_id = :organizationId
                          AND source_revision_id = :sourceRevisionId
                          AND knowledge_asset_id = :knowledgeAssetId
                          AND knowledge_asset_version_id = :knowledgeAssetVersionId
                          AND projection_generation = :projectionGeneration
                          AND active = false
                        """,
                new MapSqlParameterSource()
                        .addValue("organizationId", organizationId)
                        .addValue("sourceRevisionId", sourceRevisionId)
                        .addValue("knowledgeAssetId", knowledgeAssetId)
                        .addValue("knowledgeAssetVersionId", knowledgeAssetVersionId)
                        .addValue("projectionGeneration", projectionGeneration));
    }

    @Transactional(readOnly = true)
    public List<GraphIndexChunk> loadActive(
            UUID organizationId,
            UUID sourceRevisionId,
            UUID knowledgeAssetId,
            UUID knowledgeAssetVersionId,
            long projectionGeneration) {
        return jdbc.query(
                """
                SELECT id, chunk_index, content
                FROM knowledge_chunks
                WHERE organization_id = :organizationId
                  AND source_revision_id = :sourceRevisionId
                  AND knowledge_asset_id = :knowledgeAssetId
                  AND knowledge_asset_version_id = :knowledgeAssetVersionId
                  AND projection_generation = :projectionGeneration
                  AND active
                ORDER BY chunk_index, id
                """,
                new MapSqlParameterSource()
                        .addValue("organizationId", organizationId)
                        .addValue("sourceRevisionId", sourceRevisionId)
                        .addValue("knowledgeAssetId", knowledgeAssetId)
                        .addValue("knowledgeAssetVersionId", knowledgeAssetVersionId)
                        .addValue("projectionGeneration", projectionGeneration),
                (resultSet, rowNumber) -> new GraphIndexChunk(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getInt("chunk_index"),
                        resultSet.getString("content")));
    }

}
