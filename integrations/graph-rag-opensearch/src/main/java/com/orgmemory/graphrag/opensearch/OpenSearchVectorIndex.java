package com.orgmemory.graphrag.opensearch;

import com.orgmemory.graphrag.authorization.AuthorizedEvidenceScope;
import com.orgmemory.graphrag.storage.ProjectionBatch;
import com.orgmemory.graphrag.storage.ProjectionKind;
import com.orgmemory.graphrag.storage.ProjectionSnapshot;
import com.orgmemory.graphrag.storage.VectorIndex;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.search.Hit;

public final class OpenSearchVectorIndex implements VectorIndex {

    private static final ConcurrentHashMap<String, ReentrantLock> COPY_LOCKS =
            new ConcurrentHashMap<>();

    private final OpenSearchOperations operations;
    private final OpenSearchProjectionPublicationStore publications;
    private final OpenSearchIndexNames indexes;
    private final OpenSearchScanner scanner;

    OpenSearchVectorIndex(
            OpenSearchOperations operations,
            OpenSearchProjectionPublicationStore publications,
            OpenSearchIndexNames indexes) {
        this.operations = Objects.requireNonNull(operations, "operations");
        this.publications = Objects.requireNonNull(publications, "publications");
        this.indexes = Objects.requireNonNull(indexes, "indexes");
        this.scanner = new OpenSearchScanner(operations);
    }

    @Override
    public void stageUpsert(
            ProjectionBatch batch,
            Collection<VectorRecord> records) {
        List<VectorRecord> immutable =
                List.copyOf(Objects.requireNonNull(records, "records"));
        immutable.forEach(record -> OpenSearchStoreSupport.requireSameOrganization(
                batch, record.evidence()));
        ensureCopyForward(batch);
        Map<String, List<BulkOperation>> byIndex = new LinkedHashMap<>();
        for (VectorRecord record : immutable) {
            String index = indexes.vectors(
                    record.embeddingProfileId(),
                    record.vector().dimensions());
            operations.ensureIndex(index, OpenSearchSchemas.vector(record.vector().dimensions()));
            byIndex.computeIfAbsent(index, ignored -> new ArrayList<>())
                    .add(BulkOperation.of(operation -> operation.index(write -> write
                            .index(index)
                            .id(OpenSearchStagedIndex.physicalId(batch.id(), record.id()))
                            .document(OpenSearchProjectionCodec.vector(batch, record)))));
        }
        byIndex.values().forEach(operations::bulk);
    }

    @Override
    public void stageDelete(
            ProjectionBatch batch,
            Collection<String> ids) {
        List<String> immutable = OpenSearchStoreSupport.requireIds(ids);
        ensureCopyForward(batch);
        if (immutable.isEmpty()) {
            return;
        }
        Query query = Query.of(candidate -> candidate.bool(bool -> bool.filter(List.of(
                OpenSearchStagedIndex.term(
                        OpenSearchProjectionCodec.BATCH_ID,
                        batch.id().toString()),
                OpenSearchStoreSupport.anyTerms(
                        OpenSearchProjectionCodec.RECORD_ID,
                        immutable)))));
        delete(scanner.scan(indexes.vectorPattern(), query, Integer.MAX_VALUE));
    }

    @Override
    public List<VectorRecord> get(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<String> ids) {
        List<String> immutable = OpenSearchStoreSupport.requireIds(ids);
        requireReadable(scope, snapshot);
        if (immutable.isEmpty() || scope.authorizedAssetIds().isEmpty()) {
            return List.of();
        }
        Query query = visibleQuery(
                scope,
                snapshot,
                List.of(OpenSearchStoreSupport.anyTerms(
                        OpenSearchProjectionCodec.RECORD_ID,
                        immutable)));
        return scanner.scan(indexes.vectorPattern(), query, Integer.MAX_VALUE).stream()
                .map(hit -> OpenSearchProjectionCodec.vector(hit.source()))
                .sorted(Comparator.comparing(VectorRecord::id)
                        .thenComparing(VectorRecord::embeddingProfileId))
                .toList();
    }

    @Override
    public List<VectorHit> search(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            SearchRequest request) {
        Objects.requireNonNull(request, "request");
        requireReadable(scope, snapshot);
        if (scope.authorizedAssetIds().isEmpty()) {
            return List.of();
        }
        String index = indexes.vectors(
                request.embeddingProfileId(),
                request.dimensions());
        if (!operations.indexExists(index)) {
            return List.of();
        }
        List<Query> filters = new ArrayList<>();
        filters.add(OpenSearchStoreSupport.anyTerms(
                "vector_kind",
                request.kinds().stream().map(Enum::name).toList()));
        filters.add(OpenSearchStagedIndex.term(
                "embedding_profile_id",
                request.embeddingProfileId().toString()));
        if (!request.candidateIds().isEmpty()) {
            filters.add(OpenSearchStoreSupport.anyTerms(
                    "subject_id",
                    request.candidateIds()));
        }
        Query visibility = visibleQuery(scope, snapshot, filters);
        if (request.limit() > 10_000 || isZero(request.queryVector().copyValues())) {
            return exactSearch(index, visibility, request);
        }
        List<Float> vector = new ArrayList<>(request.dimensions());
        for (float value : request.queryVector().copyValues()) {
            vector.add(value);
        }
        Query query = Query.of(candidate -> candidate.knn(knn -> knn
                .field("vector")
                .vector(vector)
                .k(request.limit())
                .filter(visibility)));
        try {
            var response = operations.client().search(
                    search -> search
                            .index(index)
                            .size(request.limit())
                            .minScore(similarityToScore(request.minimumSimilarity()))
                            .query(query),
                    Map.class);
            List<VectorHit> hits = new ArrayList<>();
            for (Hit<Map> hit : response.hits().hits()) {
                if (hit.source() == null || hit.score() == null) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> source = (Map<String, Object>) hit.source();
                VectorRecord record = OpenSearchProjectionCodec.vector(source);
                hits.add(new VectorHit(
                        record.id(),
                        record.subjectId(),
                        record.evidence(),
                        record.kind(),
                        scoreToSimilarity(hit.score())));
            }
            return List.copyOf(hits);
        } catch (IOException | OpenSearchException exception) {
            throw new OpenSearchProjectionException(
                    "OpenSearch failed to execute vector search",
                    exception);
        }
    }

    @Override
    public void discard(ProjectionBatch batch) {
        Objects.requireNonNull(batch, "batch");
        Query query = Query.of(candidate -> candidate.bool(bool -> bool.filter(
                OpenSearchStagedIndex.term(
                        OpenSearchProjectionCodec.BATCH_ID,
                        batch.id().toString()))));
        delete(scanner.scan(indexes.vectorPattern(), query, Integer.MAX_VALUE));
        operations.deleteIfExists(indexes.control(), copyMarkerId(batch));
    }

    private void ensureCopyForward(ProjectionBatch batch) {
        String markerId = copyMarkerId(batch);
        OpenSearchOperations.VersionedDocument marker =
                operations.get(indexes.control(), markerId);
        if (ready(marker)) {
            return;
        }
        ReentrantLock lock =
                COPY_LOCKS.computeIfAbsent(markerId, ignored -> new ReentrantLock());
        lock.lock();
        try {
            marker = operations.get(indexes.control(), markerId);
            if (ready(marker)) {
                return;
            }
            String owner = UUID.randomUUID().toString();
            Map<String, Object> copying = OpenSearchProjectionCodec.batch(batch, "PREPARING");
            copying.put("document_kind", "COPY_FORWARD");
            copying.put("projection_kind", ProjectionKind.VECTOR.name());
            copying.put("copy_status", "COPYING");
            copying.put("copy_owner", owner);
            copying.put("copy_started_at", Instant.now().toString());
            if (marker == null) {
                if (!operations.create(indexes.control(), markerId, copying)) {
                    throw new OpenSearchProjectionException(
                            "another process is preparing " + markerId);
                }
            } else if (!operations.compareAndSet(
                    indexes.control(), markerId, marker, copying)) {
                throw new OpenSearchProjectionException(
                        "another process is preparing " + markerId);
            }
            copyPrevious(batch);
            OpenSearchOperations.VersionedDocument owned =
                    operations.get(indexes.control(), markerId);
            if (owned == null || !owner.equals(owned.source().get("copy_owner"))) {
                throw new OpenSearchProjectionException(
                        "copy-forward ownership changed for " + markerId);
            }
            Map<String, Object> completed = new LinkedHashMap<>(owned.source());
            completed.put("copy_status", "READY");
            completed.put("copy_completed_at", Instant.now().toString());
            if (!operations.compareAndSet(
                    indexes.control(), markerId, owned, completed)) {
                throw new OpenSearchProjectionException(
                        "could not complete copy-forward marker " + markerId);
            }
        } finally {
            lock.unlock();
            COPY_LOCKS.remove(markerId, lock);
        }
    }

    private void copyPrevious(ProjectionBatch batch) {
        if (batch.expectedPreviousGeneration() == 0) {
            return;
        }
        ProjectionSnapshot previous = publications
                .published(batch.namespace(), batch.expectedPreviousGeneration())
                .orElseThrow(() -> new OpenSearchProjectionException(
                        "previous publication snapshot is missing"));
        if (!previous.projections().contains(ProjectionKind.VECTOR)) {
            return;
        }
        Query query = Query.of(candidate -> candidate.bool(bool -> bool.filter(List.of(
                OpenSearchStagedIndex.term(
                        OpenSearchProjectionCodec.ORGANIZATION_ID,
                        previous.namespace().organizationId().toString()),
                OpenSearchStagedIndex.term(
                        OpenSearchProjectionCodec.BATCH_ID,
                        previous.batchId().toString()),
                OpenSearchStagedIndex.term(
                        OpenSearchProjectionCodec.GENERATION,
                        previous.generation())))));
        Map<String, List<BulkOperation>> byIndex = new LinkedHashMap<>();
        for (OpenSearchScanner.StoredHit hit :
                scanner.scan(indexes.vectorPattern(), query, Integer.MAX_VALUE)) {
            Map<String, Object> document = new LinkedHashMap<>(hit.source());
            document.put(OpenSearchProjectionCodec.BATCH_ID, batch.id().toString());
            document.put(OpenSearchProjectionCodec.GENERATION, batch.generation());
            String recordId = document.get(OpenSearchProjectionCodec.RECORD_ID).toString();
            byIndex.computeIfAbsent(hit.index(), ignored -> new ArrayList<>())
                    .add(BulkOperation.of(operation -> operation.index(write -> write
                            .index(hit.index())
                            .id(OpenSearchStagedIndex.physicalId(batch.id(), recordId))
                            .document(Map.copyOf(document)))));
        }
        byIndex.values().forEach(operations::bulk);
    }

    private void requireReadable(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(snapshot, "snapshot");
        if (!scope.organizationId().equals(snapshot.namespace().organizationId())) {
            throw new IllegalArgumentException(
                    "authorization scope and snapshot must share an organization");
        }
        publications.requireReadable(snapshot, ProjectionKind.VECTOR);
    }

    private static Query visibleQuery(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<Query> additionalFilters) {
        List<Query> filters = new ArrayList<>(List.of(
                OpenSearchStagedIndex.term(
                        OpenSearchProjectionCodec.ORGANIZATION_ID,
                        scope.organizationId().toString()),
                OpenSearchStagedIndex.term(
                        OpenSearchProjectionCodec.BATCH_ID,
                        snapshot.batchId().toString()),
                OpenSearchStagedIndex.term(
                        OpenSearchProjectionCodec.GENERATION,
                        snapshot.generation()),
                OpenSearchStoreSupport.anyTerms(
                        OpenSearchProjectionCodec.ASSET_ID,
                        scope.authorizedAssetIds().stream()
                                .map(UUID::toString)
                                .toList())));
        filters.addAll(additionalFilters);
        return Query.of(query -> query.bool(bool -> bool.filter(filters)));
    }

    private void delete(Collection<OpenSearchScanner.StoredHit> hits) {
        operations.bulk(hits.stream()
                .map(hit -> BulkOperation.of(operation -> operation.delete(delete -> delete
                        .index(hit.index())
                        .id(hit.id()))))
                .toList());
    }

    private List<VectorHit> exactSearch(
            String index,
            Query visibility,
            SearchRequest request) {
        return scanner.scan(index, visibility, Integer.MAX_VALUE).stream()
                .map(hit -> OpenSearchProjectionCodec.vector(hit.source()))
                .map(record -> new VectorHit(
                        record.id(),
                        record.subjectId(),
                        record.evidence(),
                        record.kind(),
                        cosine(record.vector().copyValues(), request.queryVector().copyValues())))
                .filter(hit -> hit.similarity() >= request.minimumSimilarity())
                .sorted(Comparator.comparingDouble(VectorHit::similarity)
                        .reversed()
                        .thenComparing(VectorHit::id))
                .limit(request.limit())
                .toList();
    }

    private String copyMarkerId(ProjectionBatch batch) {
        return "copy:" + batch.id() + ":" + ProjectionKind.VECTOR.name();
    }

    private static boolean ready(OpenSearchOperations.VersionedDocument marker) {
        return marker != null && "READY".equals(marker.source().get("copy_status"));
    }

    private static double similarityToScore(double similarity) {
        return (similarity + 1.0) / 2.0;
    }

    private static double scoreToSimilarity(double score) {
        return Math.max(-1.0, Math.min(1.0, (score * 2.0) - 1.0));
    }

    private static double cosine(
            float[] left,
            float[] right) {
        if (left.length != right.length) {
            throw new IllegalArgumentException("vector dimensions must match");
        }
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int index = 0; index < left.length; index++) {
            dot += left[index] * right[index];
            leftNorm += left[index] * left[index];
            rightNorm += right[index] * right[index];
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return 0;
        }
        return Math.max(
                -1.0,
                Math.min(1.0, dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm))));
    }

    private static boolean isZero(float[] vector) {
        for (float value : vector) {
            if (value != 0) {
                return false;
            }
        }
        return true;
    }
}
