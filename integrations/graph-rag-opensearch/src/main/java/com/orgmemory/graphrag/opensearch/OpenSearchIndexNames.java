package com.orgmemory.graphrag.opensearch;

import java.util.Locale;
import java.util.UUID;

record OpenSearchIndexNames(String prefix) {

    String control() {
        return prefix + "-control-v1";
    }

    String content() {
        return prefix + "-content-v1";
    }

    String lexical(UUID batchId) {
        return prefix
                + "-lexical-"
                + batchId.toString().toLowerCase(Locale.ROOT)
                + "-v1";
    }

    String graphEntities(UUID batchId) {
        return prefix
                + "-graph-entities-"
                + batchId.toString().toLowerCase(Locale.ROOT)
                + "-v1";
    }

    String graphRelations(UUID batchId) {
        return prefix
                + "-graph-relations-"
                + batchId.toString().toLowerCase(Locale.ROOT)
                + "-v1";
    }

    String status() {
        return prefix + "-status-v1";
    }

    String vectors(UUID embeddingProfileId, int dimensions) {
        return prefix
                + "-vector-"
                + embeddingProfileId.toString().toLowerCase(Locale.ROOT)
                + "-"
                + dimensions
                + "-v1";
    }

    String vectorPattern() {
        return prefix + "-vector-*-v1";
    }
}
