package com.orgmemory.core.knowledge;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ClaimedGraphIndex(
        UUID jobId,
        UUID organizationId,
        UUID knowledgeAssetId,
        UUID knowledgeAssetVersionId,
        UUID sourceRevisionId,
        UUID aclSnapshotId,
        long aclGeneration,
        long projectionGeneration,
        EmbeddingProfileRef embeddingProfile,
        String language,
        int attempt,
        List<GraphIndexChunk> chunks) {

    public ClaimedGraphIndex {
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(knowledgeAssetId, "knowledgeAssetId");
        Objects.requireNonNull(knowledgeAssetVersionId, "knowledgeAssetVersionId");
        Objects.requireNonNull(sourceRevisionId, "sourceRevisionId");
        Objects.requireNonNull(aclSnapshotId, "aclSnapshotId");
        Objects.requireNonNull(embeddingProfile, "embeddingProfile");
        language = language == null || language.isBlank() ? "und" : language.strip();
        chunks = List.copyOf(Objects.requireNonNull(chunks, "chunks"));
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("a graph index claim must contain chunks");
        }
    }
}
