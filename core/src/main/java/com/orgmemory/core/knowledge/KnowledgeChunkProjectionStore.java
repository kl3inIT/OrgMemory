package com.orgmemory.core.knowledge;

import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class KnowledgeChunkProjectionStore {

    private final JdbcClient jdbc;

    public KnowledgeChunkProjectionStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void replace(
            UUID organizationId,
            UUID sourceObjectId,
            UUID sourceRevisionId,
            UUID knowledgeAssetId,
            EmbeddingProfileRef embeddingProfile,
            String pipelineVersion,
            long projectionGeneration,
            List<KnowledgeChunkDraft> chunks) {
        if (embeddingProfile == null || !organizationId.equals(embeddingProfile.organizationId())) {
            throw new IllegalArgumentException("embedding profile must belong to the projection organization");
        }
        jdbc.sql("DELETE FROM knowledge_chunks WHERE source_revision_id = :revisionId")
                .param("revisionId", sourceRevisionId)
                .update();
        Instant now = Instant.now();
        for (KnowledgeChunkDraft chunk : chunks) {
            float[] embedding = chunk.embedding();
            if (embedding == null || embedding.length != embeddingProfile.dimensions()) {
                throw new IllegalArgumentException("chunk embedding dimensions do not match the projection");
            }
            jdbc.sql("""
                            INSERT INTO knowledge_chunks (
                                id, organization_id, source_object_id, source_revision_id,
                                knowledge_asset_id, chunk_index, content, content_sha256,
                                token_count, start_page, end_page, heading, embedding,
                                embedding_profile_id, embedding_dimensions, pipeline_version,
                                projection_generation, active, created_at
                            ) VALUES (
                                :id, :organizationId, :sourceObjectId, :sourceRevisionId,
                                :knowledgeAssetId, :chunkIndex, :content, :contentSha256,
                                :tokenCount, :startPage, :endPage, :heading,
                                CAST(:embedding AS vector), :embeddingProfileId,
                                :embeddingDimensions, :pipelineVersion,
                                :projectionGeneration, true, :createdAt
                            )
                            """)
                    .param("id", UUID.randomUUID())
                    .param("organizationId", organizationId)
                    .param("sourceObjectId", sourceObjectId)
                    .param("sourceRevisionId", sourceRevisionId)
                    .param("knowledgeAssetId", knowledgeAssetId)
                    .param("chunkIndex", chunk.index())
                    .param("content", chunk.content())
                    .param("contentSha256", chunk.contentSha256())
                    .param("tokenCount", chunk.tokenCount(), Types.INTEGER)
                    .param("startPage", chunk.startPage(), Types.INTEGER)
                    .param("endPage", chunk.endPage(), Types.INTEGER)
                    .param("heading", chunk.heading(), Types.VARCHAR)
                    .param("embedding", vectorLiteral(embedding))
                    .param("embeddingProfileId", embeddingProfile.id())
                    .param("embeddingDimensions", embeddingProfile.dimensions())
                    .param("pipelineVersion", pipelineVersion)
                    .param("projectionGeneration", projectionGeneration)
                    .param(
                            "createdAt",
                            OffsetDateTime.ofInstant(now, ZoneOffset.UTC),
                            Types.TIMESTAMP_WITH_TIMEZONE)
                    .update();
        }
    }

    private static String vectorLiteral(float[] vector) {
        StringBuilder value = new StringBuilder(vector.length * 12).append('[');
        for (int index = 0; index < vector.length; index++) {
            if (index > 0) {
                value.append(',');
            }
            value.append(vector[index]);
        }
        return value.append(']').toString();
    }
}
