package com.orgmemory.graphrag.opensearch;

import com.orgmemory.graphrag.authorization.AuthorizedEvidenceScope;
import com.orgmemory.graphrag.storage.ProjectionBatch;
import com.orgmemory.graphrag.storage.ProjectionKind;
import com.orgmemory.graphrag.storage.ProjectionSnapshot;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;

final class OpenSearchStagedIndex {

    private final OpenSearchOperations operations;
    private final OpenSearchProjectionPublicationStore publications;
    private final String controlIndex;
    private final Function<ProjectionBatch, String> batchIndex;
    private final Function<ProjectionSnapshot, String> snapshotIndex;
    private final ProjectionKind kind;
    private final String logicalCopyUnit;
    private final OpenSearchScanner scanner;
    private final OpenSearchCopyForwardCoordinator copyForward;

    OpenSearchStagedIndex(
            OpenSearchOperations operations,
            OpenSearchProjectionPublicationStore publications,
            String controlIndex,
            String index,
            ProjectionKind kind,
            String logicalCopyUnit,
            OpenSearchCopyForwardCoordinator copyForward) {
        this(
                operations,
                publications,
                controlIndex,
                ignored -> index,
                ignored -> index,
                kind,
                logicalCopyUnit,
                copyForward);
    }

    OpenSearchStagedIndex(
            OpenSearchOperations operations,
            OpenSearchProjectionPublicationStore publications,
            String controlIndex,
            Function<ProjectionBatch, String> batchIndex,
            Function<ProjectionSnapshot, String> snapshotIndex,
            ProjectionKind kind,
            String logicalCopyUnit,
            OpenSearchCopyForwardCoordinator copyForward) {
        this.operations = Objects.requireNonNull(operations, "operations");
        this.publications = Objects.requireNonNull(publications, "publications");
        this.controlIndex = Objects.requireNonNull(controlIndex, "controlIndex");
        this.batchIndex = Objects.requireNonNull(batchIndex, "batchIndex");
        this.snapshotIndex = Objects.requireNonNull(snapshotIndex, "snapshotIndex");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.logicalCopyUnit = Objects.requireNonNull(logicalCopyUnit, "logicalCopyUnit");
        this.scanner = new OpenSearchScanner(operations);
        this.copyForward = Objects.requireNonNull(copyForward, "copyForward");
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

    List<Map<String, Object>> searchSortedAfter(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<Query> additionalFilters,
            String sortField,
            String afterExclusive,
            int limit) {
        publications.requireReadable(snapshot, kind);
        if (scope.authorizedAssetIds().isEmpty()) {
            return List.of();
        }
        try {
            var request = new org.opensearch.client.opensearch.core.SearchRequest.Builder()
                    .index(snapshotIndex.apply(snapshot))
                    .size(limit)
                    .query(authorizedQuery(scope, snapshot, additionalFilters))
                    .collapse(collapse -> collapse.field(sortField))
                    .sort(sort -> sort.field(field -> field
                            .field(sortField)
                            .order(SortOrder.Asc)));
            if (afterExclusive != null) {
                request.searchAfter(List.of(FieldValue.of(afterExclusive)));
            }
            return operations.client().search(request.build(), Map.class)
                    .hits()
                    .hits()
                    .stream()
                    .filter(hit -> hit.source() != null)
                    .map(hit -> {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> source =
                                (Map<String, Object>) hit.source();
                        return Map.copyOf(source);
                    })
                    .toList();
        } catch (IOException | OpenSearchException exception) {
            throw new OpenSearchProjectionException(
                    "OpenSearch failed to read a stable authorized page",
                    exception);
        }
    }

    void discard(ProjectionBatch batch) {
        discardDocuments(batch);
        discardMarker(batch);
    }

    private void discardDocuments(ProjectionBatch batch) {
        Query query = batchQuery(batch);
        String index = batchIndex.apply(batch);
        copyForward.stream(
                index,
                query,
                hit -> copyForward.deleteOperation(index, hit.id()));
    }

    void discardMarker(ProjectionBatch batch) {
        Objects.requireNonNull(batch, "batch");
        operations.deleteIfExists(
                controlIndex,
                copyForward.markerId(batch, copyUnit(batch)));
    }

    private void ensureCopyForward(ProjectionBatch batch) {
        copyForward.copyForward(
                batch,
                copyUnit(batch),
                () -> copyPreviousGeneration(batch),
                () -> discardDocuments(batch));
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
        String target = batchIndex.apply(batch);
        copyForward.stream(
                snapshotIndex.apply(previousSnapshot),
                snapshotQuery(previousSnapshot),
                hit -> {
                    Map<String, Object> document = new LinkedHashMap<>(hit.source());
                    document.put(OpenSearchProjectionCodec.BATCH_ID, batch.id().toString());
                    document.put(OpenSearchProjectionCodec.GENERATION, batch.generation());
                    String recordId = document.get(OpenSearchProjectionCodec.RECORD_ID).toString();
                    return copyForward.indexOperation(
                            target,
                            physicalId(batch.id(), recordId),
                            Map.copyOf(document));
                });
    }

    private List<Map<String, Object>> scan(
            String index,
            Query query,
            int limit) {
        return scanner.scan(index, query, limit).stream()
                .map(OpenSearchScanner.StoredHit::source)
                .toList();
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
        return OpenSearchStoreSupport.term(field, value);
    }

    static Query term(String field, long value) {
        return OpenSearchStoreSupport.term(field, value);
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

    private OpenSearchCopyForwardCoordinator.CopyUnit copyUnit(ProjectionBatch batch) {
        return new OpenSearchCopyForwardCoordinator.CopyUnit(
                kind,
                logicalCopyUnit,
                batchIndex.apply(batch));
    }
}
