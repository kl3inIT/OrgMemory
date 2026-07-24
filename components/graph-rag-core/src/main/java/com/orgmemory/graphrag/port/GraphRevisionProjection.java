package com.orgmemory.graphrag.port;

import com.orgmemory.graphrag.model.ContributionEmbedding;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record GraphRevisionProjection(
        GraphRevisionContributions contributions,
        GraphRevisionEmbeddings embeddings) {

    public GraphRevisionProjection {
        Objects.requireNonNull(contributions, "contributions");
        Objects.requireNonNull(embeddings, "embeddings");
        if (!contributions.organizationId().equals(embeddings.organizationId())
                || !contributions.knowledgeAssetId().equals(embeddings.knowledgeAssetId())
                || !contributions.sourceRevisionId().equals(embeddings.sourceRevisionId())
                || contributions.projectionGeneration() != embeddings.projectionGeneration()) {
            throw new IllegalArgumentException(
                    "contributions and embeddings must target the same graph generation");
        }
        Set<UUID> contributionIds = Stream.concat(
                        contributions.entities().stream().map(entity -> entity.id()),
                        contributions.relations().stream().map(relation -> relation.id()))
                .collect(Collectors.toUnmodifiableSet());
        Set<UUID> embeddingIds = Stream.concat(
                        embeddings.entityEmbeddings().stream(),
                        embeddings.relationEmbeddings().stream())
                .map(ContributionEmbedding::contributionId)
                .collect(Collectors.toUnmodifiableSet());
        if (!contributionIds.equals(embeddingIds)) {
            throw new IllegalArgumentException(
                    "every graph contribution must have exactly one embedding");
        }
    }

    public String manifestFingerprint() {
        return GraphProjectionManifest.fingerprint(this);
    }

    public String idempotencyKey() {
        return GraphProjectionManifest.idempotencyKey(contributions);
    }
}
