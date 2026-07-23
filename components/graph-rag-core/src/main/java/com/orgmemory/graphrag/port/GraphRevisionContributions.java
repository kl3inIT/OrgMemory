package com.orgmemory.graphrag.port;

import com.orgmemory.graphrag.model.EntityContribution;
import com.orgmemory.graphrag.model.RelationContribution;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record GraphRevisionContributions(
        UUID organizationId,
        UUID knowledgeAssetId,
        UUID sourceRevisionId,
        long projectionGeneration,
        List<EntityContribution> entities,
        List<RelationContribution> relations) {

    public GraphRevisionContributions {
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(knowledgeAssetId, "knowledgeAssetId");
        Objects.requireNonNull(sourceRevisionId, "sourceRevisionId");
        if (projectionGeneration < 0) {
            throw new IllegalArgumentException("projectionGeneration must be non-negative");
        }
        entities = List.copyOf(Objects.requireNonNull(entities, "entities"));
        relations = List.copyOf(Objects.requireNonNull(relations, "relations"));
        boolean mismatchedEntity = entities.stream().anyMatch(contribution ->
                !organizationId.equals(contribution.provenance().organizationId())
                        || !knowledgeAssetId.equals(contribution.provenance().knowledgeAssetId())
                        || !sourceRevisionId.equals(contribution.provenance().sourceRevisionId())
                        || projectionGeneration != contribution.provenance().projectionGeneration());
        boolean mismatchedRelation = relations.stream().anyMatch(contribution ->
                !organizationId.equals(contribution.provenance().organizationId())
                        || !knowledgeAssetId.equals(contribution.provenance().knowledgeAssetId())
                        || !sourceRevisionId.equals(contribution.provenance().sourceRevisionId())
                        || projectionGeneration != contribution.provenance().projectionGeneration());
        if (mismatchedEntity || mismatchedRelation) {
            throw new IllegalArgumentException("all contributions must belong to the revision batch");
        }
        Set<UUID> contributionIds = new HashSet<>();
        boolean duplicateContributionId = entities.stream()
                        .map(EntityContribution::id)
                        .anyMatch(id -> !contributionIds.add(id))
                || relations.stream()
                        .map(RelationContribution::id)
                        .anyMatch(id -> !contributionIds.add(id));
        if (duplicateContributionId) {
            throw new IllegalArgumentException("contribution ids must be unique within a revision batch");
        }
        Set<UUID> entityIds = new HashSet<>();
        entities.stream().map(contribution -> contribution.entity().id()).forEach(entityIds::add);
        boolean unresolvedEndpoint = relations.stream().anyMatch(contribution ->
                !entityIds.contains(contribution.relation().sourceEntityId())
                        || !entityIds.contains(contribution.relation().targetEntityId()));
        if (unresolvedEndpoint) {
            throw new IllegalArgumentException(
                    "every relation endpoint must have an entity contribution in the revision batch");
        }
    }
}
