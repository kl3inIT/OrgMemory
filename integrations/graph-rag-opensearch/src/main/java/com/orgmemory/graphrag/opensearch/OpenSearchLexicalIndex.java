package com.orgmemory.graphrag.opensearch;

import com.orgmemory.graphrag.authorization.AuthorizedEvidenceScope;
import com.orgmemory.graphrag.storage.LexicalIndex;
import com.orgmemory.graphrag.storage.ProjectionBatch;
import com.orgmemory.graphrag.storage.ProjectionKind;
import com.orgmemory.graphrag.storage.ProjectionSnapshot;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
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
    private static final ConcurrentHashMap<String, ReentrantLock> COPY_LOCKS =
            new ConcurrentHashMap<>();

    private final OpenSearchOperations operations;
    private final OpenSearchProjectionPublicationStore publications;
    private final OpenSearchIndexNames indexes;
    private final OpenSearchScanner scanner;

    OpenSearchLexicalIndex(
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
        Query visibility = Query.of(query -> query.bool(bool -> bool
                .filter(OpenSearchStagedIndex.term(
                        OpenSearchProjectionCodec.ORGANIZATION_ID,
                        scope.organizationId().toString()))
                .filter(OpenSearchStagedIndex.term(
                        OpenSearchProjectionCodec.BATCH_ID,
                        snapshot.batchId().toString()))
                .filter(OpenSearchStagedIndex.term(
                        OpenSearchProjectionCodec.GENERATION,
                        snapshot.generation()))
                .filter(OpenSearchStoreSupport.anyTerms(
                        OpenSearchProjectionCodec.ASSET_ID,
                        scope.authorizedAssetIds().stream()
                                .map(UUID::toString)
                                .toList()))
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
    public void discard(ProjectionBatch batch) {
        Objects.requireNonNull(batch, "batch");
        operations.deleteIndex(indexes.lexical(batch.id()));
        operations.deleteIfExists(indexes.control(), copyMarkerId(batch));
    }

    private void ensureCopyForward(ProjectionBatch batch) {
        Objects.requireNonNull(batch, "batch");
        String target = indexes.lexical(batch.id());
        operations.ensureIndex(target, OpenSearchSchemas.lexical());
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
            copying.put("projection_kind", ProjectionKind.LEXICAL.name());
            copying.put("target_index", target);
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
            copyPrevious(batch, target);
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
        List<BulkOperation> copies = scanner.scan(source, query, Integer.MAX_VALUE).stream()
                .map(hit -> {
                    Map<String, Object> document = new LinkedHashMap<>(hit.source());
                    document.put(OpenSearchProjectionCodec.BATCH_ID, batch.id().toString());
                    document.put(OpenSearchProjectionCodec.GENERATION, batch.generation());
                    return BulkOperation.of(operation -> operation.index(write -> write
                            .index(target)
                            .id(hit.id())
                            .document(Map.copyOf(document))));
                })
                .toList();
        operations.bulk(copies);
    }

    private String copyMarkerId(ProjectionBatch batch) {
        return "copy:" + batch.id() + ":" + ProjectionKind.LEXICAL.name();
    }

    private static boolean ready(OpenSearchOperations.VersionedDocument marker) {
        return marker != null && "READY".equals(marker.source().get("copy_status"));
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
