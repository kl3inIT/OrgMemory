package com.orgmemory.graphrag.opensearch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.Time;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.DeletePitRequest;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.Pit;

final class OpenSearchScanner {

    private static final int PAGE_SIZE = 500;

    private final OpenSearchOperations operations;

    OpenSearchScanner(OpenSearchOperations operations) {
        this.operations = Objects.requireNonNull(operations, "operations");
    }

    List<StoredHit> scan(
            String index,
            Query query,
            int limit) {
        List<StoredHit> result = new ArrayList<>();
        scanPages(index, query, limit, PAGE_SIZE, result::addAll);
        return List.copyOf(result);
    }

    void scanPages(
            String index,
            Query query,
            int limit,
            int pageSize,
            Consumer<List<StoredHit>> pageConsumer) {
        Objects.requireNonNull(index, "index");
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(pageConsumer, "pageConsumer");
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be positive");
        }
        if (limit <= 0 || !operations.indexExists(index)) {
            return;
        }
        String pitId = null;
        try {
            pitId = operations.client()
                    .createPit(request -> request
                            .index(List.of(index))
                            .keepAlive(Time.of(time -> time.time("1m"))))
                    .pitId();
            String activePitId = pitId;
            List<FieldValue> searchAfter = List.of();
            int accepted = 0;
            while (accepted < limit) {
                int size = Math.min(pageSize, limit - accepted);
                var request = new org.opensearch.client.opensearch.core.SearchRequest.Builder()
                        .size(size)
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
                List<StoredHit> page = new ArrayList<>(hits.size());
                for (Hit<Map> hit : hits) {
                    if (hit.source() != null) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> source = (Map<String, Object>) hit.source();
                        page.add(new StoredHit(hit.index(), hit.id(), Map.copyOf(source)));
                    }
                }
                if (!page.isEmpty()) {
                    pageConsumer.accept(List.copyOf(page));
                    accepted += page.size();
                }
                searchAfter = hits.getLast().sort();
                if (hits.size() < size) {
                    break;
                }
            }
        } catch (IOException | OpenSearchException exception) {
            throw new OpenSearchProjectionException(
                    "OpenSearch failed to scan index " + index,
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

    record StoredHit(String index, String id, Map<String, Object> source) {

        StoredHit {
            index = Objects.requireNonNull(index, "index");
            id = Objects.requireNonNull(id, "id");
            source = Map.copyOf(Objects.requireNonNull(source, "source"));
        }
    }
}
