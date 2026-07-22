package com.orgmemory.core.knowledge;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record PublishKnowledgeAssetCommand(
        UUID organizationId,
        UUID sourceObjectId,
        UUID sourceRevisionId,
        UUID normalizedRecordId,
        UUID ownerUserId,
        EmbeddingProfileRef embeddingProfile,
        String pipelineVersion,
        long projectionGeneration,
        List<KnowledgeChunkDraft> chunks) {

    public PublishKnowledgeAssetCommand {
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(sourceObjectId, "sourceObjectId");
        Objects.requireNonNull(sourceRevisionId, "sourceRevisionId");
        Objects.requireNonNull(normalizedRecordId, "normalizedRecordId");
        Objects.requireNonNull(ownerUserId, "ownerUserId");
        Objects.requireNonNull(embeddingProfile, "embeddingProfile");
        pipelineVersion = requirePipelineVersion(pipelineVersion);
        if (projectionGeneration <= 0) {
            throw new IllegalArgumentException("projectionGeneration must be positive");
        }
        chunks = List.copyOf(Objects.requireNonNull(chunks, "chunks"));
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("At least one knowledge chunk is required");
        }
    }

    private static String requirePipelineVersion(String value) {
        String normalized = Objects.requireNonNull(value, "pipelineVersion").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("pipelineVersion must not be blank");
        }
        return normalized;
    }
}
