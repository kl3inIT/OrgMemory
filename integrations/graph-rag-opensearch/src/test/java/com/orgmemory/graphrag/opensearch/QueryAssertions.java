package com.orgmemory.graphrag.opensearch;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.opensearch.client.opensearch._types.query_dsl.Query;

final class QueryAssertions {

    private QueryAssertions() {
    }

    static void assertNoBatchDocuments(
            OpenSearchOperations operations,
            String index,
            UUID batchId) {
        Query query = Query.of(candidate -> candidate.bool(bool -> bool.filter(List.of(
                OpenSearchStagedIndex.term(
                        OpenSearchProjectionCodec.BATCH_ID,
                        batchId.toString())))));
        assertTrue(new OpenSearchScanner(operations)
                .scan(index, query, 1)
                .isEmpty());
    }
}
