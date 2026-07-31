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
    }

    @Test
    void approximateIndexRequiresAtLeastOneDimension() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PostgresGraphStoreOptions(
                        ApacheAgeMode.REQUIRED,
                        PostgresVectorIndexStrategy.HNSW,
                        Set.of(),
                        16,
                        64,
                        100,
                        ""));
    }
}
