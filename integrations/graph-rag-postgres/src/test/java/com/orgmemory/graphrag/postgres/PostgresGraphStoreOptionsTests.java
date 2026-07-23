package com.orgmemory.graphrag.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import org.junit.jupiter.api.Test;

class PostgresGraphStoreOptionsTests {

    @Test
    void defaultsMatchThePinnedOpenAiEmbeddingProfile() {
        PostgresGraphStoreOptions options = PostgresGraphStoreOptions.defaults();

        assertEquals(PostgresVectorIndexStrategy.HNSW, options.vectorIndexStrategy());
        assertEquals(Set.of(1536), options.indexedVectorDimensions());
        assertEquals(200, options.maxBatchRecords());
        assertEquals(4L * 1024 * 1024, options.maxBatchPayloadBytes());
    }

    @Test
    void approximateIndexRequiresAtLeastOneDimension() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PostgresGraphStoreOptions(
                        ApacheAgeMode.REQUIRED,
                        200,
                        1024,
                        PostgresVectorIndexStrategy.HNSW,
                        Set.of(),
                        16,
                        64,
                        100,
                        ""));
    }
}
