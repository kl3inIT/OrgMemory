package com.orgmemory.graphrag.port;

import com.orgmemory.graphrag.model.ContributionEmbedding;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record GraphRevisionEmbeddings(
        UUID organizationId,
        UUID knowledgeAssetId,
        UUID sourceRevisionId,
        long projectionGeneration,
        UUID embeddingProfileId,
        int embeddingDimensions,
        List<ContributionEmbedding> entityEmbeddings,
        List<ContributionEmbedding> relationEmbeddings) {

    public GraphRevisionEmbeddings {
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(knowledgeAssetId, "knowledgeAssetId");
        Objects.requireNonNull(sourceRevisionId, "sourceRevisionId");
        Objects.requireNonNull(embeddingProfileId, "embeddingProfileId");
        if (projectionGeneration < 0) {
            throw new IllegalArgumentException("projectionGeneration must be non-negative");
        }
        if (embeddingDimensions <= 0 || embeddingDimensions > 16000) {
            throw new IllegalArgumentException(
                    "embeddingDimensions must be between 1 and 16000");
        }
        entityEmbeddings =
                List.copyOf(Objects.requireNonNull(entityEmbeddings, "entityEmbeddings"));
        relationEmbeddings =
                List.copyOf(Objects.requireNonNull(relationEmbeddings, "relationEmbeddings"));
        boolean dimensionMismatch = java.util.stream.Stream.concat(
                        entityEmbeddings.stream(), relationEmbeddings.stream())
                .anyMatch(embedding ->
                        embedding.vector().dimensions() != embeddingDimensions);
        if (dimensionMismatch) {
            throw new IllegalArgumentException(
                    "every contribution embedding must match embeddingDimensions");
        }
        Set<UUID> contributionIds = new HashSet<>();
        boolean duplicate = java.util.stream.Stream.concat(
                        entityEmbeddings.stream(), relationEmbeddings.stream())
                .map(ContributionEmbedding::contributionId)
                .anyMatch(id -> !contributionIds.add(id));
        if (duplicate) {
            throw new IllegalArgumentException(
                    "contribution embedding ids must be unique within a revision batch");
        }
    }
}
