package com.orgmemory.core.knowledge;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ClaimedGraphIndex(
        UUID jobId,
        UUID organizationId,
        UUID knowledgeAssetId,
        UUID knowledgeSpaceId,
        UUID knowledgeAssetVersionId,
        UUID sourceRevisionId,
        UUID aclSnapshotId,
        long aclGeneration,
        long projectionGeneration,
        String idempotencyKey,
        EmbeddingProfileRef embeddingProfile,
        String language,
        int attempt,
        List<GraphIndexChunk> chunks) {

    public ClaimedGraphIndex {
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(knowledgeAssetId, "knowledgeAssetId");
        Objects.requireNonNull(knowledgeSpaceId, "knowledgeSpaceId");
        Objects.requireNonNull(knowledgeAssetVersionId, "knowledgeAssetVersionId");
        Objects.requireNonNull(sourceRevisionId, "sourceRevisionId");
        Objects.requireNonNull(aclSnapshotId, "aclSnapshotId");
        idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey").strip();
        if (idempotencyKey.isEmpty()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
        Objects.requireNonNull(embeddingProfile, "embeddingProfile");
        if (!organizationId.equals(embeddingProfile.organizationId())) {
            throw new IllegalArgumentException(
                    "embeddingProfile must belong to the claim organization");
        }
        language = language == null || language.isBlank() ? "und" : language.strip();
        chunks = List.copyOf(Objects.requireNonNull(chunks, "chunks"));
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("a graph index claim must contain chunks");
        }
        if (chunks.stream().anyMatch(
                chunk -> chunk.embedding().dimensions() != embeddingProfile.dimensions())) {
            throw new IllegalArgumentException(
                    "every chunk embedding must match the pinned embedding profile");
        }
    }
}
