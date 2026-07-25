package com.orgmemory.graphrag.opensearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenSearchStoreSupportTests {

    @Test
    void rejectsNullAndBlankIdsBeforeCreatingTheImmutableCopy() {
        List<String> nullId = new ArrayList<>();
        nullId.add("asset-a");
        nullId.add(null);

        assertThrows(
                IllegalArgumentException.class,
                () -> OpenSearchStoreSupport.requireIds(nullId));
        assertThrows(
                IllegalArgumentException.class,
                () -> OpenSearchStoreSupport.requireIds(List.of("asset-a", " ")));
        assertEquals(
                List.of("asset-a", "asset-b"),
                OpenSearchStoreSupport.requireIds(List.of("asset-a", "asset-b")));
    }
}
