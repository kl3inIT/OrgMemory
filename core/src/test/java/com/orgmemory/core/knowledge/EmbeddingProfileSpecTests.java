package com.orgmemory.core.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class EmbeddingProfileSpecTests {

    @Test
    void encodesDelimiterSensitiveProfileKeyComponents() {
        var first = new EmbeddingProfileSpec(
                "vendor/cloud",
                "model/v1",
                1536,
                EmbeddingDistanceMetric.COSINE);
        var second = new EmbeddingProfileSpec(
                "vendor",
                "cloud/model/v1",
                1536,
                EmbeddingDistanceMetric.COSINE);

        assertEquals("vendor%2Fcloud/model%2Fv1/1536/cosine", first.profileKey());
        assertNotEquals(first.profileKey(), second.profileKey());
    }
}
