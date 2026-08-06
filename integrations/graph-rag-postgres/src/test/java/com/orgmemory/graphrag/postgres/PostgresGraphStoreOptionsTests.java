package com.orgmemory.graphrag.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
                        PostgresVectorIndexStrategy.HNSW,
                        Set.of(),
                        16,
                        64,
                        100,
                        ""));
    }

    @Test
    void hotRelationReadsAreCandidateFirstAndPostgresBounded() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/orgmemory/graphrag/postgres/PostgresGraphStore.java"));

        assertHotRead(source, "loadRelationContributions", "loadIncidentRelations");
        assertHotRead(source, "loadVisibleRelationWeights", "discard");
    }

    private static void assertHotRead(String source, String method, String nextMethod) {
        int start = source.lastIndexOf("public ", source.indexOf(" " + method + "("));
        int nextName = source.indexOf(" " + nextMethod + "(", start);
        int end = source.lastIndexOf("public ", nextName);
        String body = source.substring(start, end);

        assertTrue(
                body.contains("support.read(GRAPH_QUERY_STATEMENT_TIMEOUT"),
                () -> method + " must use the transaction-local PostgreSQL budget");
        assertTrue(
                body.contains("WITH candidate_relations AS MATERIALIZED"),
                () -> method + " must constrain authorization work to requested relations first");
    }
}
