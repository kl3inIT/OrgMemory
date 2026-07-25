package com.orgmemory.graphrag.query;

import com.orgmemory.graphrag.authorization.AuthorizedEvidenceScope;
import com.orgmemory.graphrag.model.CanonicalEntity;
import com.orgmemory.graphrag.model.CanonicalRelation;
import com.orgmemory.graphrag.model.EntityContribution;
import com.orgmemory.graphrag.model.RelationContribution;
import com.orgmemory.graphrag.storage.ContentStore;
import com.orgmemory.graphrag.storage.GraphStore;
import com.orgmemory.graphrag.storage.ProjectionSnapshot;
import com.orgmemory.graphrag.storage.VectorIndex;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Framework-neutral query projection composed from the shared storage ports.
 */
public final class StoreBackedAuthorizedQueryProjection
        implements AuthorizedQueryProjection {

    private final ContentStore content;
    private final VectorIndex vectors;
    private final GraphStore graph;

    public StoreBackedAuthorizedQueryProjection(
            ContentStore content,
            VectorIndex vectors,
            GraphStore graph) {
        this.content = Objects.requireNonNull(content, "content");
        this.vectors = Objects.requireNonNull(vectors, "vectors");
        this.graph = Objects.requireNonNull(graph, "graph");
    }

    @Override
    public List<RankedItem<CanonicalEntity>> searchEntities(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            VectorSearch search) {
        List<VectorIndex.VectorHit> hits =
                vectorHits(scope, snapshot, search, VectorIndex.VectorKind.ENTITY, Set.of());
        Map<UUID, Double> scores = subjectScores(hits);
        return graph.loadEntities(scope, snapshot, scores.keySet()).stream()
                .map(entity -> new RankedItem<>(
                        entity.id().toString(),
                        entity,
                        scores.get(entity.id())))
                .sorted(rankedOrder())
                .limit(search.limit())
                .toList();
    }

    @Override
    public List<RankedItem<CanonicalRelation>> searchRelations(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            VectorSearch search) {
        List<VectorIndex.VectorHit> hits =
                vectorHits(scope, snapshot, search, VectorIndex.VectorKind.RELATION, Set.of());
        Map<UUID, Double> scores = subjectScores(hits);
        return graph.loadRelations(scope, snapshot, scores.keySet()).stream()
                .map(relation -> new RankedItem<>(
                        relation.id().toString(),
                        relation,
                        scores.get(relation.id())))
                .sorted(rankedOrder())
                .limit(search.limit())
                .toList();
    }

    @Override
    public List<RankedItem<Chunk>> searchChunks(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            VectorSearch search) {
        return chunks(
                scope,
                snapshot,
                vectorHits(scope, snapshot, search, VectorIndex.VectorKind.CHUNK, Set.of()));
    }

    @Override
    public List<RankedItem<Chunk>> rankChunks(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            VectorSearch search,
            Collection<UUID> candidateChunkIds) {
        Set<String> candidates = Objects.requireNonNull(candidateChunkIds, "candidateChunkIds")
                .stream()
                .map(UUID::toString)
                .collect(Collectors.toUnmodifiableSet());
        if (candidates.isEmpty()) {
            return List.of();
        }
        return chunks(
                scope,
                snapshot,
                vectorHits(
                        scope,
                        snapshot,
                        search,
                        VectorIndex.VectorKind.CHUNK,
                        candidates));
    }

    @Override
    public List<UUID> expandEntityIds(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> seedEntityIds,
            int maximumDepth,
            int limit) {
        return graph.expandEntityIds(
                scope, snapshot, seedEntityIds, maximumDepth, limit);
    }

    @Override
    public List<EntityContribution> loadEntityContributions(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> entityIds) {
        return graph.loadEntityContributions(scope, snapshot, entityIds);
    }

    @Override
    public List<RelationContribution> loadRelationContributions(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> relationIds) {
        return graph.loadRelationContributions(scope, snapshot, relationIds);
    }

    @Override
    public List<CanonicalRelation> loadIncidentRelations(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> entityIds,
            int limit) {
        return graph.loadIncidentRelations(scope, snapshot, entityIds, limit);
    }

    @Override
    public Map<UUID, Long> loadVisibleEntityDegrees(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> entityIds) {
        return graph.loadVisibleEntityDegrees(scope, snapshot, entityIds);
    }

    @Override
    public Map<UUID, Double> loadVisibleRelationWeights(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> relationIds) {
        return graph.loadVisibleRelationWeights(scope, snapshot, relationIds);
    }

    @Override
    public List<Chunk> loadChunks(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> chunkIds) {
        List<String> ids = Objects.requireNonNull(chunkIds, "chunkIds").stream()
                .map(UUID::toString)
                .toList();
        return content.get(scope, snapshot, ids).stream()
                .filter(record -> record.kind() == ContentStore.ContentKind.CHUNK)
                .map(record -> chunk(record, snapshot))
                .toList();
    }

    private List<VectorIndex.VectorHit> vectorHits(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            VectorSearch search,
            VectorIndex.VectorKind kind,
            Set<String> candidates) {
        return vectors.search(
                scope,
                snapshot,
                new VectorIndex.SearchRequest(
                        search.embeddingProfileId(),
                        Set.of(kind),
                        candidates,
                        search.vector(),
                        search.limit(),
                        search.minimumSimilarity()));
    }

    private List<RankedItem<Chunk>> chunks(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            List<VectorIndex.VectorHit> hits) {
        Map<String, Double> scores = hits.stream().collect(Collectors.toMap(
                VectorIndex.VectorHit::subjectId,
                VectorIndex.VectorHit::similarity,
                Math::max,
                LinkedHashMap::new));
        return content.get(scope, snapshot, scores.keySet()).stream()
                .filter(record -> record.kind() == ContentStore.ContentKind.CHUNK)
                .filter(record -> scores.containsKey(record.id()))
                .map(record -> new RankedItem<>(
                        record.id(),
                        chunk(record, snapshot),
                        scores.get(record.id())))
                .sorted(rankedOrder())
                .toList();
    }

    private static Chunk chunk(
            ContentStore.ContentRecord record,
            ProjectionSnapshot snapshot) {
        return new Chunk(
                record.evidence().chunkId(),
                record.evidence(),
                snapshot.generation(),
                record.content(),
                record.tokenCount(),
                record.metadata());
    }

    private static Map<UUID, Double> subjectScores(
            List<VectorIndex.VectorHit> hits) {
        return hits.stream().collect(Collectors.toMap(
                hit -> UUID.fromString(hit.subjectId()),
                VectorIndex.VectorHit::similarity,
                Math::max,
                LinkedHashMap::new));
    }

    private static <T> Comparator<RankedItem<T>> rankedOrder() {
        return Comparator
                .comparingDouble((RankedItem<T> item) -> item.score())
                .reversed()
                .thenComparing(RankedItem::stableKey);
    }
}
