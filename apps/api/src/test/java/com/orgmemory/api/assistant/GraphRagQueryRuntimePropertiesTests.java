package com.orgmemory.api.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class GraphRagQueryRuntimePropertiesTests {

    @Test
    void defaultsKeepRerankingDisabledAndBoundTheEvidenceClosure() {
        GraphRagQueryRuntimeProperties properties = properties(
                null,
                null,
                null);

        var policy = properties.toPolicy();

        assertFalse(policy.rerank().enabled());
        assertEquals("none", policy.rerank().provider());
        assertEquals(2_000, policy.maximumEvidenceClosure());
        assertEquals(4, policy.maximumConcurrentSpaces());
        assertEquals(4, policy.retrievalAdmissionPermits());
        assertEquals(40, policy.topK());
        assertEquals(Duration.ofHours(24), properties.keywordCacheTtl());
    }

    @Test
    void supersededMaximumConcurrentSpacesRemainsValidated() {
        assertThrows(
                IllegalArgumentException.class,
                () -> propertiesWithConcurrency(0));
        assertThrows(
                IllegalArgumentException.class,
                () -> propertiesWithConcurrency(21));
    }

    @Test
    void enabledRerankingRequiresAnExplicitProvider() {
        assertThrows(
                IllegalArgumentException.class,
                () -> properties(true, null, 0.2));
    }

    @Test
    void configuredRerankingBecomesTypedRuntimePolicy() {
        var policy = properties(
                        true,
                        "cohere-rerank-v3.5",
                        0.35)
                .toPolicy();

        assertTrue(policy.rerank().enabled());
        assertEquals(
                "cohere-rerank-v3.5",
                policy.rerank().provider());
        assertEquals(0.35, policy.rerank().minimumScore());
    }

    private static GraphRagQueryRuntimeProperties properties(
            Boolean rerankEnabled,
            String rerankProvider,
            Double minimumRerankScore) {
        return new GraphRagQueryRuntimeProperties(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                rerankEnabled,
                rerankProvider,
                minimumRerankScore,
                null);
    }

    private static GraphRagQueryRuntimeProperties propertiesWithConcurrency(
            Integer maximumConcurrentSpaces) {
        return new GraphRagQueryRuntimeProperties(
                null,
                null,
                null,
                20,
                maximumConcurrentSpaces,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }
}
