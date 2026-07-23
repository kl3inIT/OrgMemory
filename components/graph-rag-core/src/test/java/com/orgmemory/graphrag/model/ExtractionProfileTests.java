package com.orgmemory.graphrag.model;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class ExtractionProfileTests {

    @Test
    void fingerprintSeparatesAdjacentListFields() {
        ExtractionProfile guidanceEndingWithMarker = profile(
                List.of("SYSTEM", "examples"),
                List.of());
        ExtractionProfile exampleEqualToMarker = profile(
                List.of("SYSTEM"),
                List.of("examples"));

        assertNotEquals(
                guidanceEndingWithMarker.fingerprint(),
                exampleEqualToMarker.fingerprint());
    }

    private static ExtractionProfile profile(
            List<String> entityTypes,
            List<String> examples) {
        return new ExtractionProfile(
                "openai",
                "gpt-5.6-sol",
                "lightrag-v1.5.4",
                10,
                10,
                entityTypes,
                examples,
                1,
                24_000,
                256);
    }
}
