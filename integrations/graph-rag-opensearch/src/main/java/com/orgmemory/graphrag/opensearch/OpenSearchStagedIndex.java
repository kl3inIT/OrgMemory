package com.orgmemory.graphrag.opensearch;

import com.orgmemory.graphrag.authorization.AuthorizedEvidenceScope;
import com.orgmemory.graphrag.storage.ProjectionBatch;
import com.orgmemory.graphrag.storage.ProjectionKind;
import com.orgmemory.graphrag.storage.ProjectionSnapshot;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.Time;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.DeletePitRequest;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.Pit;

final class OpenSearchStagedIndex {

    private static final int SCAN_PAGE_SIZE = 500;
    private static final ConcurrentHashMap<String, ReentrantLock> COPY_LOCKS =
            new ConcurrentHashMap<>();

    private final OpenSearchOperations operations;
    private final OpenSearchProjectionPublicationStore publications;
    private final String controlIndex;
    private final Function<ProjectionBatch, String> batchIndex;
    private final Function<ProjectionSnapshot, String> snapshotIndex;
    private final ProjectionKind kind;

    OpenSearchStagedIndex(
            OpenSearchOperations operations,
            OpenSearchProjectionPublicationStore publications,
            String controlIndex,
            String index,
            ProjectionKind kind) {
        this(
                operations,
                publications,
                controlIndex,
                ignored -> index,
                ignored -> index,
                kind);
    }

    OpenSearchStagedIndex(
            OpenSearchOperations operations,
            OpenSearchProjectionPublicationStore publications,
            String controlIndex,
            Function<ProjectionBatch, String> batchIndex,
            Function<ProjectionSnapshot, String> snapshotIndex,
            ProjectionKind kind) {
        this.operations = Objects.requireNonNull(operations, "operations");
        this.publications = Objects.requireNonNull(publications, "publications");
        this.controlIndex = Objects.requireNonNull(controlIndex, "controlIndex");
        this.batchIndex = Objects.requireNonNull(batchIndex, "batchIndex");
        this.snapshotIndex = Objects.requireNonNull(snapshotIndex, "snapshotIndex");
        this.kind = Objects.requireNonNull(kind, "kind");
    }

    void stageUpsert(
            ProjectionBatch batch,
            Collection<Map<String, Object>> documents) {
        Objects.requireNonNull(batch, "batch");
        Objects.requireNonNull(documents, "documents");
        ensureCopyForward(batch);
        String index = batchIndex.apply(batch);
        List<BulkOperation> operations = documents.stream()
                .map(document -> BulkOperation.of(operation -> operation.index(indexing -> indexing
                        .index(index)
                        .id(physicalId(batch.id(), document
                                .get(OpenSearchProjectionCodec.RECORD_ID)
                                .toString()))
                        .document(document))))
                .toList();
        this.operations.bulk(operations);
    }

    void stageDelete(
            ProjectionBatch batch,
            Collection<String> recordIds) {
        Objects.requireNonNull(batch, "batch");
        Objects.requireNonNull(recordIds, "recordIds");
        ensureCopyForward(batch);
        String index = batchIndex.apply(batch);
        List<BulkOperation> operations = recordIds.stream()
                .map(recordId -> BulkOperation.of(operation -> operation.delete(deleting -> deleting
                        .index(index)
                        .id(physicalId(batch.id(), recordId)))))
                .toList();
        this.operations.bulk(operations);
    }

    void stageDeleteMatching(
            ProjectionBatch batch,
            Collection<Query> additionalFilters) {
        Objects.requireNonNull(batch, "batch");
        Objects.requireNonNull(additionalFilters, "additionalFilters");
        ensureCopyForward(batch);
        String index = batchIndex.apply(batch);
        List<Query> filters = new ArrayList<>();
        filters.add(batchQuery(batch));
        filters.addAll(additionalFilters);
        Query query = Query.of(candidate -> candidate.bool(bool -> bool.filter(filters)));
        List<BulkOperation> deletes = scan(index, query, Integer.MAX_VALUE).stream()
                .map(document -> BulkOperation.of(operation -> operation.delete(deleting -> deleting
                        .index(index)
                        .id(physicalId(
                                batch.id(),
                                document.get(OpenSearchProjectionCodec.RECORD_ID)
                                        .toString())))))
                .toList();
        operations.bulk(deletes);
    }

    List<Map<String, Object>> load(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<String> recordIds) {
        Objects.requireNonNull(recordIds, "recordIds");
        if (recordIds.isEmpty() || scope.authorizedAssetIds().isEmpty()) {
            return List.of();
        }
        publications.requireReadable(snapshot, kind);
        String index = snapshotIndex.apply(snapshot);
        Query query = authorizedQuery(
                scope,
                snapshot,
                List.of(terms(
                        OpenSearchProjectionCodec.RECORD_ID,
                        recordIds)));
        return scan(index, query, Math.max(recordIds.size(), 1_000));
    }

    List<Map<String, Object>> search(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<Query> additionalFilters,
            int limit) {
        if (scope.authorizedAssetIds().isEmpty()) {
            return List.of();
        }
        publications.requireReadable(snapshot, kind);
        return scan(
                snapshotIndex.apply(snapshot),
                authorizedQuery(scope, snapshot, additionalFilters),
                limit);
    }

    void discard(ProjectionBatch batch) {
        Query query = batchQuery(batch);
        String index = batchIndex.apply(batch);
        List<Map<String, Object>> documents = scan(index, query, Integer.MAX_VALUE);
        List<BulkOperation> deletes = documents.stream()
                .map(document -> BulkOperation.of(operation -> operation.delete(deleting -> deleting
                        .index(index)
                        .id(physicalId(
                                batch.id(),
                                document.get(OpenSearchProjectionCodec.RECORD_ID)
                                        .toString())))))
                .toList();
        operations.bulk(deletes);
        discardMarker(batch);
    }

    void discardMarker(ProjectionBatch batch) {
        Objects.requireNonNull(batch, "batch");
        operations.deleteIfExists(controlIndex, copyMarkerId(batch));
    }

    private void ensureCopyForward(ProjectionBatch batch) {
        String markerId = copyMarkerId(batch);
        OpenSearchOperations.VersionedDocument marker =
                operations.get(controlIndex, markerId);
        if (ready(marker)) {
            return;
        }
        ReentrantLock lock = COPY_LOCKS.computeIfAbsent(markerId, ignored -> new ReentrantLock());
        lock.lock();
        try {
            marker = operations.get(controlIndex, markerId);
            if (ready(marker)) {
                return;
            }
            String owner = UUID.randomUUID().toString();
            Map<String, Object> copying = OpenSearchProjectionCodec.batch(batch, "PREPARING");
            copying.put("document_kind", "COPY_FORWARD");
            copying.put("projection_kind", kind.name());
            copying.put("target_index", batchIndex.apply(batch));
            copying.put("copy_status", "COPYING");
            copying.put("copy_owner", owner);
            copying.put("copy_started_at", Instant.now().toString());
            if (marker == null) {
                if (!operations.create(controlIndex, markerId, copying)) {
                    marker = operations.get(controlIndex, markerId);
                    if (ready(marker)) {
                        return;
                    }
                    throw new OpenSearchProjectionException(
                            "another process is preparing " + markerId);
                }
            } else {
                if (!operations.compareAndSet(
                        controlIndex,
                        markerId,
                        marker,
                        copying)) {
                    throw new OpenSearchProjectionException(
                            "another process is preparing " + markerId);
                }
            }

            copyPreviousGeneration(batch);
            OpenSearchOperations.VersionedDocument owned =
                    operations.get(controlIndex, markerId);
            if (owned == null
                    || !owner.equals(owned.source().get("copy_owner"))) {
                throw new OpenSearchProjectionException(
                        "copy-forward ownership changed for " + markerId);
            }
            Map<String, Object> ready = new LinkedHashMap<>(owned.source());
            ready.put("copy_status", "READY");
            ready.put("copy_completed_at", Instant.now().toString());
            if (!operations.compareAndSet(
                    controlIndex,
                    markerId,
                    owned,
                    ready)) {
                throw new OpenSearchProjectionException(
                        "could not complete copy-forward marker " + markerId);
            }
        } finally {
            lock.unlock();
            COPY_LOCKS.remove(markerId, lock);
        }
    }

    private void copyPreviousGeneration(ProjectionBatch batch) {
        UUID previousBatch = publications.previousBatchId(batch).orElse(null);
        if (previousBatch == null) {
            return;
        }
        ProjectionSnapshot previousSnapshot = publications
                .published(batch.namespace(), batch.expectedPreviousGeneration())
                .orElseThrow(() -> new OpenSearchProjectionException(
                        "previous publication snapshot is missing"));
        if (!previousSnapshot.projections().contains(kind)) {
            return;
        }
        List<Map<String, Object>> previous = scan(
                snapshotIndex.apply(previousSnapshot),
                snapshotQuery(previousSnapshot),
                Integer.MAX_VALUE);
        List<Map<String, Object>> copies = previous.stream()
                .map(source -> {
                    Map<String, Object> copy = new LinkedHashMap<>(source);
                    copy.put(OpenSearchProjectionCodec.BATCH_ID, batch.id().toString());
                    copy.put(OpenSearchProjectionCodec.GENERATION, batch.generation());
                    return Map.copyOf(copy);
                })
                .toList();
        stageCopiedDocuments(batch, copies);
    }

    private void stageCopiedDocuments(
            ProjectionBatch batch,
            List<Map<String, Object>> documents) {
        List<BulkOperation> writes = documents.stream()
                .map(document -> BulkOperation.of(operation -> operation.index(indexing -> indexing
                        .index(batchIndex.apply(batch))
                        .id(physicalId(
                                batch.id(),
                                document.get(OpenSearchProjectionCodec.RECORD_ID)
                                        .toString()))
                        .document(document))))
                .toList();
        operations.bulk(writes);
    }

    private List<Map<String, Object>> scan(
            String index,
            Query query,
            int limit) {
        if (limit <= 0) {
            return List.of();
        }
        String pitId = null;
        try {
            pitId = operations.client()
                    .createPit(request -> request
                            .index(List.of(index))
                            .keepAlive(Time.of(time -> time.time("1m"))))
                    .pitId();
            String activePitId = pitId;
            List<Map<String, Object>> result = new ArrayList<>();
            List<FieldValue> searchAfter = List.of();
            while (result.size() < limit) {
                int pageSize = Math.min(SCAN_PAGE_SIZE, limit - result.size());
                var request = new org.opensearch.client.opensearch.core.SearchRequest.Builder()
                        .size(pageSize)
                        .query(query)
                        .pit(Pit.of(pit -> pit.id(activePitId).keepAlive("1m")))
                        .sort(sort -> sort.field(field -> field
                                .field("_shard_doc")
                                .order(SortOrder.Asc)));
                if (!searchAfter.isEmpty()) {
                    request.searchAfter(searchAfter);
                }
                var response = operations.client().search(request.build(), Map.class);
                List<Hit<Map>> hits = response.hits().hits();
                if (hits.isEmpty()) {
                    break;
                }
                for (Hit<Map> hit : hits) {
                    if (hit.source() != null) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> source =
                                (Map<String, Object>) hit.source();
                        result.add(Map.copyOf(source));
                    }
                }
                searchAfter = hits.getLast().sort();
                if (hits.size() < pageSize) {
                    break;
                }
            }
            return List.copyOf(result);
        } catch (IOException | OpenSearchException exception) {
            throw new OpenSearchProjectionException(
                    "OpenSearch failed to scan staged index " + index,
                    exception);
        } finally {
            if (pitId != null) {
                try {
                    operations.client().deletePit(
                            new DeletePitRequest.Builder()
                                    .pitId(List.of(pitId))
                                    .build());
                } catch (Exception ignored) {
                    // The PIT expires automatically. Query correctness is already decided.
                }
            }
        }
    }

    private Query authorizedQuery(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<Query> additionalFilters) {
        if (!scope.organizationId().equals(snapshot.namespace().organizationId())) {
            return Query.of(query -> query.matchNone(matchNone -> matchNone));
        }
        List<Query> filters = new ArrayList<>();
        filters.addAll(snapshotFilters(snapshot));
        filters.add(OpenSearchStoreSupport.anyTerms(
                OpenSearchProjectionCodec.ASSET_ID,
                scope.authorizedAssetIds().stream().map(UUID::toString).toList()));
        filters.addAll(additionalFilters);
        return Query.of(query -> query.bool(bool -> bool.filter(filters)));
    }

    private Query batchQuery(ProjectionBatch batch) {
        return Query.of(query -> query.bool(bool -> bool.filter(List.of(
                term(
                        OpenSearchProjectionCodec.ORGANIZATION_ID,
                        batch.namespace().organizationId().toString()),
                term(OpenSearchProjectionCodec.WORKSPACE, batch.namespace().workspace()),
                term(OpenSearchProjectionCodec.COLLECTION, batch.namespace().collection()),
                term(OpenSearchProjectionCodec.BATCH_ID, batch.id().toString())))));
    }

    private Query snapshotQuery(ProjectionSnapshot snapshot) {
        return Query.of(query -> query.bool(bool -> bool.filter(snapshotFilters(snapshot))));
    }

    private static List<Query> snapshotFilters(ProjectionSnapshot snapshot) {
        return List.of(
                term(
                        OpenSearchProjectionCodec.ORGANIZATION_ID,
                        snapshot.namespace().organizationId().toString()),
                term(
                        OpenSearchProjectionCodec.WORKSPACE,
                        snapshot.namespace().workspace()),
                term(
                        OpenSearchProjectionCodec.COLLECTION,
                        snapshot.namespace().collection()),
                term(
                        OpenSearchProjectionCodec.BATCH_ID,
                        snapshot.batchId().toString()),
                term(
                        OpenSearchProjectionCodec.GENERATION,
                        snapshot.generation()));
    }

    static Query term(String field, String value) {
        return Query.of(query -> query.term(term -> term
                .field(field)
                .value(FieldValue.of(value))));
    }

    static Query term(String field, long value) {
        return Query.of(query -> query.term(term -> term
                .field(field)
                .value(FieldValue.of(value))));
    }

    static Query terms(String field, Collection<String> values) {
        List<FieldValue> encoded = values.stream().map(FieldValue::of).toList();
        return Query.of(query -> query.terms(terms -> terms
                .field(field)
                .terms(termsField -> termsField.value(encoded))));
    }

    static String physicalId(UUID batchId, String recordId) {
        return batchId + ":" + recordId;
    }

    private String copyMarkerId(ProjectionBatch batch) {
        return "copy:"
                + batch.id()
                + ":"
                + kind.name()
                + ":"
                + Integer.toUnsignedString(batchIndex.apply(batch).hashCode(), 36);
    }

    private static boolean ready(OpenSearchOperations.VersionedDocument marker) {
        return marker != null && "READY".equals(marker.source().get("copy_status"));
    }
}
