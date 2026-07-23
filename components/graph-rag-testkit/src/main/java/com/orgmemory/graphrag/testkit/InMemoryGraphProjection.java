package com.orgmemory.graphrag.testkit;

import com.orgmemory.graphrag.authorization.AuthorizedGraphScope;
import com.orgmemory.graphrag.model.CanonicalEntity;
import com.orgmemory.graphrag.model.CanonicalRelation;
import com.orgmemory.graphrag.model.EntityContribution;
import com.orgmemory.graphrag.model.RelationContribution;
import com.orgmemory.graphrag.port.GraphProjectionReader;
import com.orgmemory.graphrag.port.GraphProjectionWriter;
import com.orgmemory.graphrag.port.GraphRevisionContributions;
import com.orgmemory.graphrag.port.GraphSeedIndex;
import com.orgmemory.graphrag.query.DeterministicRanker;
import com.orgmemory.graphrag.query.RankedItem;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class InMemoryGraphProjection
        implements GraphProjectionReader, GraphProjectionWriter, GraphSeedIndex {

    private final Map<UUID, EntityContribution> entityContributions = new LinkedHashMap<>();
    private final Map<UUID, RelationContribution> relationContributions = new LinkedHashMap<>();

    @Override
    public synchronized void replaceRevision(GraphRevisionContributions contributions) {
        Objects.requireNonNull(contributions, "contributions");
        Set<UUID> replacementIds = new HashSet<>();
        contributions.entities().forEach(contribution -> replacementIds.add(contribution.id()));
        contributions.relations().forEach(contribution -> replacementIds.add(contribution.id()));
        boolean collidesWithAnotherRevision = entityContributions.values().stream()
                        .filter(contribution -> replacementIds.contains(contribution.id()))
                        .anyMatch(contribution -> !belongsToRevision(
                                contribution.provenance().organizationId(),
                                contribution.provenance().sourceRevisionId(),
                                contributions.organizationId(),
                                contributions.sourceRevisionId()))
                || relationContributions.values().stream()
                        .filter(contribution -> replacementIds.contains(contribution.id()))
                        .anyMatch(contribution -> !belongsToRevision(
                                contribution.provenance().organizationId(),
                                contribution.provenance().sourceRevisionId(),
                                contributions.organizationId(),
                                contributions.sourceRevisionId()));
        if (collidesWithAnotherRevision) {
            throw new IllegalArgumentException("contribution id already belongs to another revision");
        }
        removeRevision(contributions.organizationId(), contributions.sourceRevisionId());
        contributions.entities().forEach(contribution ->
                putUnique(entityContributions, contribution.id(), contribution));
        contributions.relations().forEach(contribution ->
                putUnique(relationContributions, contribution.id(), contribution));
    }

    @Override
    public synchronized void removeRevision(UUID organizationId, UUID sourceRevisionId) {
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(sourceRevisionId, "sourceRevisionId");
        entityContributions.values().removeIf(contribution ->
                organizationId.equals(contribution.provenance().organizationId())
                        && sourceRevisionId.equals(contribution.provenance().sourceRevisionId()));
        relationContributions.values().removeIf(contribution ->
                organizationId.equals(contribution.provenance().organizationId())
                        && sourceRevisionId.equals(contribution.provenance().sourceRevisionId()));
    }

    @Override
    public synchronized List<EntityContribution> loadEntityContributions(
            AuthorizedGraphScope scope,
            Collection<UUID> entityIds) {
        Objects.requireNonNull(scope, "scope");
        Set<UUID> requestedIds = Set.copyOf(Objects.requireNonNull(entityIds, "entityIds"));
        return entityContributions.values().stream()
                .filter(contribution -> requestedIds.contains(contribution.entity().id()))
                .filter(contribution -> visible(scope, contribution))
                .sorted(Comparator.comparing(EntityContribution::id))
                .toList();
    }

    @Override
    public synchronized List<RelationContribution> loadRelationContributions(
            AuthorizedGraphScope scope,
            Collection<UUID> relationIds) {
        Objects.requireNonNull(scope, "scope");
        Set<UUID> requestedIds = Set.copyOf(Objects.requireNonNull(relationIds, "relationIds"));
        Set<UUID> visibleEntityIds = visibleEntityIds(scope);
        return relationContributions.values().stream()
                .filter(contribution -> requestedIds.contains(contribution.relation().id()))
                .filter(contribution -> visibleRelation(scope, visibleEntityIds, contribution))
                .sorted(Comparator.comparing(RelationContribution::id))
                .toList();
    }

    @Override
    public synchronized List<CanonicalRelation> loadIncidentRelations(
            AuthorizedGraphScope scope,
            Collection<UUID> entityIds,
            int limit) {
        Objects.requireNonNull(scope, "scope");
        Set<UUID> requestedIds = Set.copyOf(Objects.requireNonNull(entityIds, "entityIds"));
        requireNonNegative(limit, "limit");
        Set<UUID> visibleEntityIds = visibleEntityIds(scope);
        return relationContributions.values().stream()
                .filter(contribution -> visibleRelation(scope, visibleEntityIds, contribution))
                .map(RelationContribution::relation)
                .filter(relation -> requestedIds.stream().anyMatch(relation::isIncidentTo))
                .collect(Collectors.toMap(
                        CanonicalRelation::id,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new))
                .values().stream()
                .sorted(Comparator.comparing(CanonicalRelation::id))
                .limit(limit)
                .toList();
    }

    @Override
    public synchronized Map<UUID, Long> loadVisibleEntityDegrees(
            AuthorizedGraphScope scope,
            Collection<UUID> entityIds) {
        Objects.requireNonNull(scope, "scope");
        Set<UUID> requestedIds = Set.copyOf(Objects.requireNonNull(entityIds, "entityIds"));
        Set<UUID> visibleEntityIds = visibleEntityIds(scope);
        Set<CanonicalRelation> visibleRelations = relationContributions.values().stream()
                .filter(contribution -> visibleRelation(scope, visibleEntityIds, contribution))
                .map(RelationContribution::relation)
                .collect(Collectors.toSet());
        Map<UUID, Long> degrees = new LinkedHashMap<>();
        requestedIds.stream().sorted().forEach(entityId -> degrees.put(
                entityId,
                visibleRelations.stream().filter(relation -> relation.isIncidentTo(entityId)).count()));
        return Map.copyOf(degrees);
    }

    @Override
    public synchronized Map<UUID, Double> loadVisibleRelationWeights(
            AuthorizedGraphScope scope,
            Collection<UUID> relationIds) {
        Objects.requireNonNull(scope, "scope");
        Set<UUID> requestedIds = Set.copyOf(Objects.requireNonNull(relationIds, "relationIds"));
        Set<UUID> visibleEntityIds = visibleEntityIds(scope);
        Map<UUID, Double> weights = new HashMap<>();
        relationContributions.values().stream()
                .filter(contribution -> requestedIds.contains(contribution.relation().id()))
                .filter(contribution -> visibleRelation(scope, visibleEntityIds, contribution))
                .forEach(contribution -> weights.merge(
                        contribution.relation().id(),
                        contribution.provenance().confidence(),
                        Double::sum));
        Map<UUID, Double> ordered = new LinkedHashMap<>();
        requestedIds.stream().sorted().forEach(relationId ->
                ordered.put(relationId, weights.getOrDefault(relationId, 0.0)));
        return Map.copyOf(ordered);
    }

    @Override
    public synchronized List<RankedItem<CanonicalEntity>> searchEntities(
            AuthorizedGraphScope scope,
            String query,
            int limit) {
        Objects.requireNonNull(scope, "scope");
        String normalizedQuery = requireQuery(query);
        requireNonNegative(limit, "limit");

        Map<UUID, RankedItem<CanonicalEntity>> matches = new HashMap<>();
        entityContributions.values().stream()
                .filter(contribution -> visible(scope, contribution))
                .forEach(contribution -> {
                    double score = entityScore(contribution, normalizedQuery);
                    if (score > 0.0) {
                        RankedItem<CanonicalEntity> candidate = new RankedItem<>(
                                contribution.entity().id().toString(),
                                contribution.entity(),
                                score);
                        matches.merge(
                                contribution.entity().id(),
                                candidate,
                                (left, right) -> left.score() >= right.score() ? left : right);
                    }
                });
        return DeterministicRanker.rank(matches.values(), limit);
    }

    @Override
    public synchronized List<RankedItem<CanonicalRelation>> searchRelations(
            AuthorizedGraphScope scope,
            String query,
            int limit) {
        Objects.requireNonNull(scope, "scope");
        String normalizedQuery = requireQuery(query);
        requireNonNegative(limit, "limit");

        Map<UUID, RankedItem<CanonicalRelation>> matches = new HashMap<>();
        Set<UUID> visibleEntityIds = visibleEntityIds(scope);
        relationContributions.values().stream()
                .filter(contribution -> visibleRelation(scope, visibleEntityIds, contribution))
                .forEach(contribution -> {
                    double score = relationScore(contribution, normalizedQuery);
                    if (score > 0.0) {
                        RankedItem<CanonicalRelation> candidate = new RankedItem<>(
                                contribution.relation().id().toString(),
                                contribution.relation(),
                                score);
                        matches.merge(
                                contribution.relation().id(),
                                candidate,
                                (left, right) -> left.score() >= right.score() ? left : right);
                    }
                });
        return DeterministicRanker.rank(matches.values(), limit);
    }

    private static boolean visible(AuthorizedGraphScope scope, EntityContribution contribution) {
        return scope.includes(
                contribution.provenance().organizationId(),
                contribution.provenance().knowledgeAssetId());
    }

    private boolean visibleRelation(
            AuthorizedGraphScope scope,
            Set<UUID> visibleEntityIds,
            RelationContribution contribution) {
        if (!scope.includes(
                contribution.provenance().organizationId(),
                contribution.provenance().knowledgeAssetId())) {
            return false;
        }
        CanonicalRelation relation = contribution.relation();
        return visibleEntityIds.contains(relation.sourceEntityId())
                && visibleEntityIds.contains(relation.targetEntityId());
    }

    private Set<UUID> visibleEntityIds(AuthorizedGraphScope scope) {
        return entityContributions.values().stream()
                .filter(entityContribution -> visible(scope, entityContribution))
                .map(entityContribution -> entityContribution.entity().id())
                .collect(Collectors.toSet());
    }

    private static double entityScore(EntityContribution contribution, String query) {
        CanonicalEntity entity = contribution.entity();
        String normalizedName = entity.normalizedName().toLowerCase(Locale.ROOT);
        if (normalizedName.equals(query)) {
            return 3.0;
        }
        if (normalizedName.contains(query)) {
            return 2.0;
        }
        if (entity.type().toLowerCase(Locale.ROOT).contains(query)
                || contribution.description().toLowerCase(Locale.ROOT).contains(query)) {
            return 1.0;
        }
        return 0.0;
    }

    private static double relationScore(RelationContribution contribution, String query) {
        if (contribution.relation().type().toLowerCase(Locale.ROOT).contains(query)) {
            return 2.0;
        }
        boolean keywordMatch = contribution.keywords().stream()
                .map(keyword -> keyword.toLowerCase(Locale.ROOT))
                .anyMatch(keyword -> keyword.contains(query));
        if (keywordMatch || contribution.description().toLowerCase(Locale.ROOT).contains(query)) {
            return 1.0;
        }
        return 0.0;
    }

    private static String requireQuery(String query) {
        Objects.requireNonNull(query, "query");
        String normalized = query.strip().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        return normalized;
    }

    private static void requireNonNegative(int value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " must be non-negative");
        }
    }

    private static boolean belongsToRevision(
            UUID existingOrganizationId,
            UUID existingRevisionId,
            UUID replacementOrganizationId,
            UUID replacementRevisionId) {
        return existingOrganizationId.equals(replacementOrganizationId)
                && existingRevisionId.equals(replacementRevisionId);
    }

    private static <T> void putUnique(Map<UUID, T> target, UUID id, T value) {
        if (target.putIfAbsent(id, value) != null) {
            throw new IllegalArgumentException("duplicate contribution id: " + id);
        }
    }
}
