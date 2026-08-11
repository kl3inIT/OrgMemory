package com.orgmemory.graphrag.testkit;

import com.orgmemory.graphrag.authorization.AuthorizedEvidenceScope;
import com.orgmemory.graphrag.model.CanonicalEntity;
import com.orgmemory.graphrag.model.CanonicalRelation;
import com.orgmemory.graphrag.model.EntityContribution;
import com.orgmemory.graphrag.model.RelationContribution;
import com.orgmemory.graphrag.query.AuthorizedQueryProjection;
import com.orgmemory.graphrag.query.AuthorizedGraphTraversal;
import com.orgmemory.graphrag.query.DeterministicRanker;
import com.orgmemory.graphrag.query.RankedItem;
import com.orgmemory.graphrag.storage.ProjectionSnapshot;
import com.orgmemory.graphrag.storage.AuthorizedGraphTraversalSource;
import com.orgmemory.graphrag.storage.AuthorizedGraphTraversalSource.IncidentRelationPage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Deterministic reference adapter for PR 7 query conformance. */
public final class InMemoryAuthorizedQueryProjection
        implements AuthorizedQueryProjection {

    private final Map<UUID, EntityContribution> entities = new LinkedHashMap<>();
    private final Map<UUID, RelationContribution> relations = new LinkedHashMap<>();
    private final Map<UUID, Chunk> chunks = new LinkedHashMap<>();
    private final Map<UUID, Double> entityScores = new HashMap<>();
    private final Map<UUID, Double> relationScores = new HashMap<>();
    private final Map<UUID, Double> chunkScores = new HashMap<>();
    private final AuthorizedGraphTraversal traversal =
            new AuthorizedGraphTraversal(new TraversalSource());
    private int reads;

    public synchronized InMemoryAuthorizedQueryProjection add(
            EntityContribution contribution, double score) {
        Objects.requireNonNull(contribution, "contribution");
        entities.put(contribution.id(), contribution);
        entityScores.merge(contribution.entity().id(), score, Math::max);
        return this;
    }

    public synchronized InMemoryAuthorizedQueryProjection add(
            RelationContribution contribution, double score) {
        Objects.requireNonNull(contribution, "contribution");
        relations.put(contribution.id(), contribution);
        relationScores.merge(contribution.relation().id(), score, Math::max);
        return this;
    }

    public synchronized InMemoryAuthorizedQueryProjection add(
            Chunk chunk, double score) {
        Objects.requireNonNull(chunk, "chunk");
        chunks.put(chunk.id(), chunk);
        chunkScores.put(chunk.id(), score);
        return this;
    }

    public synchronized int reads() {
        return reads;
    }

    @Override
    public synchronized List<RankedItem<CanonicalEntity>> searchEntities(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            VectorSearch search) {
        begin(scope, snapshot);
        Map<UUID, CanonicalEntity> visible = visibleEntities(scope, snapshot).stream()
                .collect(Collectors.toMap(
                        contribution -> contribution.entity().id(),
                        EntityContribution::entity,
                        (left, right) -> left,
                        LinkedHashMap::new));
        return DeterministicRanker.rank(
                visible.values().stream()
                        .map(entity -> new RankedItem<>(
                                entity.id().toString(),
                                entity,
                                entityScores.getOrDefault(entity.id(), 0.0)))
                        .filter(item -> item.score() >= search.minimumSimilarity())
                        .toList(),
                search.limit());
    }

    @Override
    public synchronized List<RankedItem<CanonicalRelation>> searchRelations(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            VectorSearch search) {
        begin(scope, snapshot);
        Map<UUID, CanonicalRelation> visible = visibleRelations(scope, snapshot).stream()
                .collect(Collectors.toMap(
                        contribution -> contribution.relation().id(),
                        RelationContribution::relation,
                        (left, right) -> left,
                        LinkedHashMap::new));
        return DeterministicRanker.rank(
                visible.values().stream()
                        .map(relation -> new RankedItem<>(
                                relation.id().toString(),
                                relation,
                                relationScores.getOrDefault(relation.id(), 0.0)))
                        .filter(item -> item.score() >= search.minimumSimilarity())
                        .toList(),
                search.limit());
    }

    @Override
    public synchronized List<RankedItem<Chunk>> searchChunks(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            VectorSearch search) {
        begin(scope, snapshot);
        return rankVisibleChunks(scope, snapshot, search, chunks.keySet());
    }

    @Override
    public synchronized List<RankedItem<Chunk>> rankChunks(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            VectorSearch search,
            Collection<UUID> candidateChunkIds) {
        begin(scope, snapshot);
        return rankVisibleChunks(scope, snapshot, search, candidateChunkIds);
    }

    @Override
    public synchronized List<UUID> expandEntityIds(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> seedEntityIds,
            int maximumDepth,
            int limit) {
        return traversal.expandEntityIds(
                scope,
                snapshot,
                seedEntityIds,
                maximumDepth,
                limit);
    }

    @Override
    public synchronized List<EntityContribution> loadEntityContributions(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> entityIds) {
        begin(scope, snapshot);
        Set<UUID> requested = Set.copyOf(entityIds);
        return visibleEntities(scope, snapshot).stream()
                .filter(contribution -> requested.contains(contribution.entity().id()))
                .sorted(Comparator.comparing(EntityContribution::id))
                .toList();
    }

    @Override
    public synchronized List<RelationContribution> loadRelationContributions(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> relationIds) {
        begin(scope, snapshot);
        Set<UUID> requested = Set.copyOf(relationIds);
        return visibleRelations(scope, snapshot).stream()
                .filter(contribution -> requested.contains(contribution.relation().id()))
                .sorted(Comparator.comparing(RelationContribution::id))
                .toList();
    }

    @Override
    public synchronized List<CanonicalRelation> loadIncidentRelations(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> entityIds,
            int limit) {
        begin(scope, snapshot);
        Set<UUID> requested = Set.copyOf(entityIds);
        return visibleRelations(scope, snapshot).stream()
                .map(RelationContribution::relation)
                .filter(relation -> requested.stream().anyMatch(relation::isIncidentTo))
                .collect(Collectors.toMap(
                        CanonicalRelation::id,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new))
                .values().stream()
                .sorted(Comparator.comparing(
                        CanonicalRelation::id,
                        AuthorizedGraphTraversalSource.CANONICAL_UUID_ORDER))
                .limit(limit)
                .toList();
    }

    @Override
    public synchronized Map<UUID, Long> loadVisibleEntityDegrees(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> entityIds) {
        begin(scope, snapshot);
        Set<CanonicalRelation> visible = visibleRelations(scope, snapshot).stream()
                .map(RelationContribution::relation)
                .collect(Collectors.toSet());
        LinkedHashMap<UUID, Long> result = new LinkedHashMap<>();
        entityIds.stream().sorted().forEach(id -> result.put(
                id,
                visible.stream().filter(relation -> relation.isIncidentTo(id)).count()));
        return Map.copyOf(result);
    }

    @Override
    public synchronized Map<UUID, Double> loadVisibleRelationWeights(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> relationIds) {
        begin(scope, snapshot);
        Set<UUID> requested = Set.copyOf(relationIds);
        Map<UUID, Double> weights = new HashMap<>();
        visibleRelations(scope, snapshot).stream()
                .filter(contribution -> requested.contains(contribution.relation().id()))
                .forEach(contribution -> weights.merge(
                        contribution.relation().id(),
                        contribution.weight(),
                        Double::sum));
        return Map.copyOf(weights);
    }

    @Override
    public synchronized List<Chunk> loadChunks(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> chunkIds) {
        begin(scope, snapshot);
        return chunkIds.stream()
                .distinct()
                .map(chunks::get)
                .filter(Objects::nonNull)
                .filter(chunk -> visible(scope, snapshot, chunk))
                .toList();
    }

    private List<RankedItem<Chunk>> rankVisibleChunks(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            VectorSearch search,
            Collection<UUID> candidateIds) {
        Set<UUID> candidates = Set.copyOf(candidateIds);
        return DeterministicRanker.rank(
                chunks.values().stream()
                        .filter(chunk -> candidates.contains(chunk.id()))
                        .filter(chunk -> visible(scope, snapshot, chunk))
                        .map(chunk -> new RankedItem<>(
                                chunk.id().toString(),
                                chunk,
                                chunkScores.getOrDefault(chunk.id(), 0.0)))
                        .filter(item -> item.score() >= search.minimumSimilarity())
                        .toList(),
                search.limit());
    }

    private List<EntityContribution> visibleEntities(
            AuthorizedEvidenceScope scope, ProjectionSnapshot snapshot) {
        return entities.values().stream()
                .filter(contribution -> visible(scope, snapshot, contribution))
                .toList();
    }

    private List<RelationContribution> visibleRelations(
            AuthorizedEvidenceScope scope, ProjectionSnapshot snapshot) {
        Set<UUID> visibleEntityIds = visibleEntities(scope, snapshot).stream()
                .map(contribution -> contribution.entity().id())
                .collect(Collectors.toSet());
        return relations.values().stream()
                .filter(contribution -> visible(scope, snapshot, contribution))
                .filter(contribution -> visibleEntityIds.contains(
                                contribution.relation().sourceEntityId())
                        && visibleEntityIds.contains(
                                contribution.relation().targetEntityId()))
                .toList();
    }

    private static boolean visible(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            EntityContribution contribution) {
        return scope.includesEvidence(
                        contribution.provenance().organizationId(),
                        contribution.provenance().knowledgeAssetId(),
                        contribution.provenance().sourceRevisionId())
                && contribution.provenance().projectionGeneration() == snapshot.generation();
    }

    private static boolean visible(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            RelationContribution contribution) {
        return scope.includesEvidence(
                        contribution.provenance().organizationId(),
                        contribution.provenance().knowledgeAssetId(),
                        contribution.provenance().sourceRevisionId())
                && contribution.provenance().projectionGeneration() == snapshot.generation();
    }

    private static boolean visible(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Chunk chunk) {
        return scope.includesEvidence(
                        chunk.evidence().organizationId(),
                        chunk.evidence().knowledgeAssetId(),
                        chunk.evidence().sourceRevisionId())
                && snapshot.namespace().organizationId().equals(
                        chunk.evidence().organizationId())
                && chunk.projectionGeneration() == snapshot.generation();
    }

    private void begin(AuthorizedEvidenceScope scope, ProjectionSnapshot snapshot) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(snapshot, "snapshot");
        if (!scope.organizationId().equals(snapshot.namespace().organizationId())) {
            throw new IllegalArgumentException(
                    "authorization scope and snapshot belong to different organizations");
        }
        reads++;
    }

    private final class TraversalSource implements AuthorizedGraphTraversalSource {

        @Override
        public void validateSnapshot(
                AuthorizedEvidenceScope scope,
                ProjectionSnapshot snapshot) {
            begin(scope, snapshot);
        }

        @Override
        public List<CanonicalEntity> loadEntities(
                AuthorizedEvidenceScope scope,
                ProjectionSnapshot snapshot,
                Collection<UUID> entityIds) {
            Set<UUID> requested = Set.copyOf(entityIds);
            return visibleEntities(scope, snapshot).stream()
                    .map(EntityContribution::entity)
                    .filter(entity -> requested.contains(entity.id()))
                    .collect(Collectors.toMap(
                            CanonicalEntity::id,
                            Function.identity(),
                            (left, right) -> left,
                            LinkedHashMap::new))
                    .values()
                    .stream()
                    .sorted(Comparator.comparing(
                            CanonicalEntity::id,
                            AuthorizedGraphTraversalSource.CANONICAL_UUID_ORDER))
                    .toList();
        }

        @Override
        public IncidentRelationPage loadIncidentRelationPage(
                AuthorizedEvidenceScope scope,
                ProjectionSnapshot snapshot,
                Collection<UUID> entityIds,
                UUID afterRelationId,
                int pageSize) {
            if (pageSize <= 0 || pageSize > MAXIMUM_PAGE_SIZE) {
                throw new IllegalArgumentException("invalid pageSize");
            }
            Set<UUID> requested = Set.copyOf(entityIds);
            List<CanonicalRelation> fetched = visibleRelations(scope, snapshot).stream()
                    .map(RelationContribution::relation)
                    .filter(relation -> requested.stream().anyMatch(relation::isIncidentTo))
                    .filter(relation -> afterRelationId == null
                            || AuthorizedGraphTraversalSource.CANONICAL_UUID_ORDER.compare(
                                    relation.id(), afterRelationId) > 0)
                    .collect(Collectors.toMap(
                            CanonicalRelation::id,
                            Function.identity(),
                            (left, right) -> left,
                            LinkedHashMap::new))
                    .values()
                    .stream()
                    .sorted(Comparator.comparing(
                            CanonicalRelation::id,
                            AuthorizedGraphTraversalSource.CANONICAL_UUID_ORDER))
                    .limit((long) pageSize + 1)
                    .toList();
            boolean hasMore = fetched.size() > pageSize;
            List<CanonicalRelation> page = hasMore
                    ? List.copyOf(fetched.subList(0, pageSize))
                    : fetched;
            return new IncidentRelationPage(
                    page,
                    hasMore ? page.getLast().id() : null);
        }
    }
}
