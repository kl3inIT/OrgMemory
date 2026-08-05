package com.orgmemory.api.evaluation;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("orgmemory.retrieval-observation")
public record RetrievalObservationProperties(
        boolean enabled,
        Path officialCases,
        Path documentManifest,
        Path output,
        String expectedDatabase) {

    public RetrievalObservationProperties {
        officialCases = officialCases == null
                ? Path.of("demo/fixtures/public-evaluation.json")
                : officialCases;
        documentManifest = documentManifest == null
                ? Path.of("demo/fixtures/documents/manifest.json")
                : documentManifest;
        output = output == null
                ? Path.of("evaluation/output/retrieval-observations-v2.json")
                : output;
        expectedDatabase = expectedDatabase == null
                ? ""
                : expectedDatabase.strip();
        if (enabled && expectedDatabase.isEmpty()) {
            throw new IllegalArgumentException(
                    "orgmemory.retrieval-observation.expected-database is required");
        }
    }
}
