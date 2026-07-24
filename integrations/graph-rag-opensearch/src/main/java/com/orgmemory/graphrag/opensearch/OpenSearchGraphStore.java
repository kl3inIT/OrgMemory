package com.orgmemory.graphrag.opensearch;

import com.orgmemory.graphrag.authorization.AuthorizedEvidenceScope;
import com.orgmemory.graphrag.model.CanonicalEntity;
import com.orgmemory.graphrag.model.CanonicalRelation;
import com.orgmemory.graphrag.model.EntityContribution;
import com.orgmemory.graphrag.model.RelationContribution;
import com.orgmemory.graphrag.port.GraphRevisionContributions;
import com.orgmemory.graphrag.storage.GraphStore;
import com.orgmemory.graphrag.storage.ProjectionBatch;
import com.orgmemory.graphrag.storage.ProjectionKind;
import com.orgmemory.graphrag.storage.ProjectionSnapshot;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.opensearch.client.opensearch._types.query_dsl.Query;

public final class OpenSearchGraphStore implements GraphStore {

    private final OpenSearchStagedIndex entities;
    private final OpenSearchStagedIndex relations;
    private final OpenSearchOperations operations;
    private final OpenSearchIndexNames indexes;
    private final OpenSearchPplGraphLookup ppl;
    private final int maximumFrontier;

    OpenSearchGraphStore(
            OpenSearchOperations operations,
            OpenSearchProjectionPublicationStore publications,
            OpenSearchIndexNames indexes,
            int maximumFrontier,
            OpenSearchPplGraphLookup ppl) {
        if (maximumFrontier <= 0) {
            throw new IllegalArgumentException("maximumFrontier must be positive");
        }
        this.operations = Objects.requireNonNull(operations, "operations");
        this.indexes = Objects.requireNonNull(indexes, "indexes");
        this.ppl = Objects.requireNonNull(ppl, "ppl");
        this.entities = new OpenSearchStagedIndex(
                operations,
                publications,
                indexes.control(),
                batch -> indexes.graphEntities(batch.id()),
                snapshot -> indexes.graphEntities(snapshot.batchId()),
                ProjectionKind.GRAPH);
        this.relations = new OpenSearchStagedIndex(
                operations,
                publications,
                indexes.control(),
                batch -> indexes.graphRelations(batch.id()),
                snapshot -> indexes.graphRelations(snapshot.batchId()),
                ProjectionKind.GRAPH);
        this.maximumFrontier = maximumFrontier;
    }

    @Override
    public void stageReplaceRevision(
            ProjectionBatch batch,
            GraphRevisionContributions contributions) {
        Objects.requireNonNull(batch, "batch");
        Objects.requireNonNull(contributions, "contributions");
        if (!batch.namespace().organizationId().equals(contributions.organizationId())) {
            throw new IllegalArgumentException(
                    "graph revision belongs to another organization");
        }
        ensureBatchIndices(batch);
        stageDeleteRevision(batch, contributions.sourceRevisionId());
        entities.stageUpsert(
                batch,
                contributions.entities().stream()
                        .map(contribution -> OpenSearchProjectionCodec.entity(batch, contribution))
                        .toList());
        relations.stageUpsert(
                batch,
                contributions.relations().stream()
                        .map(contribution -> OpenSearchProjectionCodec.relation(batch, contribution))
                        .toList());
    }

    @Override
    public void stageDeleteRevision(
            ProjectionBatch batch,
            UUID sourceRevisionId) {
        Objects.requireNonNull(sourceRevisionId, "sourceRevisionId");
        ensureBatchIndices(batch);
        List<Query> filter = List.of(OpenSearchStagedIndex.term(
                OpenSearchProjectionCodec.REVISION_ID,
                sourceRevisionId.toString()));
        entities.stageDeleteMatching(batch, filter);
        relations.stageDeleteMatching(batch, filter);
    }

    @Override
    public List<CanonicalEntity> loadEntities(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> entityIds) {
        return distinctEntities(loadEntityContributions(scope, snapshot, entityIds));
    }

    @Override
    public List<CanonicalRelation> loadRelations(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> relationIds) {
        return distinctRelations(loadRelationContributions(scope, snapshot, relationIds));
    }

    @Override
    public List<EntityContribution> loadEntityContributions(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> entityIds) {
        List<UUID> ids = requireUuidIds(entityIds);
        if (ids.isEmpty()) {
            return List.of();
        }
        return entities.search(
                        scope,
                        snapshot,
                        List.of(OpenSearchStoreSupport.anyTerms(
                                "entity_id",
                                ids.stream().map(UUID::toString).toList())),
                        Integer.MAX_VALUE)
                .stream()
                .map(OpenSearchProjectionCodec::entity)
                .sorted(Comparator.comparing(EntityContribution::id))
                .toList();
    }

    @Override
    public List<RelationContribution> loadRelationContributions(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> relationIds) {
        List<UUID> ids = requireUuidIds(relationIds);
        if (ids.isEmpty()) {
            return List.of();
        }
        return relations.search(
                        scope,
                        snapshot,
                        List.of(OpenSearchStoreSupport.anyTerms(
                                "relation_id",
                                ids.stream().map(UUID::toString).toList())),
                        Integer.MAX_VALUE)
                .stream()
                .map(OpenSearchProjectionCodec::relation)
                .sorted(Comparator.comparing(RelationContribution::id))
                .toList();
    }

    @Override
    public List<CanonicalRelation> loadIncidentRelations(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> entityIds,
            int limit) {
        List<UUID> ids = requireUuidIds(entityIds);
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        if (ids.isEmpty()) {
            return List.of();
        }
        List<String> encoded = ids.stream().map(UUID::toString).toList();
        Query incident = Query.of(query -> query.bool(bool -> bool
                .should(OpenSearchStoreSupport.anyTerms("source_entity_id", encoded))
                .should(OpenSearchStoreSupport.anyTerms("target_entity_id", encoded))
                .minimumShouldMatch("1")));
        List<RelationContribution> contributions = relations.search(
                        scope,
                        snapshot,
                        List.of(incident),
                        Integer.MAX_VALUE)
                .stream()
                .map(OpenSearchProjectionCodec::relation)
                .toList();
        return distinctRelations(contributions).stream().limit(limit).toList();
    }

    @Override
    public Map<UUID, Long> loadVisibleEntityDegrees(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> entityIds) {
        List<UUID> ids = requireUuidIds(entityIds);
        Map<UUID, Long> result = new LinkedHashMap<>();
        ids.forEach(id -> result.put(id, 0L));
        Set<CanonicalRelation> incident =
                Set.copyOf(loadIncidentRelations(
                        scope,
                        snapshot,
                        ids,
                        Integer.MAX_VALUE));
        for (UUID entityId : ids) {
            result.put(
                    entityId,
                    incident.stream()
                            .filter(relation -> relation.isIncidentTo(entityId))
                            .count());
        }
        return Map.copyOf(result);
    }

    @Override
    public Map<UUID, Double> loadVisibleRelationWeights(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> relationIds) {
        List<UUID> ids = requireUuidIds(relationIds);
        Map<UUID, Double> result = new LinkedHashMap<>();
        ids.forEach(id -> result.put(id, 0.0));
        for (RelationContribution contribution :
                loadRelationContributions(scope, snapshot, ids)) {
            result.merge(
                    contribution.relation().id(),
                    contribution.weight(),
                    Double::sum);
        }
        return Map.copyOf(result);
    }

    @Override
    public List<UUID> expandEntityIds(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> seedEntityIds,
            int maximumDepth,
            int limit) {
        List<UUID> seeds = requireUuidIds(seedEntityIds);
        if (maximumDepth < 0) {
            throw new IllegalArgumentException("maximumDepth must be non-negative");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        if (seeds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<UUID> visible = loadEntities(scope, snapshot, seeds).stream()
                .map(CanonicalEntity::id)
                .collect(
                        LinkedHashSet::new,
                        LinkedHashSet::add,
                        LinkedHashSet::addAll);
        LinkedHashSet<UUID> frontier = new LinkedHashSet<>(visible);
        Optional<List<UUID>> accelerated = ppl.expand(
                scope,
                snapshot,
                Set.copyOf(visible),
                maximumDepth,
                limit);
        if (accelerated.isPresent()) {
            Set<UUID> verified = loadEntities(
                            scope,
                            snapshot,
                            accelerated.orElseThrow())
                    .stream()
                    .map(CanonicalEntity::id)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            return accelerated.orElseThrow().stream()
                    .filter(verified::contains)
                    .limit(limit)
                    .toList();
        }
        int depth = 0;
        while (!frontier.isEmpty() && depth < maximumDepth && visible.size() < limit) {
            List<CanonicalRelation> incident = loadIncidentRelations(
                    scope,
                    snapshot,
                    frontier,
                    maximumFrontier);
            LinkedHashSet<UUID> candidates = new LinkedHashSet<>();
            for (CanonicalRelation relation : incident) {
                candidates.add(relation.sourceEntityId());
                candidates.add(relation.targetEntityId());
            }
            candidates.removeAll(visible);
            LinkedHashSet<UUID> next = loadEntities(scope, snapshot, candidates).stream()
                    .map(CanonicalEntity::id)
                    .sorted()
                    .limit(Math.max(0, limit - visible.size()))
                    .collect(
                            LinkedHashSet::new,
                            LinkedHashSet::add,
                            LinkedHashSet::addAll);
            visible.addAll(next);
            frontier = next;
            depth++;
        }
        return visible.stream().limit(limit).toList();
    }

    @Override
    public void discard(ProjectionBatch batch) {
        Objects.requireNonNull(batch, "batch");
        operations.deleteIndex(indexes.graphEntities(batch.id()));
        operations.deleteIndex(indexes.graphRelations(batch.id()));
        entities.discardMarker(batch);
        relations.discardMarker(batch);
    }

    private void ensureBatchIndices(ProjectionBatch batch) {
        operations.ensureIndex(
                indexes.graphEntities(batch.id()),
                OpenSearchSchemas.graphEntities());
        operations.ensureIndex(
                indexes.graphRelations(batch.id()),
                OpenSearchSchemas.graphRelations());
    }

    private static List<CanonicalEntity> distinctEntities(
            Collection<EntityContribution> contributions) {
        Map<UUID, CanonicalEntity> distinct = new LinkedHashMap<>();
        contributions.stream()
                .sorted(Comparator.comparing(EntityContribution::id))
                .forEach(contribution ->
                        distinct.putIfAbsent(
                                contribution.entity().id(),
                                contribution.entity()));
        return List.copyOf(distinct.values());
    }

    private static List<CanonicalRelation> distinctRelations(
            Collection<RelationContribution> contributions) {
        Map<UUID, CanonicalRelation> distinct = new LinkedHashMap<>();
        contributions.stream()
                .sorted(Comparator.comparing(RelationContribution::id))
                .forEach(contribution ->
                        distinct.putIfAbsent(
                                contribution.relation().id(),
                                contribution.relation()));
        return List.copyOf(distinct.values());
    }

    private static List<UUID> requireUuidIds(Collection<UUID> ids) {
        List<UUID> immutable = List.copyOf(Objects.requireNonNull(ids, "ids"));
        if (immutable.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("ids must not contain null values");
        }
        return immutable;
    }
}
