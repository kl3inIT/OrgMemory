package com.orgmemory.core.assistant;

import com.orgmemory.core.ai.AiRouteResolver;
import com.orgmemory.core.ai.AiWorkload;
import com.orgmemory.core.organization.CurrentActor;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class JdbcAssistantPrivateFileSearch implements AssistantPrivateFileSearch {

    private static final TokenCountBatchingStrategy BATCHING = new TokenCountBatchingStrategy();
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectProvider<EmbeddingModel> models;
    private final AiRouteResolver aiRoutes;
    private final Clock clock;

    JdbcAssistantPrivateFileSearch(
            NamedParameterJdbcTemplate jdbc,
            ObjectProvider<EmbeddingModel> models,
            AiRouteResolver aiRoutes,
            Clock clock) {
        this.jdbc = jdbc;
        this.models = models;
        this.aiRoutes = aiRoutes;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public AssistantPrivateFileSearchResult search(
            CurrentActor actor,
            String query,
            Integer requestedLimit,
            String requestId,
            AssistantPrivateFileSelection selection) {
        if (selection == null || !selection.restricted()) {
            throw new IllegalArgumentException("private file selection is required");
        }
        String normalized = query == null ? "" : query.strip();
        if (normalized.isEmpty() || normalized.length() > 8_000) {
            throw new IllegalArgumentException("query must contain between 1 and 8000 characters");
        }
        String safeRequestId = requestId == null || requestId.isBlank()
                ? UUID.randomUUID().toString()
                : requestId.strip();
        List<UUID> fileIds = selection.files().stream()
                .map(AssistantPrivateFileSelection.Item::fileId).toList();
        List<PrivateFileGeneration> eligible = jdbc.query("""
                select f.id, f.processing_generation
                from assistant_files f
                where f.id in (:fileIds)
                  and f.organization_id = :organizationId
                  and f.actor_user_id = :actorUserId
                  and f.status = 'READY'
                  and f.expires_at > :now
                """,
                new MapSqlParameterSource()
                        .addValue("fileIds", fileIds)
                        .addValue("organizationId", actor.organizationId())
                        .addValue("actorUserId", actor.userId())
                        .addValue("now", clock.instant()),
                (rs, row) -> new PrivateFileGeneration(
                        rs.getObject("id", UUID.class),
                        rs.getLong("processing_generation")));
        boolean exactSelection = eligible.size() == selection.files().size()
                && selection.files().stream().allMatch(selected -> eligible.stream().anyMatch(
                        file -> file.id().equals(selected.fileId())
                                && file.processingGeneration() == selected.processingGeneration()));
        if (!exactSelection) {
            throw new AssistantUnavailableException(
                    "Selected private file evidence is unavailable",
                    null,
                    "assistant_private_file_unavailable");
        }
        List<PrivateEmbeddingProfile> profiles = jdbc.query("""
                select distinct ep.id, ep.model, ep.dimensions
                from assistant_files f
                join embedding_profiles ep
                  on ep.id = f.embedding_profile_id
                 and ep.organization_id = f.organization_id
                 and ep.dimensions = f.embedding_dimensions
                where f.id in (:fileIds)
                  and f.organization_id = :organizationId
                  and f.actor_user_id = :actorUserId
                  and f.status = 'READY'
                  and f.expires_at > :now
                """,
                new MapSqlParameterSource()
                        .addValue("fileIds", fileIds)
                        .addValue("organizationId", actor.organizationId())
                        .addValue("actorUserId", actor.userId())
                        .addValue("now", clock.instant()),
                (rs, row) -> new PrivateEmbeddingProfile(
                        rs.getObject("id", UUID.class),
                        rs.getString("model"),
                        rs.getInt("dimensions")));
        if (profiles.size() != 1) {
            throw new AssistantUnavailableException(
                    "Selected private files do not share one active embedding profile",
                    null,
                    "assistant_private_profile_mismatch");
        }
        PrivateEmbeddingProfile profile = profiles.getFirst();
        if (!aiRoutes.resolve(AiWorkload.DOCUMENT_EMBEDDING).modelId().equals(profile.model())) {
            throw new AssistantUnavailableException(
                    "Selected private file embeddings are no longer active",
                    null,
                    "assistant_private_profile_inactive");
        }
        EmbeddingModel model = models.getIfAvailable();
        if (model == null) {
            throw new AssistantUnavailableException(
                    "Private file retrieval is unavailable",
                    null,
                    "assistant_private_embedding_unavailable");
        }
        float[] embedding = model.embed(List.of(new Document(normalized)), null, BATCHING).getFirst();
        if (embedding.length != profile.dimensions()) {
            throw new AssistantUnavailableException(
                    "Private file embedding dimensions do not match",
                    null,
                    "assistant_private_embedding_mismatch");
        }
        int limit = Math.max(1, Math.min(requestedLimit == null ? 5 : requestedLimit, 20));
        var parameters = new MapSqlParameterSource()
                .addValue("fileIds", fileIds)
                .addValue("organizationId", actor.organizationId())
                .addValue("actorUserId", actor.userId())
                .addValue("now", clock.instant())
                .addValue("query", normalized)
                .addValue("embedding", vectorLiteral(embedding))
                .addValue("dimensions", embedding.length)
                .addValue("embeddingProfileId", profile.id())
                .addValue("limit", limit);
        List<AssistantCitationEvidence> evidence = jdbc.query("""
                select c.id, c.assistant_file_id, c.processing_generation,
                       f.file_name, c.content, c.heading, c.start_page, c.end_page
                from assistant_file_chunks c
                join assistant_files f
                  on f.id = c.assistant_file_id
                 and f.organization_id = c.organization_id
                 and f.actor_user_id = c.actor_user_id
                 and f.processing_generation = c.processing_generation
                where c.assistant_file_id in (:fileIds)
                  and c.organization_id = :organizationId
                  and c.actor_user_id = :actorUserId
                  and c.embedding_dimensions = :dimensions
                  and c.embedding_profile_id = :embeddingProfileId
                  and f.status = 'READY'
                  and f.expires_at > :now
                order by (
                    0.65 * (1 - (c.embedding <=> cast(:embedding as vector)))
                    + 0.35 * ts_rank_cd(c.search_vector, websearch_to_tsquery('simple', :query))
                ) desc, c.id
                limit :limit
                """, parameters, (rs, row) -> new AssistantCitationEvidence(
                        AssistantCitationEvidence.Kind.ASSISTANT_FILE,
                        rs.getObject("id", UUID.class),
                        rs.getObject("assistant_file_id", UUID.class),
                        rs.getLong("processing_generation"),
                        rs.getString("file_name"),
                        rs.getString("content"),
                        "urn:orgmemory:assistant-file:" + rs.getObject("assistant_file_id", UUID.class),
                        (Integer) rs.getObject("start_page"),
                        (Integer) rs.getObject("end_page"),
                        rs.getString("heading")));
        return new AssistantPrivateFileSearchResult(safeRequestId, evidence);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<AssistantPrivateFileCitation> findCitation(
            CurrentActor actor,
            UUID fileId,
            long processingGeneration,
            UUID chunkId) {
        List<AssistantPrivateFileCitation> found = jdbc.query("""
                select c.id, c.assistant_file_id, c.processing_generation,
                       f.file_name, c.heading, c.start_page, c.end_page, c.content
                from assistant_file_chunks c
                join assistant_files f
                  on f.id = c.assistant_file_id
                 and f.organization_id = c.organization_id
                 and f.actor_user_id = c.actor_user_id
                 and f.processing_generation = c.processing_generation
                where c.id = :chunkId
                  and c.assistant_file_id = :fileId
                  and c.processing_generation = :generation
                  and c.organization_id = :organizationId
                  and c.actor_user_id = :actorUserId
                  and f.status = 'READY'
                  and f.expires_at > :now
                """,
                new MapSqlParameterSource()
                        .addValue("chunkId", chunkId)
                        .addValue("fileId", fileId)
                        .addValue("generation", processingGeneration)
                        .addValue("organizationId", actor.organizationId())
                        .addValue("actorUserId", actor.userId())
                        .addValue("now", clock.instant()),
                (rs, row) -> new AssistantPrivateFileCitation(
                        rs.getObject("id", UUID.class),
                        rs.getObject("assistant_file_id", UUID.class),
                        rs.getLong("processing_generation"),
                        rs.getString("file_name"),
                        rs.getString("heading"),
                        (Integer) rs.getObject("start_page"),
                        (Integer) rs.getObject("end_page"),
                        rs.getString("content")));
        return found.stream().findFirst();
    }

    private static String vectorLiteral(float[] vector) {
        StringBuilder value = new StringBuilder(vector.length * 12).append('[');
        for (int index = 0; index < vector.length; index++) {
            if (index > 0) value.append(',');
            value.append(vector[index]);
        }
        return value.append(']').toString();
    }

    private record PrivateEmbeddingProfile(UUID id, String model, int dimensions) {}

    private record PrivateFileGeneration(UUID id, long processingGeneration) {}
}
