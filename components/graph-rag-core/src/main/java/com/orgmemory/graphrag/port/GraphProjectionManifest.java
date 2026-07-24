package com.orgmemory.graphrag.port;

import com.orgmemory.graphrag.cache.CanonicalCacheKeyHasher;
import com.orgmemory.graphrag.model.ContributionEmbedding;
import com.orgmemory.graphrag.model.EntityContribution;
import com.orgmemory.graphrag.model.RelationContribution;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Canonical identity of every artifact participating in one graph publication. */
public final class GraphProjectionManifest {

    private GraphProjectionManifest() {
    }

    public static String fingerprint(GraphRevisionProjection projection) {
        Objects.requireNonNull(projection, "projection");
        GraphRevisionContributions contributions = projection.contributions();
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("organizationId", contributions.organizationId().toString());
        fields.put("knowledgeAssetId", contributions.knowledgeAssetId().toString());
        fields.put("sourceRevisionId", contributions.sourceRevisionId().toString());
        fields.put(
                "projectionGeneration",
                Long.toString(contributions.projectionGeneration()));
        fields.put(
                "entities",
                contributions.entities().stream()
                        .sorted(java.util.Comparator.comparing(EntityContribution::id))
                        .map(GraphProjectionManifest::entity)
                        .collect(Collectors.joining("\n")));
        fields.put(
                "relations",
                contributions.relations().stream()
                        .sorted(java.util.Comparator.comparing(RelationContribution::id))
                        .map(GraphProjectionManifest::relation)
                        .collect(Collectors.joining("\n")));
        fields.put(
                "embeddingProfileId",
                projection.embeddings().embeddingProfileId().toString());
        fields.put(
                "embeddingDimensions",
                Integer.toString(projection.embeddings().embeddingDimensions()));
        fields.put(
                "entityEmbeddings",
                projection.embeddings().entityEmbeddings().stream()
                        .sorted(java.util.Comparator.comparing(
                                ContributionEmbedding::contributionId))
                        .map(GraphProjectionManifest::embedding)
                        .collect(Collectors.joining("\n")));
        fields.put(
                "relationEmbeddings",
                projection.embeddings().relationEmbeddings().stream()
                        .sorted(java.util.Comparator.comparing(
                                ContributionEmbedding::contributionId))
                        .map(GraphProjectionManifest::embedding)
                        .collect(Collectors.joining("\n")));
        return CanonicalCacheKeyHasher.sha256(
                "orgmemory.graph-rag.projection-manifest.v1", fields);
    }

    public static String idempotencyKey(GraphRevisionContributions contributions) {
        Objects.requireNonNull(contributions, "contributions");
        return "graph:"
                + contributions.organizationId()
                + ":"
                + contributions.sourceRevisionId()
                + ":"
                + contributions.projectionGeneration();
    }

    private static String entity(EntityContribution contribution) {
        return String.join(
                "|",
                contribution.id().toString(),
                contribution.entity().id().toString(),
                contribution.entity().normalizedName(),
                contribution.type(),
                contribution.description(),
                provenance(contribution.provenance()));
    }

    private static String relation(RelationContribution contribution) {
        return String.join(
                "|",
                contribution.id().toString(),
                contribution.relation().id().toString(),
                contribution.relation().sourceEntityId().toString(),
                contribution.relation().targetEntityId().toString(),
                contribution.relation().orientation().name(),
                contribution.type(),
                String.join("\u001f", contribution.keywords()),
                contribution.description(),
                Double.toHexString(contribution.weight()),
                provenance(contribution.provenance()));
    }

    private static String provenance(
            com.orgmemory.graphrag.model.EvidenceProvenance provenance) {
        return String.join(
                "|",
                provenance.evidence().organizationId().toString(),
                provenance.evidence().knowledgeAssetId().toString(),
                provenance.evidence().sourceRevisionId().toString(),
                Objects.toString(provenance.evidence().chunkId(), ""),
                provenance.evidence().aclSnapshotId().toString(),
                Long.toString(provenance.evidence().aclGeneration()),
                Long.toString(provenance.projectionGeneration()),
                provenance.extractorProvider(),
                provenance.extractorModel(),
                provenance.promptVersion(),
                provenance.extractionProfileFingerprint(),
                Double.toHexString(provenance.confidence()));
    }

    private static String embedding(ContributionEmbedding embedding) {
        StringBuilder value = new StringBuilder(embedding.contributionId().toString());
        for (int index = 0; index < embedding.vector().dimensions(); index++) {
            value.append('|')
                    .append(Float.toHexString(embedding.vector().valueAt(index)));
        }
        return value.toString();
    }
}
