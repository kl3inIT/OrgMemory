package com.orgmemory.core.assistant;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Worker-facing lifecycle boundary for the actor-private file projection. */
@Service
public class AssistantFileProcessingService {

    private final AssistantFileRepository files;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    AssistantFileProcessingService(
            AssistantFileRepository files,
            NamedParameterJdbcTemplate jdbc,
            Clock clock) {
        this.files = files;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public Optional<AssistantFileProcessingClaim> claimNext(
            String workerId,
            Duration lease,
            AssistantFileProcessingProfile requestedProfile) {
        Instant now = clock.instant();
        List<AssistantFile> candidates = files.claimable(now, PageRequest.of(0, 1));
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        AssistantFile file = candidates.getFirst();
        if (!file.claim(workerId, lease, now, requestedProfile)) {
            return Optional.empty();
        }
        return Optional.of(new AssistantFileProcessingClaim(
                file.getId(),
                file.organizationId(),
                file.actorUserId(),
                file.fileName(),
                file.mediaType(),
                file.contentLength(),
                file.contentSha256(),
                file.objectKey(),
                file.processingGeneration(),
                new AssistantFileProcessingProfile(
                        file.requestedProfileCanonical(), file.requestedProfileSha256()),
                file.resolvedProfileSha256() == null
                        ? Optional.empty()
                        : Optional.of(new AssistantFileProcessingProfile(
                                file.resolvedProfileCanonical(), file.resolvedProfileSha256()))));
    }

    @Transactional
    public void bindResolvedProfile(
            UUID fileId,
            String workerId,
            AssistantFileProcessingProfile resolvedProfile) {
        requireForUpdate(fileId).bindResolvedProfile(workerId, resolvedProfile);
    }

    @Transactional
    public void complete(
            UUID fileId,
            String workerId,
            AssistantFileProcessingProfile resolvedProfile,
            UUID embeddingProfileId,
            int embeddingDimensions,
            List<AssistantFileChunkDraft> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            throw new IllegalArgumentException("processed file requires chunks");
        }
        AssistantFile file = requireForUpdate(fileId);
        file.requireClaim(workerId);
        jdbc.update(
                "delete from assistant_file_chunks where assistant_file_id = :fileId and processing_generation = :generation",
                new MapSqlParameterSource()
                        .addValue("fileId", fileId)
                        .addValue("generation", file.processingGeneration()));
        for (int index = 0; index < chunks.size(); index++) {
            AssistantFileChunkDraft chunk = chunks.get(index);
            if (chunk.embedding().length != embeddingDimensions) {
                throw new IllegalArgumentException("chunk embedding dimension mismatch");
            }
            jdbc.update("""
                    insert into assistant_file_chunks (
                        id, assistant_file_id, organization_id, actor_user_id,
                        processing_generation, chunk_index, content, heading,
                        start_page, end_page, token_count, source_start_char,
                        source_end_char, source_block_indexes, canonical_text_sha256,
                        embedding, embedding_dimensions, embedding_profile_id,
                        created_at, updated_at, version
                    ) values (
                        :id, :fileId, :organizationId, :actorUserId,
                        :generation, :chunkIndex, :content, :heading,
                        :startPage, :endPage, :tokenCount, :sourceStartChar,
                        :sourceEndChar, :sourceBlockIndexes, :canonicalTextSha256,
                        cast(:embedding as vector), :embeddingDimensions, :embeddingProfileId,
                        :now, :now, 0
                    )
                    """,
                    new MapSqlParameterSource()
                            .addValue("id", UUID.randomUUID())
                            .addValue("fileId", fileId)
                            .addValue("organizationId", file.organizationId())
                            .addValue("actorUserId", file.actorUserId())
                            .addValue("generation", file.processingGeneration())
                            .addValue("chunkIndex", index)
                            .addValue("content", chunk.content())
                            .addValue("heading", chunk.heading())
                            .addValue("startPage", chunk.startPage())
                            .addValue("endPage", chunk.endPage())
                            .addValue("tokenCount", chunk.tokenCount())
                            .addValue("sourceStartChar", chunk.sourceStartChar())
                            .addValue("sourceEndChar", chunk.sourceEndChar())
                            .addValue("sourceBlockIndexes", chunk.sourceBlockIndexes().toArray(Integer[]::new))
                            .addValue("canonicalTextSha256", chunk.canonicalTextSha256())
                            .addValue("embedding", vectorLiteral(chunk.embedding()))
                            .addValue("embeddingDimensions", embeddingDimensions)
                            .addValue("embeddingProfileId", embeddingProfileId)
                            .addValue("now", clock.instant()));
        }
        file.complete(workerId, resolvedProfile, embeddingProfileId, embeddingDimensions);
    }

    @Transactional
    public void fail(UUID fileId, String workerId, String failureCode, boolean retryable) {
        requireForUpdate(fileId).fail(workerId, failureCode, retryable, clock.instant());
    }

    @Transactional
    public Optional<AssistantFileCleanupClaim> claimCleanup() {
        Instant now = clock.instant();
        List<AssistantFile> candidates = files.cleanupCandidate(now, PageRequest.of(0, 1));
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        AssistantFile file = candidates.getFirst();
        boolean expired = file.status() != AssistantFileStatus.DELETING;
        if (expired) {
            file.markExpired(now);
        }
        jdbc.update(
                "delete from assistant_file_chunks where assistant_file_id = :fileId",
                new MapSqlParameterSource("fileId", file.getId()));
        return Optional.of(new AssistantFileCleanupClaim(
                file.getId(), file.objectKey(), expired));
    }

    @Transactional
    public void completeCleanup(UUID fileId) {
        AssistantFile file = requireForUpdate(fileId);
        file.markCleanupComplete(clock.instant());
    }

    private AssistantFile requireForUpdate(UUID fileId) {
        return files.findForUpdate(fileId)
                .orElseThrow(() -> new IllegalStateException("assistant file disappeared"));
    }

    private static String vectorLiteral(float[] vector) {
        StringBuilder value = new StringBuilder(vector.length * 12).append('[');
        for (int index = 0; index < vector.length; index++) {
            if (index > 0) value.append(',');
            value.append(vector[index]);
        }
        return value.append(']').toString();
    }
}
