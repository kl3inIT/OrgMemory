package com.orgmemory.graphrag.opensearch;

import com.orgmemory.graphrag.authorization.AuthorizedEvidenceScope;
import com.orgmemory.graphrag.storage.LexicalIndex;
import com.orgmemory.graphrag.storage.ProjectionBatch;
import com.orgmemory.graphrag.storage.ProjectionDiscardPermit;
import com.orgmemory.graphrag.storage.ProjectionKind;
import com.orgmemory.graphrag.storage.ProjectionSnapshot;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.Time;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.DeletePitRequest;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.Pit;

/**
 * BM25 adapter with one immutable physical index per publication batch.
 *
 * <p>OpenSearch computes inverse-document frequency at physical-index scope.
 * Isolating a batch prevents unpublished generations and other tenants from
 * changing the score distribution of a pinned snapshot.
 */
public final class OpenSearchLexicalIndex implements LexicalIndex {

    private static final int SEARCH_PAGE_SIZE = 500;
    private final OpenSearchOperations operations;
    private final OpenSearchProjectionPublicationStore publications;
    private final OpenSearchIndexNames indexes;
    private final OpenSearchScanner scanner;
    private final OpenSearchCopyForwardCoordinator copyForward;

    OpenSearchLexicalIndex(
            OpenSearchOperations operations,
            OpenSearchProjectionPublicationStore publications,
            OpenSearchIndexNames indexes,
            OpenSearchCopyForwardCoordinator copyForward) {
        this.operations = Objects.requireNonNull(operations, "operations");
        this.publications = Objects.requireNonNull(publications, "publications");
        this.indexes = Objects.requireNonNull(indexes, "indexes");
        this.scanner = new OpenSearchScanner(operations);
        this.copyForward = Objects.requireNonNull(copyForward, "copyForward");
    }

    @Override
    public void stageUpsert(
            ProjectionBatch batch,
            Collection<LexicalDocument> documents) {
        List<LexicalDocument> immutable =
                List.copyOf(Objects.requireNonNull(documents, "documents"));
        immutable.forEach(document -> OpenSearchStoreSupport.requireSameOrganization(
                batch, document.evidence()));
        ensureCopyForward(batch);
        String index = indexes.lexical(batch.id());
        operations.bulk(immutable.stream()
                .map(document -> BulkOperation.of(operation -> operation.index(write -> write
                        .index(index)
                        .id(document.id())
                        .document(OpenSearchProjectionCodec.lexical(batch, document)))))
                .toList());
    }

    @Override
    public void stageDelete(
            ProjectionBatch batch,
            Collection<String> ids) {
        List<String> immutable = OpenSearchStoreSupport.requireIds(ids);
        ensureCopyForward(batch);
        String index = indexes.lexical(batch.id());
        operations.bulk(immutable.stream()
                .map(id -> BulkOperation.of(operation -> operation.delete(delete -> delete
                        .index(index)
                        .id(id))))
                .toList());
    }

    @Override
    public void stageDeleteAsset(
            ProjectionBatch batch,
            UUID knowledgeAssetId) {
        UUID assetId = Objects.requireNonNull(knowledgeAssetId, "knowledgeAssetId");
        ensureCopyForward(batch);
        String index = indexes.lexical(batch.id());
        Query query = Query.of(candidate -> candidate.bool(bool -> bool.filter(List.of(
                OpenSearchStagedIndex.term(
                        OpenSearchProjectionCodec.BATCH_ID,
                        batch.id().toString()),
                OpenSearchStagedIndex.term(
                        OpenSearchProjectionCodec.ASSET_ID,
                        assetId.toString())))));
        operations.bulk(scanner.scan(index, query, Integer.MAX_VALUE).stream()
                .map(hit -> BulkOperation.of(operation -> operation.delete(delete -> delete
                        .index(hit.index())
                        .id(hit.id()))))
                .toList());
    }

    @Override
    public SearchPage search(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            SearchRequest request) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(request, "request");
        publications.requireReadable(snapshot, ProjectionKind.LEXICAL);
        if (!scope.organizationId().equals(snapshot.namespace().organizationId())) {
            throw new IllegalArgumentException(
                    "authorization scope and snapshot must share an organization");
        }
        if (scope.authorizedAssetIds().isEmpty()) {
            return new SearchPage(List.of(), null);
        }
        String index = indexes.lexical(snapshot.batchId());
        if (!operations.indexExists(index)) {
            throw new OpenSearchProjectionException(
                    "published lexical index is missing: " + index);
        }
        Cursor cursor = decodeCursor(request.cursor());
        Query textQuery = request.fields().isEmpty()
                ? Query.of(query -> query.match(match -> match
                        .field("search_text")
                        .query(FieldValue.of(request.query()))))
                : Query.of(query -> query.nested(nested -> nested
                        .path("search_fields")
                        .query(candidate -> candidate.bool(bool -> bool
                                .filter(OpenSearchStoreSupport.anyTerms(
                                        "search_fields.name",
                                        request.fields()))
                                .must(must -> must.match(match -> match
                                        .field("search_fields.value")
                                        .query(FieldValue.of(request.query()))))))));
        List<Query> visibilityFilters = new ArrayList<>(List.of(
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
        if (scope.exactEvidenceRestricted()) {
            visibilityFilters.add(OpenSearchStoreSupport.anyTerms(
                    OpenSearchProjectionCodec.REVISION_ID,
                    scope.selectedSourceRevisionIds().stream()
                            .map(UUID::toString)
                            .toList()));
        }
        Query visibility = Query.of(query -> query.bool(bool -> bool
                .filter(visibilityFilters)
                .must(textQuery)));
        String pitId = null;
        try {
            pitId = operations.client()
                    .createPit(create -> create
                            .index(List.of(index))
                            .keepAlive(Time.of(time -> time.time("1m"))))
                    .pitId();
            String activePitId = pitId;
            int target = request.limit() == Integer.MAX_VALUE
                    ? Integer.MAX_VALUE
                    : request.limit() + 1;
            List<SearchHit> hits = new ArrayList<>();
            List<FieldValue> searchAfter = cursor == null
                    ? List.of()
                    : List.of(
                            FieldValue.of(cursor.score()),
                            FieldValue.of(cursor.id()));
            while (hits.size() < target) {
                int size = Math.min(SEARCH_PAGE_SIZE, target - hits.size());
                var builder = new org.opensearch.client.opensearch.core.SearchRequest.Builder()
                        .size(size)
                        .minScore(request.minimumScore())
                        .query(visibility)
                        .pit(Pit.of(pit -> pit.id(activePitId).keepAlive("1m")))
                        .sort(sort -> sort.score(score -> score.order(SortOrder.Desc)))
                        .sort(sort -> sort.field(field -> field
                                .field(OpenSearchProjectionCodec.RECORD_ID)
                                .order(SortOrder.Asc)));
                if (!searchAfter.isEmpty()) {
                    builder.searchAfter(searchAfter);
                }
                var response = operations.client().search(builder.build(), Map.class);
                List<Hit<Map>> openSearchHits = response.hits().hits();
                if (openSearchHits.isEmpty()) {
                    break;
                }
                for (Hit<Map> hit : openSearchHits) {
                    if (hit.source() == null || hit.score() == null) {
                        continue;
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> source = (Map<String, Object>) hit.source();
                    LexicalDocument document = OpenSearchProjectionCodec.lexical(source);
                    hits.add(new SearchHit(
                            document.id(),
                            document.evidence(),
                            hit.score(),
                            Map.of("opensearch_bm25", hit.score())));
                }
                searchAfter = openSearchHits.getLast().sort();
                if (openSearchHits.size() < size) {
                    break;
                }
            }
            if (hits.size() <= request.limit()) {
                return new SearchPage(hits, null);
            }
            List<SearchHit> page = List.copyOf(hits.subList(0, request.limit()));
            SearchHit last = page.getLast();
            return new SearchPage(page, encodeCursor(last.score(), last.id()));
        } catch (IOException | OpenSearchException exception) {
            throw new OpenSearchProjectionException(
                    "OpenSearch failed to execute lexical search",
                    exception);
        } finally {
            if (pitId != null) {
                try {
                    operations.client().deletePit(
                            new DeletePitRequest.Builder()
                                    .pitId(List.of(pitId))
                                    .build());
                } catch (Exception ignored) {
                    // The PIT expires automatically.
                }
            }
        }
    }

    @Override
    public void discard(ProjectionBatch batch, ProjectionDiscardPermit permit) {
        Objects.requireNonNull(batch, "batch");
        Objects.requireNonNull(permit, "permit").requireAuthorizes(batch);
        discardDocuments(batch);
        operations.deleteIfExists(
                indexes.control(),
                copyForward.markerId(batch, copyUnit(batch)));
    }

    private void ensureCopyForward(ProjectionBatch batch) {
        Objects.requireNonNull(batch, "batch");
        copyForward.copyForward(
                batch,
                copyUnit(batch),
                () -> {
                    String target = indexes.lexical(batch.id());
                    operations.ensureIndex(target, OpenSearchSchemas.lexical());
                    copyPrevious(batch, target);
                },
                () -> discardDocuments(batch));
    }

    private void copyPrevious(
            ProjectionBatch batch,
            String target) {
        if (batch.expectedPreviousGeneration() == 0) {
            return;
        }
        ProjectionSnapshot previous = publications
                .published(batch.namespace(), batch.expectedPreviousGeneration())
                .orElseThrow(() -> new OpenSearchProjectionException(
                        "previous publication snapshot is missing"));
        if (!previous.projections().contains(ProjectionKind.LEXICAL)) {
            return;
        }
        String source = indexes.lexical(previous.batchId());
        Query query = Query.of(candidate -> candidate.matchAll(matchAll -> matchAll));
        copyForward.stream(
                source,
                query,
                hit -> {
                    Map<String, Object> document = new LinkedHashMap<>(hit.source());
                    document.put(OpenSearchProjectionCodec.BATCH_ID, batch.id().toString());
                    document.put(OpenSearchProjectionCodec.GENERATION, batch.generation());
                    return copyForward.indexOperation(
                            target,
                            hit.id(),
                            Map.copyOf(document));
                });
    }

    private void discardDocuments(ProjectionBatch batch) {
        operations.deleteIndex(indexes.lexical(batch.id()));
    }

    private OpenSearchCopyForwardCoordinator.CopyUnit copyUnit(ProjectionBatch batch) {
        return new OpenSearchCopyForwardCoordinator.CopyUnit(
                ProjectionKind.LEXICAL,
                ProjectionKind.LEXICAL.name(),
                indexes.lexical(batch.id()));
    }

    private static String encodeCursor(double score, String id) {
        String value = Double.toHexString(score) + "\n" + id;
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static Cursor decodeCursor(String encoded) {
        if (encoded == null) {
            return null;
        }
        try {
            String value = new String(
                    Base64.getUrlDecoder().decode(encoded),
                    StandardCharsets.UTF_8);
            int separator = value.indexOf('\n');
            if (separator < 1 || separator == value.length() - 1) {
                throw new IllegalArgumentException("invalid cursor");
            }
            return new Cursor(
                    Double.valueOf(value.substring(0, separator)),
                    value.substring(separator + 1));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("invalid lexical cursor", exception);
        }
    }

    private record Cursor(double score, String id) {
    }
}
